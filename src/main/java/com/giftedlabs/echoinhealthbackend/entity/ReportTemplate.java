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
 * Report template entity for customizable report templates
 */
@Entity
@Table(name = "report_templates", indexes = {
        @Index(name = "idx_templates_user", columnList = "user_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user; // null for system templates

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // Template Content
    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 10)
    private Gender gender; // MALE, FEMALE, or null for both

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", length = 100)
    private ReportType reportType;

    @Column(name = "default_findings", columnDefinition = "TEXT")
    private String defaultFindings;

    @Column(name = "default_impression", columnDefinition = "TEXT")
    private String defaultImpression;

    @Column(name = "is_default")
    @Builder.Default
    private Boolean isDefault = false;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
