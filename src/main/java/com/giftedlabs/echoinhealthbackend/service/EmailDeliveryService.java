package com.giftedlabs.echoinhealthbackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.giftedlabs.echoinhealthbackend.entity.EmailDeliveryStatus;
import com.giftedlabs.echoinhealthbackend.entity.EmailOutboxEntry;
import com.giftedlabs.echoinhealthbackend.repository.EmailOutboxRepository;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Performs outbound email delivery and records every attempt in the outbox.
 *
 * <p>Deliberately a separate bean from {@link EmailService}, which composes the messages. Spring's
 * {@code @Async} and {@code @Transactional} are proxy-based, so a call from one method of a bean to
 * another method of the same bean bypasses them entirely. Keeping composition and delivery in
 * different beans is what makes the "record the attempt in its own transaction, then send off the
 * request thread" guarantee actually hold.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailDeliveryService {

    /** Total attempts per message, including the first. */
    private static final int MAX_ATTEMPTS = 4;

    /** A message is eligible for another attempt once this long has passed since the last one. */
    private static final Duration RETRY_BACKOFF = Duration.ofMinutes(2);

    /** Ceiling on how many messages one retry sweep drains, so a backlog cannot stall the app. */
    private static final int RETRY_BATCH_SIZE = 25;

    private final EmailOutboxRepository outboxRepository;

    /**
     * Rendered message bodies for in-flight deliveries, held in memory rather than in the outbox
     * table on purpose: they carry single-use verification links and hospital-identifying content
     * that has no business sitting in a queryable table indefinitely. The trade is that a restart
     * loses in-flight bodies, which the retry sweep reports as an abandoned entry rather than
     * pretending success.
     */
    private final Map<String, String> pendingBodies = new ConcurrentHashMap<>();

    @Value("${email.resend.api-key}")
    private String resendApiKey;

    @Value("${email.resend.api-url}")
    private String resendApiUrl;

    @Value("${email.from}")
    private String fromEmail;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Outcome of a single delivery attempt. Returned rather than swallowed so callers that care —
     * the platform console's delivery test, for one — can report what the provider actually said.
     */
    @Getter
    @Builder
    public static class DeliveryResult {
        private final boolean delivered;
        private final Integer providerStatusCode;
        private final String providerMessageId;
        private final String error;
        private final String outboxEntryId;
    }

    /**
     * Records the intent to send, in its own transaction, before any network call happens. A
     * message is therefore never lost to a crash between "we decided to send this" and "we tried".
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EmailOutboxEntry queue(String to, String subject, String html, String templateName,
            String organizationId) {
        EmailOutboxEntry entry = outboxRepository.save(EmailOutboxEntry.builder()
                .recipient(to)
                .subject(subject)
                .templateName(templateName)
                .organizationId(organizationId)
                .status(EmailDeliveryStatus.PENDING)
                .attemptCount(0)
                .build());
        pendingBodies.put(entry.getId(), html);
        return entry;
    }

    /** Attempts delivery off the calling thread, so registration does not wait on the provider. */
    @Async
    public void deliverAsync(String outboxEntryId) {
        deliver(outboxEntryId);
    }

    /**
     * Performs one delivery attempt and records its outcome.
     *
     * <p>Runs in its own transaction: the attempt must be recorded whether or not the caller's
     * transaction — a user registration, say — goes on to commit or roll back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DeliveryResult deliver(String outboxEntryId) {
        EmailOutboxEntry entry = outboxRepository.findById(outboxEntryId).orElse(null);
        if (entry == null) {
            log.error("Email outbox entry {} disappeared before delivery", outboxEntryId);
            return DeliveryResult.builder()
                    .delivered(false)
                    .error("Outbox entry not found")
                    .outboxEntryId(outboxEntryId)
                    .build();
        }

        String htmlContent = pendingBodies.get(outboxEntryId);
        if (htmlContent == null) {
            return abandon(entry, "Message body no longer available after restart; resend manually");
        }

        entry.setAttemptCount(entry.getAttemptCount() + 1);
        entry.setLastAttemptAt(LocalDateTime.now());

        try {
            Map<String, Object> emailData = new HashMap<>();
            emailData.put("from", fromEmail);
            emailData.put("to", List.of(entry.getRecipient()));
            emailData.put("subject", entry.getSubject());
            emailData.put("html", htmlContent);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(resendApiUrl))
                    .header("Authorization", "Bearer " + resendApiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(emailData)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            entry.setProviderStatusCode(response.statusCode());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                entry.setStatus(EmailDeliveryStatus.SENT);
                entry.setSentAt(LocalDateTime.now());
                entry.setProviderMessageId(extractMessageId(response.body()));
                entry.setLastError(null);
                outboxRepository.save(entry);
                pendingBodies.remove(outboxEntryId);

                log.info("Email '{}' delivered to {} (provider id {})",
                        entry.getTemplateName(), entry.getRecipient(), entry.getProviderMessageId());
                return DeliveryResult.builder()
                        .delivered(true)
                        .providerStatusCode(response.statusCode())
                        .providerMessageId(entry.getProviderMessageId())
                        .outboxEntryId(entry.getId())
                        .build();
            }

            return recordFailure(entry, response.statusCode(), truncate(response.body()));
        } catch (Exception e) {
            return recordFailure(entry, entry.getProviderStatusCode(), truncate(String.valueOf(e.getMessage())));
        }
    }

    private DeliveryResult recordFailure(EmailOutboxEntry entry, Integer statusCode, String error) {
        boolean exhausted = entry.getAttemptCount() >= MAX_ATTEMPTS;
        entry.setStatus(exhausted ? EmailDeliveryStatus.FAILED : EmailDeliveryStatus.RETRYING);
        entry.setLastError(error);
        outboxRepository.save(entry);

        if (exhausted) {
            pendingBodies.remove(entry.getId());
            log.error("Email '{}' to {} permanently failed after {} attempts (status {}): {}",
                    entry.getTemplateName(), entry.getRecipient(), entry.getAttemptCount(), statusCode, error);
        } else {
            log.warn("Email '{}' to {} failed on attempt {} (status {}): {} - will retry",
                    entry.getTemplateName(), entry.getRecipient(), entry.getAttemptCount(), statusCode, error);
        }

        return DeliveryResult.builder()
                .delivered(false)
                .providerStatusCode(statusCode)
                .error(error)
                .outboxEntryId(entry.getId())
                .build();
    }

    private DeliveryResult abandon(EmailOutboxEntry entry, String reason) {
        entry.setStatus(EmailDeliveryStatus.FAILED);
        entry.setLastError(reason);
        outboxRepository.save(entry);
        log.error("Email '{}' to {} abandoned: {}", entry.getTemplateName(), entry.getRecipient(), reason);

        return DeliveryResult.builder()
                .delivered(false)
                .error(reason)
                .outboxEntryId(entry.getId())
                .build();
    }

    /**
     * Re-attempts unfinished messages. A transient provider outage that would previously have lost
     * the message permanently now costs it only a delay.
     */
    @Scheduled(
            fixedDelayString = "${email.retry.interval-ms:120000}",
            initialDelayString = "${email.retry.initial-delay-ms:60000}")
    public void retryPendingDeliveries() {
        LocalDateTime retryBefore = LocalDateTime.now().minus(RETRY_BACKOFF);
        List<EmailOutboxEntry> retryable =
                outboxRepository.findRetryable(retryBefore, PageRequest.of(0, RETRY_BATCH_SIZE));

        if (retryable.isEmpty()) {
            return;
        }

        log.info("Retrying {} unfinished email deliveries", retryable.size());
        retryable.forEach(entry -> deliver(entry.getId()));
    }

    private String extractMessageId(String body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            JsonNode id = node.get("id");
            return id != null ? id.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }
}
