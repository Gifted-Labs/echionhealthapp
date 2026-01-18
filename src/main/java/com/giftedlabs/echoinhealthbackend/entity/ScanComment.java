package com.giftedlabs.echoinhealthbackend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity for comments and feedback on shared scans.
 * Supports structured annotation and threaded replies.
 */
@Entity
@Table(name = "scan_comments", indexes = {
        @Index(name = "idx_comments_shared_scan", columnList = "shared_scan_id"),
        @Index(name = "idx_comments_author", columnList = "author_id"),
        @Index(name = "idx_comments_parent", columnList = "parent_id"),
        @Index(name = "idx_comments_created", columnList = "created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScanComment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shared_scan_id", nullable = false)
    private SharedScan sharedScan;

    /**
     * User who wrote the comment
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    /**
     * The comment text/feedback
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * JSON data for annotation positioning on the scan image
     * e.g., { "x": 100, "y": 200, "width": 50, "height": 50, "type": "rectangle" }
     */
    @Column(name = "annotation_data", columnDefinition = "TEXT")
    private String annotationData;

    /**
     * Parent comment for threaded replies (null for top-level comments)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private ScanComment parent;

    /**
     * Child replies to this comment
     */
    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
    @Builder.Default
    private List<ScanComment> replies = new ArrayList<>();

    /**
     * Whether this comment has been edited
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean edited = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
