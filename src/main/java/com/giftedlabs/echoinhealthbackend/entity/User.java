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
 * User entity representing healthcare professionals in the Echoin Health
 * system.
 * Supports staged registration: basic info first, professional details later.
 */
@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    // Basic Authentication Fields (Required at Registration)
    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String firstName;

    @Column(nullable = false, length = 100)
    private String lastName;

    // Professional Details (Optional - can be added later)
    @Column(length = 20)
    private String phone;

    @Column(length = 255)
    private String hospitalName;

    @Column(length = 100)
    private String department;

    @Column(length = 100)
    private String serviceNumber;

    // Security & Role Management
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Role role = Role.SONOGRAPHER;

    @Column(nullable = false)
    @Builder.Default
    private Boolean emailVerified = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean accountLocked = false;

    // Timestamps
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column
    private LocalDateTime profileUpdatedAt;

    @Column
    private LocalDateTime lastLoginAt;

    /**
     * Get the user's full name
     */
    public String getFullName() {
        return firstName + " " + lastName;
    }

    /**
     * Check if the user has completed their professional profile
     */
    public boolean hasCompletedProfile() {
        return phone != null && hospitalName != null &&
                department != null && serviceNumber != null;
    }
}
