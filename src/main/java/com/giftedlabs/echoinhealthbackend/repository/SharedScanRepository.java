package com.giftedlabs.echoinhealthbackend.repository;

import com.giftedlabs.echoinhealthbackend.entity.SharedScan;
import com.giftedlabs.echoinhealthbackend.entity.SharedScanStatus;
import com.giftedlabs.echoinhealthbackend.entity.SharingLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Collection;

@Repository
public interface SharedScanRepository extends JpaRepository<SharedScan, String> {

        /**
         * Find shared scans owned by a user
         */
        Page<SharedScan> findByOwnerIdAndOrganizationId(String ownerId, String organizationId, Pageable pageable);

        /**
         * Find shared scans by status for an owner
         */
        Page<SharedScan> findByOwnerIdAndOrganizationIdAndStatus(
                        String ownerId,
                        String organizationId,
                        SharedScanStatus status,
                        Pageable pageable);

        /**
         * Find shared scan with full details including report (handles nullable report)
         */
        @Query("SELECT ss FROM SharedScan ss LEFT JOIN FETCH ss.report JOIN FETCH ss.owner WHERE ss.id = :id AND ss.organization.id = :organizationId")
        Optional<SharedScan> findByIdWithDetails(@Param("id") String id, @Param("organizationId") String organizationId);

        /**
         * Every scan visible to a user through any sharing route, in one pageable query.
         *
         * <p>Replaces a merge of three separately-paginated queries whose results were concatenated
         * and wrapped in a {@code PageImpl} carrying the merged slice's size as the total. That
         * reported the wrong page count, and because each sub-query applied the same offset
         * independently, items were duplicated across pages or skipped entirely. Letting the
         * database evaluate the union means {@code Pageable} and the total count are computed once
         * and agree with each other.
         *
         * <p>The three routes are: an explicit access grant (SPECIFIC_COLLEAGUES), an org-wide
         * share, and a department share matching the user's own department. Scans the user owns are
         * excluded — those belong in "my shares".
         */
        @Query(value = """
                        SELECT ss FROM SharedScan ss
                        WHERE ss.organization.id = :organizationId
                          AND ss.owner.id <> :userId
                          AND (
                                EXISTS (SELECT 1 FROM SharedScanAccess ssa
                                         WHERE ssa.sharedScan = ss AND ssa.user.id = :userId)
                             OR ss.sharingLevel IN :organizationWideLevels
                             OR (ss.sharingLevel = :departmentLevel
                                 AND :department IS NOT NULL
                                 AND ss.targetDepartment = :department)
                          )
                        ORDER BY ss.createdAt DESC, ss.id DESC
                        """,
                        countQuery = """
                        SELECT COUNT(ss) FROM SharedScan ss
                        WHERE ss.organization.id = :organizationId
                          AND ss.owner.id <> :userId
                          AND (
                                EXISTS (SELECT 1 FROM SharedScanAccess ssa
                                         WHERE ssa.sharedScan = ss AND ssa.user.id = :userId)
                             OR ss.sharingLevel IN :organizationWideLevels
                             OR (ss.sharingLevel = :departmentLevel
                                 AND :department IS NOT NULL
                                 AND ss.targetDepartment = :department)
                          )
                        """)
        Page<SharedScan> findVisibleToUser(
                        @Param("userId") String userId,
                        @Param("organizationId") String organizationId,
                        @Param("department") String department,
                        @Param("organizationWideLevels") Collection<SharingLevel> organizationWideLevels,
                        @Param("departmentLevel") SharingLevel departmentLevel,
                        Pageable pageable);

        /**
         * Count pending scans shared with user
         */
        @Query("""
                        SELECT COUNT(ss) FROM SharedScan ss
                        JOIN SharedScanAccess ssa ON ssa.sharedScan = ss
                        WHERE ssa.user.id = :userId AND ss.organization.id = :organizationId AND ss.status = :status
                        """)
        long countSharedWithUserByStatus(@Param("userId") String userId, @Param("organizationId") String organizationId,
                        @Param("status") SharedScanStatus status);

        @Query("SELECT COALESCE(SUM(ss.imageSize), 0) FROM SharedScan ss WHERE ss.organization.id = :organizationId")
        long sumImageSizeByOrganizationId(@Param("organizationId") String organizationId);

        /** Shared scans belonging to one tenant. Platform console tenant drill-down. */
        @Query("SELECT COUNT(ss) FROM SharedScan ss WHERE ss.organization.id = :organizationId")
        long countByOrganizationId(@Param("organizationId") String organizationId);
}
