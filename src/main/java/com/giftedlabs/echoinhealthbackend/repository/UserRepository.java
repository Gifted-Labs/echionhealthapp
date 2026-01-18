package com.giftedlabs.echoinhealthbackend.repository;

import com.giftedlabs.echoinhealthbackend.entity.Role;
import com.giftedlabs.echoinhealthbackend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for User entity operations
 */
@Repository
public interface UserRepository extends JpaRepository<User, String> {

    /**
     * Find a user by email address
     */
    Optional<User> findByEmail(String email);

    /**
     * Check if a user exists with the given email
     */
    boolean existsByEmail(String email);

    /**
     * Find a user by email and check if email is verified
     */
    Optional<User> findByEmailAndEmailVerified(String email, Boolean emailVerified);

    // ========== Admin Methods ==========

    /**
     * Find all users by role
     */
    List<User> findByRole(Role role);

    /**
     * Count users by role
     */
    long countByRole(Role role);

    /**
     * Count locked accounts
     */
    long countByAccountLockedTrue();

    /**
     * Count verified users
     */
    long countByEmailVerifiedTrue();

    /**
     * Search users by name or email with optional filters
     */
    @Query("""
            SELECT u FROM User u
            WHERE (:search IS NULL OR :search = '' OR
                   LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR
                   LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR
                   LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))
            AND (:role IS NULL OR u.role = :role)
            AND (:locked IS NULL OR u.accountLocked = :locked)
            AND (:verified IS NULL OR u.emailVerified = :verified)
            ORDER BY u.createdAt DESC
            """)
    Page<User> searchUsers(
            @Param("search") String search,
            @Param("role") Role role,
            @Param("locked") Boolean locked,
            @Param("verified") Boolean verified,
            Pageable pageable);

    /**
     * Find users created in the last N days
     */
    @Query("SELECT u FROM User u WHERE u.createdAt >= :since ORDER BY u.createdAt DESC")
    List<User> findRecentUsers(@Param("since") java.time.LocalDateTime since);
}
