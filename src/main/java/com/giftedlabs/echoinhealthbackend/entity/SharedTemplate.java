package com.giftedlabs.echoinhealthbackend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Entity for tracking template sharing between users within an organization (UR-058).
 * Owner retains ownership; recipient gets read access.
 */
@Entity
@Table(name = "shared_templates", indexes = {
        @Index(name = "idx_shared_templates_org_template", columnList = "organization_id,template_id"),
        @Index(name = "idx_shared_templates_recipient", columnList = "recipient_id"),
        @Index(name = "idx_shared_templates_owner", columnList = "owner_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_shared_template_recipient", columnNames = {"template_id", "recipient_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SharedTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private ReportTemplate template;

    /**
     * User who owns and shared the template
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    /**
     * User who received access to the template
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @CreationTimestamp
    @Column(name = "shared_at", nullable = false, updatable = false)
    private LocalDateTime sharedAt;
}
