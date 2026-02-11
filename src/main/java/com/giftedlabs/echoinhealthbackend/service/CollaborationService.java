package com.giftedlabs.echoinhealthbackend.service;

import com.giftedlabs.echoinhealthbackend.dto.collaboration.*;
import com.giftedlabs.echoinhealthbackend.entity.*;
import com.giftedlabs.echoinhealthbackend.exception.AccessDeniedException;
import com.giftedlabs.echoinhealthbackend.exception.ResourceNotFoundException;
import com.giftedlabs.echoinhealthbackend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for SonoShare collaboration - sharing scans/images, commenting, and
 * resolution.
 * Supports two sharing levels:
 * - SPECIFIC_COLLEAGUES: Share with selected users only
 * - EVERYONE: Share with all users on the system
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CollaborationService {

    private final SharedScanRepository sharedScanRepository;
    private final SharedScanAccessRepository accessRepository;
    private final ScanCommentRepository commentRepository;
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final NotificationService notificationService;
    private final AuditService auditService;

    // ========== Share Scan/Image ==========

    /**
     * Share a scan or image for collaboration.
     * Can share a report, an uploaded image, or both.
     *
     * @param request   Share request with reportId (optional)
     * @param imageFile Optional image file to upload
     * @param owner     User sharing the scan/image
     * @return Shared scan response
     */
    @Transactional
    public SharedScanResponse shareScan(ShareScanRequest request, MultipartFile imageFile, User owner) {
        Report report = null;
        String imageUrl = null;
        String imageName = null;
        StorageType imageStorageType = null;

        // Get report if provided
        if (request.getReportId() != null && !request.getReportId().isEmpty()) {
            report = reportRepository.findByIdAndUserId(request.getReportId(), owner.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Report not found or not owned by you"));
        }

        // Handle image upload if provided
        if (imageFile != null && !imageFile.isEmpty()) {
            imageUrl = fileStorageService.storeFile(imageFile, owner.getId());
            imageName = imageFile.getOriginalFilename();
            imageStorageType = fileStorageService.getCurrentStorageType();
        }

        // Validate that at least one content type is provided
        if (report == null && imageUrl == null) {
            throw new IllegalArgumentException("Either a report ID or an image file is required for sharing");
        }

        // Create shared scan
        SharedScan sharedScan = SharedScan.builder()
                .report(report)
                .imageUrl(imageUrl)
                .imageName(imageName)
                .imageStorageType(imageStorageType)
                .owner(owner)
                .sharingLevel(request.getSharingLevel())
                .title(request.getTitle())
                .requestMessage(request.getRequestMessage())
                .status(SharedScanStatus.PENDING_REVIEW)
                .build();

        sharedScan = sharedScanRepository.save(sharedScan);

        // Handle access based on sharing level
        List<User> recipients = new ArrayList<>();

        if (request.getSharingLevel() == SharingLevel.SPECIFIC_COLLEAGUES) {
            if (request.getColleagueIds() == null || request.getColleagueIds().isEmpty()) {
                throw new IllegalArgumentException("Colleague IDs required for SPECIFIC_COLLEAGUES sharing");
            }

            for (String colleagueId : request.getColleagueIds()) {
                User colleague = userRepository.findById(colleagueId)
                        .orElseThrow(() -> new ResourceNotFoundException("Colleague not found: " + colleagueId));

                SharedScanAccess access = SharedScanAccess.builder()
                        .sharedScan(sharedScan)
                        .user(colleague)
                        .build();
                accessRepository.save(access);
                recipients.add(colleague);
            }
        }
        // For EVERYONE sharing, no access records needed - all users can access

        // Send notifications to recipients (only for SPECIFIC_COLLEAGUES)
        for (User recipient : recipients) {
            notificationService.createNotification(
                    recipient,
                    owner,
                    NotificationType.NEW_SHARE,
                    sharedScan,
                    null,
                    "New scan shared for review",
                    String.format("%s shared a scan for your review: %s",
                            owner.getFullName(),
                            request.getTitle() != null ? request.getTitle() : "Untitled"));
        }

        // Audit log
        String shareType = report != null ? "report " + report.getId() : "image " + imageName;
        auditService.logAction(owner, "scan_shared",
                String.format("Shared %s with level %s", shareType, request.getSharingLevel()));

        log.info("User {} shared {} with level {}, {} specific recipients",
                owner.getEmail(), shareType, request.getSharingLevel(), recipients.size());

        return mapToResponse(sharedScan);
    }

    /**
     * Share a scan (backward compatible - no image)
     */
    @Transactional
    public SharedScanResponse shareScan(ShareScanRequest request, User owner) {
        return shareScan(request, null, owner);
    }

    // ========== Get Shared Scans ==========

    /**
     * Get scans shared with current user
     */
    @Transactional(readOnly = true)
    public Page<SharedScanResponse> getScansSharedWithMe(User user, Pageable pageable) {
        List<SharedScanResponse> allSharedScans = new ArrayList<>();

        // Get directly shared (SPECIFIC_COLLEAGUES)
        Page<SharedScan> directlyShared = sharedScanRepository.findSharedWithUser(user.getId(), pageable);
        allSharedScans.addAll(directlyShared.map(this::mapToResponse).getContent());

        // Get EVERYONE shares (excluding own shares)
        Page<SharedScan> everyoneShared = sharedScanRepository.findByEveryoneSharing(
                SharingLevel.EVERYONE, user.getId(), pageable);
        allSharedScans.addAll(everyoneShared.map(this::mapToResponse).getContent());

        // Remove duplicates and sort by created date
        List<SharedScanResponse> uniqueScans = allSharedScans.stream()
                .distinct()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .collect(Collectors.toList());

        return new PageImpl<>(uniqueScans, pageable, uniqueScans.size());
    }

    /**
     * Get scans I've shared
     */
    @Transactional(readOnly = true)
    public Page<SharedScanResponse> getMySharedScans(User owner, Pageable pageable) {
        return sharedScanRepository.findByOwnerId(owner.getId(), pageable)
                .map(this::mapToResponse);
    }

    /**
     * Get shared scan details
     */
    @Transactional(readOnly = true)
    public SharedScanResponse getSharedScan(String sharedScanId, User user) {
        SharedScan sharedScan = sharedScanRepository.findByIdWithDetails(sharedScanId)
                .orElseThrow(() -> new ResourceNotFoundException("Shared scan not found"));

        // Check access
        if (!canAccessSharedScan(sharedScan, user)) {
            throw new AccessDeniedException("Not authorized to access this shared scan");
        }

        // Mark as viewed if first time (only for SPECIFIC_COLLEAGUES)
        if (sharedScan.getSharingLevel() == SharingLevel.SPECIFIC_COLLEAGUES) {
            accessRepository.findBySharedScanIdAndUserId(sharedScanId, user.getId())
                    .ifPresent(access -> {
                        if (access.getViewedAt() == null) {
                            access.setViewedAt(LocalDateTime.now());
                            accessRepository.save(access);
                        }
                    });
        }

        // Update status to IN_REVIEW if still pending
        if (sharedScan.getStatus() == SharedScanStatus.PENDING_REVIEW) {
            sharedScan.setStatus(SharedScanStatus.IN_REVIEW);
            sharedScanRepository.save(sharedScan);
        }

        return mapToResponse(sharedScan);
    }

    // ========== Comments ==========

    /**
     * Add a comment to a shared scan
     */
    @Transactional
    public ScanCommentResponse addComment(String sharedScanId, AddCommentRequest request, User author) {
        SharedScan sharedScan = sharedScanRepository.findById(sharedScanId)
                .orElseThrow(() -> new ResourceNotFoundException("Shared scan not found"));

        // Check access
        if (!canAccessSharedScan(sharedScan, author)) {
            throw new AccessDeniedException("Not authorized to comment on this shared scan");
        }

        // Handle parent comment for replies
        ScanComment parent = null;
        if (request.getParentId() != null && !request.getParentId().isEmpty()) {
            parent = commentRepository.findById(request.getParentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent comment not found"));
        }

        ScanComment comment = ScanComment.builder()
                .sharedScan(sharedScan)
                .author(author)
                .content(request.getContent())
                .annotationData(request.getAnnotationData())
                .parent(parent)
                .build();

        comment = commentRepository.save(comment);

        // Notify the scan owner
        if (!author.getId().equals(sharedScan.getOwner().getId())) {
            notificationService.createNotification(
                    sharedScan.getOwner(),
                    author,
                    NotificationType.NEW_COMMENT,
                    sharedScan,
                    comment,
                    "New comment on your shared scan",
                    String.format("%s commented: %s",
                            author.getFullName(),
                            request.getContent().length() > 50
                                    ? request.getContent().substring(0, 50) + "..."
                                    : request.getContent()));
        }

        // Notify parent comment author for replies
        if (parent != null && !author.getId().equals(parent.getAuthor().getId())) {
            notificationService.createNotification(
                    parent.getAuthor(),
                    author,
                    NotificationType.COMMENT_REPLY,
                    sharedScan,
                    comment,
                    "New reply to your comment",
                    String.format("%s replied: %s",
                            author.getFullName(),
                            request.getContent().length() > 50
                                    ? request.getContent().substring(0, 50) + "..."
                                    : request.getContent()));
        }

        // Audit log
        auditService.logAction(author, "comment_added",
                String.format("Added comment on shared scan %s", sharedScanId));

        log.info("User {} added comment on shared scan {}", author.getEmail(), sharedScanId);

        return mapToCommentResponse(comment);
    }

    /**
     * Get comments for a shared scan
     */
    @Transactional(readOnly = true)
    public Page<ScanCommentResponse> getComments(String sharedScanId, User user, Pageable pageable) {
        SharedScan sharedScan = sharedScanRepository.findById(sharedScanId)
                .orElseThrow(() -> new ResourceNotFoundException("Shared scan not found"));

        if (!canAccessSharedScan(sharedScan, user)) {
            throw new AccessDeniedException("Not authorized to view comments");
        }

        return commentRepository.findTopLevelComments(sharedScanId, pageable)
                .map(this::mapToCommentResponse);
    }

    // ========== Resolution ==========

    /**
     * Mark a shared scan as resolved
     */
    @Transactional
    public SharedScanResponse resolveScan(String sharedScanId, ResolveScanRequest request, User owner) {
        SharedScan sharedScan = sharedScanRepository.findById(sharedScanId)
                .orElseThrow(() -> new ResourceNotFoundException("Shared scan not found"));

        if (!sharedScan.getOwner().getId().equals(owner.getId())) {
            throw new AccessDeniedException("Only the owner can resolve a shared scan");
        }

        sharedScan.setStatus(SharedScanStatus.RESOLVED);
        sharedScan.setResolvedAt(LocalDateTime.now());
        sharedScan.setResolutionNotes(request.getResolutionNotes());
        sharedScan = sharedScanRepository.save(sharedScan);

        // Notify collaborators (only for SPECIFIC_COLLEAGUES)
        if (sharedScan.getSharingLevel() == SharingLevel.SPECIFIC_COLLEAGUES) {
            List<User> collaborators = getCollaborators(sharedScan);
            for (User collaborator : collaborators) {
                notificationService.createNotification(
                        collaborator,
                        owner,
                        NotificationType.SCAN_RESOLVED,
                        sharedScan,
                        null,
                        "Shared scan resolved",
                        String.format("%s marked the shared scan as resolved", owner.getFullName()));
            }
        }

        auditService.logAction(owner, "scan_resolved",
                String.format("Resolved shared scan %s", sharedScanId));

        log.info("User {} resolved shared scan {}", owner.getEmail(), sharedScanId);

        return mapToResponse(sharedScan);
    }

    // ========== Helper Methods ==========

    private boolean canAccessSharedScan(SharedScan sharedScan, User user) {
        // Owner always has access
        if (sharedScan.getOwner().getId().equals(user.getId())) {
            return true;
        }

        switch (sharedScan.getSharingLevel()) {
            case SPECIFIC_COLLEAGUES:
                return accessRepository.existsBySharedScanIdAndUserId(sharedScan.getId(), user.getId());
            case EVERYONE:
                // All authenticated users can access EVERYONE shares
                return true;
            default:
                return false;
        }
    }

    private List<User> getCollaborators(SharedScan sharedScan) {
        List<User> collaborators = new ArrayList<>();

        // Get users who have commented
        commentRepository.findBySharedScanId(sharedScan.getId(), Pageable.unpaged())
                .forEach(c -> {
                    if (!c.getAuthor().getId().equals(sharedScan.getOwner().getId())) {
                        collaborators.add(c.getAuthor());
                    }
                });

        // Get users with explicit access (for SPECIFIC_COLLEAGUES)
        if (sharedScan.getSharingLevel() == SharingLevel.SPECIFIC_COLLEAGUES) {
            accessRepository.findBySharedScanId(sharedScan.getId())
                    .forEach(a -> collaborators.add(a.getUser()));
        }

        return collaborators.stream().distinct().collect(Collectors.toList());
    }

    private SharedScanResponse mapToResponse(SharedScan sharedScan) {
        SharedScanResponse.SharedScanResponseBuilder builder = SharedScanResponse.builder()
                .id(sharedScan.getId())
                .ownerId(sharedScan.getOwner().getId())
                .ownerName(sharedScan.getOwner().getFullName())
                .ownerEmail(sharedScan.getOwner().getEmail())
                .ownerDepartment(sharedScan.getOwner().getDepartment())
                .ownerHospital(sharedScan.getOwner().getHospitalName())
                .sharingLevel(sharedScan.getSharingLevel())
                .status(sharedScan.getStatus())
                .title(sharedScan.getTitle())
                .requestMessage(sharedScan.getRequestMessage())
                .commentCount(commentRepository.countBySharedScanId(sharedScan.getId()))
                .accessCount(accessRepository.findBySharedScanId(sharedScan.getId()).size())
                .createdAt(sharedScan.getCreatedAt())
                .resolvedAt(sharedScan.getResolvedAt())
                .resolutionNotes(sharedScan.getResolutionNotes())
                .hasReport(sharedScan.hasReport())
                .hasImage(sharedScan.hasImage());

        // Report fields (if present)
        if (sharedScan.hasReport()) {
            Report report = sharedScan.getReport();
            builder.reportId(report.getId())
                    .reportPatientName(report.getPatientName())
                    .reportScanType(report.getScanType() != null ? report.getScanType().name() : null)
                    .reportScanDate(report.getScanDate() != null ? report.getScanDate().atStartOfDay() : null);
        }

        // Image fields (if present)
        if (sharedScan.hasImage()) {
            builder.imageUrl(sharedScan.getImageUrl())
                    .imageName(sharedScan.getImageName())
                    .imageStorageType(sharedScan.getImageStorageType());
        }

        return builder.build();
    }

    private ScanCommentResponse mapToCommentResponse(ScanComment comment) {
        List<ScanCommentResponse> replies = comment.getReplies() != null
                ? comment.getReplies().stream().map(this::mapToCommentResponse).collect(Collectors.toList())
                : new ArrayList<>();

        return ScanCommentResponse.builder()
                .id(comment.getId())
                .sharedScanId(comment.getSharedScan().getId())
                .authorId(comment.getAuthor().getId())
                .authorName(comment.getAuthor().getFullName())
                .authorEmail(comment.getAuthor().getEmail())
                .content(comment.getContent())
                .annotationData(comment.getAnnotationData())
                .edited(comment.getEdited())
                .parentId(comment.getParent() != null ? comment.getParent().getId() : null)
                .replies(replies)
                .replyCount(replies.size())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }
}
