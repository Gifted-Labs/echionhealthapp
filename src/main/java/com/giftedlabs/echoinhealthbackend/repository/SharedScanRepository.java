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

@Repository
public interface SharedScanRepository extends JpaRepository<SharedScan, String> {

        /**
         * Find shared scans owned by a user
         */
        Page<SharedScan> findByOwnerId(String ownerId, Pageable pageable);

        /**
         * Find shared scans by status for an owner
         */
        Page<SharedScan> findByOwnerIdAndStatus(String ownerId, SharedScanStatus status, Pageable pageable);

        /**
         * Find shared scan with full details including report (handles nullable report)
         */
        @Query("SELECT ss FROM SharedScan ss LEFT JOIN FETCH ss.report JOIN FETCH ss.owner WHERE ss.id = :id")
        Optional<SharedScan> findByIdWithDetails(@Param("id") String id);

        /**
         * Find scans shared with specific user (via access list)
         */
        @Query("""
                        SELECT DISTINCT ss FROM SharedScan ss
                        JOIN SharedScanAccess ssa ON ssa.sharedScan = ss
                        WHERE ssa.user.id = :userId
                        ORDER BY ss.createdAt DESC
                        """)
        Page<SharedScan> findSharedWithUser(@Param("userId") String userId, Pageable pageable);

        /**
         * Find scans shared with EVERYONE (excluding owner's own shares)
         */
        @Query("""
                        SELECT ss FROM SharedScan ss
                        WHERE ss.sharingLevel = :sharingLevel
                        AND ss.owner.id != :userId
                        ORDER BY ss.createdAt DESC
                        """)
        Page<SharedScan> findByEveryoneSharing(
                        @Param("sharingLevel") SharingLevel sharingLevel,
                        @Param("userId") String userId,
                        Pageable pageable);

        /**
         * Count pending scans shared with user
         */
        @Query("""
                        SELECT COUNT(ss) FROM SharedScan ss
                        JOIN SharedScanAccess ssa ON ssa.sharedScan = ss
                        WHERE ssa.user.id = :userId AND ss.status = :status
                        """)
        long countSharedWithUserByStatus(@Param("userId") String userId, @Param("status") SharedScanStatus status);
}
