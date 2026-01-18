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
    @Query("SELECT c FROM ScanComment c WHERE c.sharedScan.id = :sharedScanId AND c.parent IS NULL ORDER BY c.createdAt ASC")
    Page<ScanComment> findTopLevelComments(@Param("sharedScanId") String sharedScanId, Pageable pageable);

    /**
     * Find all comments for a shared scan
     */
    Page<ScanComment> findBySharedScanId(String sharedScanId, Pageable pageable);

    /**
     * Find replies to a specific comment
     */
    List<ScanComment> findByParentIdOrderByCreatedAtAsc(String parentId);

    /**
     * Count comments on a shared scan
     */
    long countBySharedScanId(String sharedScanId);

    /**
     * Count comments by a specific author on a shared scan
     */
    long countBySharedScanIdAndAuthorId(String sharedScanId, String authorId);
}
