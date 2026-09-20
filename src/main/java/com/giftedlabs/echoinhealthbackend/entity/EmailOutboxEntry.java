package com.giftedlabs.echoinhealthbackend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Durable record of every outbound email and what the provider said about it.
 *
 * <p>Sending used to be fire-and-forget: a rejected message wrote one line to a log and the
 * application carried on as though it had been delivered. Nothing in the product could answer
 * "did the new hospital ever receive its welcome email?". Each attempt now leaves a row here, so
 * the question is answerable from the super-admin console and a failure is an operational fact
 * rather than an absence.
 */
@Entity
@Table(name = "email_outbox", indexes = {
        @Index(name = "idx_email_outbox_status", columnList = "status"),
        @Index(name = "idx_email_outbox_created_at", columnList = "created_at"),
        @Index(name = "idx_email_outbox_recipient", columnList = "recipient")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailOutboxEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, length = 255)
    private String recipient;

    @Column(nullable = false, length = 500)
    private String subject;

    /**
     * Which message this was — {@code verification}, {@code welcome},
     * {@code organization_onboarded}, {@code delivery_test}. Lets an operator filter the outbox by
     * purpose without parsing subjects.
     */
    @Column(name = "template_name", nullable = false, length = 100)
    private String templateName;

    /** Tenant the message relates to, when it has one. Platform mail leaves this null. */
    @Column(name = "organization_id", length = 36)
    private String organizationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EmailDeliveryStatus status = EmailDeliveryStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private Integer attemptCount = 0;

    /** HTTP status returned by the provider on the most recent attempt. */
    @Column(name = "provider_status_code")
    private Integer providerStatusCode;

    /** Provider-assigned message id, present only on success. Use it to trace a delivery. */
    @Column(name = "provider_message_id", length = 255)
    private String providerMessageId;

    /** Provider error body or exception message from the most recent failed attempt. */
    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
