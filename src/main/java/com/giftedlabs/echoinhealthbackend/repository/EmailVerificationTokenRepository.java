package com.giftedlabs.echoinhealthbackend.repository;

import com.giftedlabs.echoinhealthbackend.entity.EmailVerificationToken;
import com.giftedlabs.echoinhealthbackend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Repository for EmailVerificationToken entity operations
 */
@Repository
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, String> {

    /**
     * Find a token by its value
     */
    Optional<EmailVerificationToken> findByToken(String token);

    /**
     * Find the most recent unverified token for a user
     */
    Optional<EmailVerificationToken> findFirstByUserAndVerifiedAtIsNullOrderByCreatedAtDesc(User user);

    /**
     * Delete all expired tokens (cleanup job)
     */
    @Modifying
    @Query("DELETE FROM EmailVerificationToken t WHERE t.expiresAt < :now AND t.verifiedAt IS NULL")
    void deleteExpiredTokens(LocalDateTime now);

    /**
     * Delete all tokens for a specific user
     */
    void deleteByUser(User user);
}
