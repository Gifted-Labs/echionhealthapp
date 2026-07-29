package com.giftedlabs.echoinhealthbackend.service;

import com.giftedlabs.echoinhealthbackend.dto.admin.AuditLogResponse;
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
import java.util.EnumSet;
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
    private final AuditLogRepository auditLogRepository;
    private final BillingService billingService;
    private final FileValidationService fileValidationService;

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
            report = reportRepository.findByIdAndUserIdAndOrganizationId(
                            request.getReportId(),
                            owner.getId(),
                            owner.getOrganizationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Report not found or not owned by you"));
        }

        // Handle image upload if provided
        if (imageFile != null && !imageFile.isEmpty()) {
            fileValidationService.requireAllowedContentTypeWithDeclaredFallback(
                    imageFile,
                    java.util.Set.of("image/png", "image/jpeg", "image/webp"),
                    "Shared image must be PNG, JPG, or WEBP");
            billingService.assertStorageCapacity(owner.getOrganization(), imageFile.getSize());
            imageUrl = fileStorageService.storeFile(imageFile, owner.getOrganizationId(), owner.getId());
            imageName = imageFile.getOriginalFilename();
            imageStorageType = fileStorageService.getCurrentStorageType();
        }

        // Validate that at least one content type is provided
        if (report == null && imageUrl == null) {
            throw new IllegalArgumentException("Either a report ID or an image file is required for sharing");
        }

        // Create shared scan
        SharedScan sharedScan = SharedScan.builder()
                .organization(owner.getOrganization())
                .report(report)
                .imageUrl(imageUrl)
                .imageName(imageName)
                .imageSize(imageFile != null ? imageFile.getSize() : null)
                .imageStorageType(imageStorageType)
                .owner(owner)
                .sharingLevel(request.getSharingLevel())
                .title(request.getTitle())
                .requestMessage(request.getRequestMessage())
                .urgency(request.getUrgency() != null ? request.getUrgency() : UrgencyLevel.MEDIUM)
                .targetDepartment(request.getSharingLevel() == SharingLevel.DEPARTMENT ? request.getDepartment() : null)
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
                User colleague = userRepository.findByIdAndOrganizationId(colleagueId, owner.getOrganizationId())
                        .orElseThrow(() -> new ResourceNotFoundException("Colleague not found: " + colleagueId));

                SharedScanAccess access = SharedScanAccess.builder()
                        .organization(owner.getOrganization())
                        .sharedScan(sharedScan)
                        .user(colleague)
                        .build();
                accessRepository.save(access);
                recipients.add(colleague);
            }
        }
        // For org-wide sharing, no access records needed - all org users can access

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
                String.format("Shared scan %s with level %s [sharedScanId=%s]", shareType, request.getSharingLevel(), sharedScan.getId()));

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
        Page<SharedScan> directlyShared = sharedScanRepository.findSharedWithUser(
                user.getId(),
                user.getOrganizationId(),
                pageable);
        allSharedScans.addAll(directlyShared.map(this::mapToResponse).getContent());

        // Get EVERYONE shares (excluding own shares)
        Page<SharedScan> everyoneShared = sharedScanRepository.findByOrganizationWideSharing(
                EnumSet.of(SharingLevel.EVERYONE, SharingLevel.ORGANIZATION_WIDE),
                user.getOrganizationId(),
                user.getId(),
                pageable);
        allSharedScans.addAll(everyoneShared.map(this::mapToResponse).getContent());

        // Get DEPARTMENT shares
        if (user.getDepartment() != null && !user.getDepartment().isEmpty()) {
            Page<SharedScan> departmentShared = sharedScanRepository.findByDepartmentSharing(
                    SharingLevel.DEPARTMENT,
                    user.getDepartment(),
                    user.getOrganizationId(),
                    user.getId(),
                    pageable);
            allSharedScans.addAll(departmentShared.map(this::mapToResponse).getContent());
        }

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
        return sharedScanRepository.findByOwnerIdAndOrganizationId(owner.getId(), owner.getOrganizationId(), pageable)
                .map(this::mapToResponse);
    }

    /**
     * Get shared scan details
     */
    @Transactional
    public SharedScanResponse getSharedScan(String sharedScanId, User user) {
        SharedScan sharedScan = sharedScanRepository.findByIdWithDetails(sharedScanId, user.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Shared scan not found"));

        // Check access
        if (!canAccessSharedScan(sharedScan, user)) {
            throw new AccessDeniedException("Not authorized to access this shared scan");
        }

        // Mark as viewed if first time (only for SPECIFIC_COLLEAGUES)
        if (sharedScan.getSharingLevel() == SharingLevel.SPECIFIC_COLLEAGUES) {
            accessRepository.findBySharedScanIdAndUserIdAndOrganizationId(
                            sharedScanId,
                            user.getId(),
                            user.getOrganizationId())
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

        auditService.logAction(user, "shared_scan_accessed",
                String.format("Accessed shared scan [sharedScanId=%s]", sharedScanId));

        return mapToResponse(sharedScan);
    }

    // ========== Comments ==========

    /**
     * Add a comment to a shared scan
     */
    @Transactional
    public ScanCommentResponse addComment(String sharedScanId, AddCommentRequest request, User author) {
        SharedScan sharedScan = sharedScanRepository.findByIdWithDetails(sharedScanId, author.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Shared scan not found"));

        // Check access
        if (!canAccessSharedScan(sharedScan, author)) {
            throw new AccessDeniedException("Not authorized to comment on this shared scan");
        }

        // Handle parent comment for replies
        ScanComment parent = null;
        if (request.getParentId() != null && !request.getParentId().isEmpty()) {
            parent = commentRepository.findByIdAndOrganizationId(request.getParentId(), author.getOrganizationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent comment not found"));
        }

        ScanComment comment = ScanComment.builder()
                .organization(author.getOrganization())
                .sharedScan(sharedScan)
                .author(author)
                .content(request.getContent())
                .annotationData(request.getAnnotationData())
                .parent(parent)
                .isSuggestedImpression(request.getIsSuggestedImpression() != null ? request.getIsSuggestedImpression() : false)
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
                String.format("Added comment on shared scan [sharedScanId=%s]", sharedScanId));

        log.info("User {} added comment on shared scan {}", author.getEmail(), sharedScanId);

        return mapToCommentResponse(comment);
    }

    /**
     * Get comments for a shared scan
     */
    @Transactional(readOnly = true)
    public Page<ScanCommentResponse> getComments(String sharedScanId, User user, Pageable pageable) {
        SharedScan sharedScan = sharedScanRepository.findByIdWithDetails(sharedScanId, user.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Shared scan not found"));

        if (!canAccessSharedScan(sharedScan, user)) {
            throw new AccessDeniedException("Not authorized to view comments");
        }

        return commentRepository.findTopLevelComments(sharedScanId, user.getOrganizationId(), pageable)
                .map(this::mapToCommentResponse);
    }

    // ========== Resolution ==========

    /**
     * Mark a shared scan as resolved
     */
    @Transactional
    public SharedScanResponse resolveScan(String sharedScanId, ResolveScanRequest request, User owner) {
        SharedScan sharedScan = sharedScanRepository.findByIdWithDetails(sharedScanId, owner.getOrganizationId())
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
                String.format("Resolved shared scan [sharedScanId=%s]", sharedScanId));

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
                return accessRepository.existsBySharedScanIdAndUserIdAndOrganizationId(
                        sharedScan.getId(),
                        user.getId(),
                        user.getOrganizationId());
            case EVERYONE:
            case ORGANIZATION_WIDE:
                return sharedScan.getOrganization().getId().equals(user.getOrganizationId());
            case DEPARTMENT:
                return user.getDepartment() != null && user.getDepartment().equals(sharedScan.getTargetDepartment()) &&
                        sharedScan.getOrganization().getId().equals(user.getOrganizationId());
            default:
                return false;
        }
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> getAuditTrail(String sharedScanId, User user, Pageable pageable) {
        SharedScan sharedScan = sharedScanRepository.findByIdWithDetails(sharedScanId, user.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Shared scan not found"));
        if (!canAccessSharedScan(sharedScan, user)) {
            throw new AccessDeniedException("Not authorized to view audit trail");
        }

        return auditLogRepository.findByOrganizationIdAndDetailsContainingOrderByCreatedAtDesc(
                        user.getOrganizationId(),
                        "[sharedScanId=" + sharedScanId + "]",
                        pageable)
                .map(log -> AuditLogResponse.builder()
                        .id(log.getId())
                        .userId(log.getUser() != null ? log.getUser().getId() : null)
                        .userEmail(log.getUserEmail())
                        .action(log.getAction())
                        .details(log.getDetails())
                        .ipAddress(log.getIpAddress())
                        .userAgent(log.getUserAgent())
                        .success(log.getSuccess())
                        .errorMessage(log.getErrorMessage())
                        .createdAt(log.getCreatedAt())
                        .build());
    }

    private List<User> getCollaborators(SharedScan sharedScan) {
        List<User> collaborators = new ArrayList<>();

        // Get users who have commented
        commentRepository.findBySharedScanIdAndOrganizationId(sharedScan.getId(), sharedScan.getOrganization().getId(),
                Pageable.unpaged())
                .forEach(c -> {
                    if (!c.getAuthor().getId().equals(sharedScan.getOwner().getId())) {
                        collaborators.add(c.getAuthor());
                    }
                });

        // Get users with explicit access (for SPECIFIC_COLLEAGUES)
        if (sharedScan.getSharingLevel() == SharingLevel.SPECIFIC_COLLEAGUES) {
            accessRepository.findBySharedScanIdAndOrganizationId(sharedScan.getId(), sharedScan.getOrganization().getId())
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
                .urgency(sharedScan.getUrgency())
                .department(sharedScan.getTargetDepartment())
                .commentCount(commentRepository.countBySharedScanIdAndOrganizationId(
                        sharedScan.getId(),
                        sharedScan.getOrganization().getId()))
                .accessCount(accessRepository.findBySharedScanIdAndOrganizationId(
                        sharedScan.getId(),
                        sharedScan.getOrganization().getId()).size())
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
                .isSuggestedImpression(comment.getIsSuggestedImpression())
                .parentId(comment.getParent() != null ? comment.getParent().getId() : null)
                .replies(replies)
                .replyCount(replies.size())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }
}
