package com.giftedlabs.echoinhealthbackend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "organizations", indexes = {
        @Index(name = "idx_organizations_name", columnList = "name")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_tier", nullable = false, length = 20)
    @Builder.Default
    private SubscriptionTier subscriptionTier = SubscriptionTier.BASIC;

    @Column(name = "addon_storage_mb", nullable = false)
    @Builder.Default
    private Integer addonStorageMb = 0;

    @Column(name = "addon_ai_credits", nullable = false)
    @Builder.Default
    private Integer addonAiCredits = 0;

    @Column(name = "lite_emr_integration_enabled", nullable = false)
    @Builder.Default
    private Boolean liteEmrIntegrationEnabled = false;

    @Column(name = "ai_credits_used_this_month", nullable = false)
    @Builder.Default
    private Integer aiCreditsUsedThisMonth = 0;

    @Column(name = "ai_credits_last_reset_at")
    private LocalDateTime aiCreditsLastResetAt;

    /**
     * Which usage thresholds admins have already been alerted about, so notifications fire on
     * threshold crossing rather than on every usage read.
     */
    @Column(name = "last_usage_alert_signature", length = 120)
    private String lastUsageAlertSignature;

    @Column(name = "last_usage_alert_at")
    private LocalDateTime lastUsageAlertAt;

    @Column(name = "hospital_name", nullable = false, length = 255)
    private String hospitalName;

    @Column(name = "letterhead_url", length = 1000)
    private String letterheadUrl;

    @Column(length = 500)
    private String address;

    @Column(length = 30)
    private String phone;

    @Column(length = 255)
    private String email;

    @Column(length = 255)
    private String website;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public int getEffectiveStorageLimitMb() {
        int base = subscriptionTier != null ? subscriptionTier.getStorageLimitMb() : 0;
        return base + (addonStorageMb != null ? addonStorageMb : 0);
    }

    public int getEffectiveAiCreditsPerMonth() {
        int base = subscriptionTier != null ? subscriptionTier.getAiCreditsPerMonth() : 0;
        return base + (addonAiCredits != null ? addonAiCredits : 0);
    }

    public int getEffectiveUserLimit() {
        return subscriptionTier != null ? subscriptionTier.getMaxUsers() : 0;
    }

    public boolean isAutoGrammarCheckEnabled() {
        return subscriptionTier != null && subscriptionTier.isAutoGrammarCheckEnabled();
    }
}
