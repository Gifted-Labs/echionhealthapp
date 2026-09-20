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

        @Query("SELECT u FROM User u WHERE u.email = :email AND u.organization.id = :organizationId")
        Optional<User> findByEmailAndOrganizationId(@Param("email") String email, @Param("organizationId") String organizationId);

        @Query("SELECT u FROM User u WHERE u.username = :username AND u.organization.id = :organizationId")
        Optional<User> findByUsernameAndOrganizationId(@Param("username") String username, @Param("organizationId") String organizationId);

        /**
         * Check if a user exists with the given email
         */
        boolean existsByEmail(String email);

        @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.email = :email AND u.organization.id = :organizationId")
        boolean existsByEmailAndOrganizationId(@Param("email") String email, @Param("organizationId") String organizationId);

        @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.username = :username AND u.organization.id = :organizationId")
        boolean existsByUsernameAndOrganizationId(@Param("username") String username, @Param("organizationId") String organizationId);

        @Query("SELECT u FROM User u WHERE u.id = :id AND u.organization.id = :organizationId")
        Optional<User> findByIdAndOrganizationId(@Param("id") String id, @Param("organizationId") String organizationId);

        @Query("SELECT COUNT(u) FROM User u WHERE u.organization.id = :organizationId")
        long countByOrganizationId(@Param("organizationId") String organizationId);

        @Query("SELECT COUNT(u) FROM User u WHERE u.organization.id = :organizationId AND u.active = true")
        long countByOrganizationIdAndActiveTrue(@Param("organizationId") String organizationId);

        /**
         * Find a user by email and check if email is verified
         */
        Optional<User> findByEmailAndEmailVerified(String email, Boolean emailVerified);

        // ========== Admin Methods ==========

        /**
         * Find all users by role
         */
        List<User> findByRole(Role role);

        @Query("SELECT u FROM User u WHERE u.organization.id = :organizationId AND u.role = :role")
        List<User> findByOrganizationIdAndRole(@Param("organizationId") String organizationId, @Param("role") Role role);

        /**
         * Count users by role
         */
        long countByRole(Role role);

        @Query("SELECT COUNT(u) FROM User u WHERE u.organization.id = :organizationId AND u.role = :role")
        long countByOrganizationIdAndRole(@Param("organizationId") String organizationId, @Param("role") Role role);

        /**
         * Count locked accounts
         */
        long countByAccountLockedTrue();

        @Query("SELECT COUNT(u) FROM User u WHERE u.organization.id = :organizationId AND u.accountLocked = true")
        long countByOrganizationIdAndAccountLockedTrue(@Param("organizationId") String organizationId);

        /**
         * Count verified users
         */
        long countByEmailVerifiedTrue();

        /** Active users across every tenant, for the platform overview. */
        long countByActiveTrue();

        @Query("SELECT COUNT(u) FROM User u WHERE u.organization.id = :organizationId AND u.emailVerified = true")
        long countByOrganizationIdAndEmailVerifiedTrue(@Param("organizationId") String organizationId);

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

        @Query("""
                        SELECT u FROM User u
                        WHERE u.organization.id = :organizationId
                        AND (:search IS NULL OR :search = '' OR
                               LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR
                               LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR
                               LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))
                        AND (:role IS NULL OR u.role = :role)
                        AND (:locked IS NULL OR u.accountLocked = :locked)
                        AND (:verified IS NULL OR u.emailVerified = :verified)
                        ORDER BY u.createdAt DESC
                        """)
        Page<User> searchUsersByOrganization(
                        @Param("organizationId") String organizationId,
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

        @Query("SELECT u FROM User u WHERE u.organization.id = :organizationId AND u.createdAt >= :since ORDER BY u.createdAt DESC")
        List<User> findRecentUsersByOrganization(@Param("organizationId") String organizationId,
                        @Param("since") java.time.LocalDateTime since);

        /**
         * Find users in the same department (excluding a specific user)
         * Used for department-wide scan sharing
         */
        @Query("SELECT u FROM User u WHERE u.department = :department AND u.organization.id = :organizationId AND u.id != :id")
        List<User> findByDepartmentAndOrganizationIdAndIdNot(@Param("department") String department, @Param("organizationId") String organizationId, @Param("id") String id);

        /**
         * Active members of an organization other than the given user.
         *
         * <p>Recipient list for an organization-wide SonoShare. Ordered and limited by the caller:
         * an org-wide share on a large tenant must not fan out unbounded notification writes inside
         * the sharing request.
         */
        @Query("""
                        SELECT u FROM User u
                         WHERE u.organization.id = :organizationId
                           AND u.id <> :excludeUserId
                           AND u.active = TRUE
                         ORDER BY u.createdAt ASC
                        """)
        List<User> findActiveOrganizationMembers(
                        @Param("organizationId") String organizationId,
                        @Param("excludeUserId") String excludeUserId,
                        org.springframework.data.domain.Pageable pageable);

        /**
         * Active members of one department, other than the given user. Recipient list for a
         * department-scoped SonoShare.
         */
        @Query("""
                        SELECT u FROM User u
                         WHERE u.organization.id = :organizationId
                           AND u.department = :department
                           AND u.id <> :excludeUserId
                           AND u.active = TRUE
                         ORDER BY u.createdAt ASC
                        """)
        List<User> findActiveDepartmentMembers(
                        @Param("organizationId") String organizationId,
                        @Param("department") String department,
                        @Param("excludeUserId") String excludeUserId,
                        org.springframework.data.domain.Pageable pageable);

        /**
         * Colleagues a user can share with: active members of their own organization, excluding
         * themselves. Backs the share dialog's recipient picker, which previously had no endpoint
         * to populate from.
         */
        @Query("""
                        SELECT u FROM User u
                         WHERE u.organization.id = :organizationId
                           AND u.id <> :excludeUserId
                           AND u.active = TRUE
                           AND (:search IS NULL OR :search = ''
                                OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
                                OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%'))
                                OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))
                                OR LOWER(u.department) LIKE LOWER(CONCAT('%', :search, '%')))
                         ORDER BY u.firstName ASC, u.lastName ASC, u.id ASC
                        """)
        Page<User> findShareableColleagues(
                        @Param("organizationId") String organizationId,
                        @Param("excludeUserId") String excludeUserId,
                        @Param("search") String search,
                        org.springframework.data.domain.Pageable pageable);

        /**
         * Find users in the same hospital (excluding a specific user)
         * Used for facility-wide scan sharing
         */
        @Query("SELECT u FROM User u WHERE u.hospitalName = :hospitalName AND u.organization.id = :organizationId AND u.id != :id")
        List<User> findByHospitalNameAndOrganizationIdAndIdNot(@Param("hospitalName") String hospitalName, @Param("organizationId") String organizationId, @Param("id") String id);
}
