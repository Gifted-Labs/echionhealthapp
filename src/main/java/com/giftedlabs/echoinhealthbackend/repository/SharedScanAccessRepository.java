package com.giftedlabs.echoinhealthbackend.repository;

import com.giftedlabs.echoinhealthbackend.entity.SharedScanAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SharedScanAccessRepository extends JpaRepository<SharedScanAccess, String> {

    /**
     * Find access entry for a specific user and shared scan
     */
    Optional<SharedScanAccess> findBySharedScanIdAndUserIdAndOrganizationId(String sharedScanId, String userId,
            String organizationId);

    /**
     * Check if user has access to a shared scan
     */
    boolean existsBySharedScanIdAndUserIdAndOrganizationId(String sharedScanId, String userId, String organizationId);

    /**
     * Find all access entries for a shared scan
     */
    List<SharedScanAccess> findBySharedScanIdAndOrganizationId(String sharedScanId, String organizationId);

    /**
     * Delete all access entries for a shared scan
     */
    void deleteBySharedScanIdAndOrganizationId(String sharedScanId, String organizationId);
}
