package com.giftedlabs.echoinhealthbackend.repository;

import com.giftedlabs.echoinhealthbackend.entity.ScanComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScanCommentRepository extends JpaRepository<ScanComment, String> {

    /**
     * Find all top-level comments for a shared scan (no parent)
     */
    @Query("SELECT c FROM ScanComment c WHERE c.sharedScan.id = :sharedScanId AND c.organization.id = :organizationId AND c.parent IS NULL ORDER BY c.createdAt ASC")
    Page<ScanComment> findTopLevelComments(@Param("sharedScanId") String sharedScanId,
            @Param("organizationId") String organizationId, Pageable pageable);

    /**
     * Find all comments for a shared scan
     */
    Page<ScanComment> findBySharedScanIdAndOrganizationId(String sharedScanId, String organizationId, Pageable pageable);

    /**
     * Find replies to a specific comment
     */
    List<ScanComment> findByParentIdAndOrganizationIdOrderByCreatedAtAsc(String parentId, String organizationId);

    java.util.Optional<ScanComment> findByIdAndOrganizationId(String id, String organizationId);

    /**
     * Count comments on a shared scan
     */
    long countBySharedScanIdAndOrganizationId(String sharedScanId, String organizationId);

    /**
     * Count comments by a specific author on a shared scan
     */
    long countBySharedScanIdAndAuthorIdAndOrganizationId(String sharedScanId, String authorId, String organizationId);
}
