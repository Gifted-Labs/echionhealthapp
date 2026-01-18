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
    Optional<SharedScanAccess> findBySharedScanIdAndUserId(String sharedScanId, String userId);

    /**
     * Check if user has access to a shared scan
     */
    boolean existsBySharedScanIdAndUserId(String sharedScanId, String userId);

    /**
     * Find all access entries for a shared scan
     */
    List<SharedScanAccess> findBySharedScanId(String sharedScanId);

    /**
     * Delete all access entries for a shared scan
     */
    void deleteBySharedScanId(String sharedScanId);
}
