package com.giftedlabs.echoinhealthbackend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for sending emails via Resend API
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

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
     * Send email asynchronously
     * 
     * @param to          Recipient email address
     * @param subject     Email subject
     * @param htmlContent HTML email content
     */
    @Async
    public void sendEmail(String to, String subject, String htmlContent) {
        try {
            Map<String, Object> emailData = new HashMap<>();
            emailData.put("from", fromEmail);
            emailData.put("to", List.of(to));
            emailData.put("subject", subject);
            emailData.put("html", htmlContent);

            String requestBody = objectMapper.writeValueAsString(emailData);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(resendApiUrl))
                    .header("Authorization", "Bearer " + resendApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Email sent successfully to: {}", to);
            } else {
                log.error("Failed to send email. Status: {}, Response: {}",
                        response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("Error sending email to: {}", to, e);
        }
    }

    /**
     * Send verification email
     */
    public void sendVerificationEmail(String to, String firstName, String verificationLink) {
        String subject = "Verify Your Email - Echoin Health";
        String htmlContent = com.giftedlabs.echoinhealthbackend.util.EmailTemplate
                .getVerificationEmail(firstName, verificationLink);
        sendEmail(to, subject, htmlContent);
    }

    /**
     * Send welcome email after verification
     */
    public void sendWelcomeEmail(String to, String firstName) {
        String subject = "Welcome to Echoin Health!";
        String htmlContent = com.giftedlabs.echoinhealthbackend.util.EmailTemplate
                .getWelcomeEmail(firstName);
        sendEmail(to, subject, htmlContent);
    }
}
