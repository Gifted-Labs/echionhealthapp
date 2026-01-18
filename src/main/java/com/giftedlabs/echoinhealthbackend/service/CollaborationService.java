package com.giftedlabs.echoinhealthbackend.service;

import com.giftedlabs.echoinhealthbackend.dto.collaboration.*;
import com.giftedlabs.echoinhealthbackend.entity.*;
import com.giftedlabs.echoinhealthbackend.exception.ResourceNotFoundException;
import com.giftedlabs.echoinhealthbackend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for scan collaboration - sharing, commenting, and resolution
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
    private final NotificationService notificationService;
    private final AuditService auditService;

    // ========== Share Scan ==========

    /**
     * Share a scan for collaboration
     */
    @Transactional
    public SharedScanResponse shareScan(ShareScanRequest request, User owner) {
        // Get the report
        Report report = reportRepository.findByIdAndUserId(request.getReportId(), owner.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Report not found or not owned by you"));

        // Create shared scan
        SharedScan sharedScan = SharedScan.builder()
                .report(report)
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
        } else if (request.getSharingLevel() == SharingLevel.DEPARTMENT_WIDE) {
            // Find users in same department
            recipients = userRepository.findAll().stream()
                    .filter(u -> owner.getDepartment() != null &&
                            owner.getDepartment().equals(u.getDepartment()) &&
                            !u.getId().equals(owner.getId()))
                    .collect(Collectors.toList());
        } else if (request.getSharingLevel() == SharingLevel.FACILITY_WIDE) {
            // Find users in same hospital
            recipients = userRepository.findAll().stream()
                    .filter(u -> owner.getHospitalName() != null &&
                            owner.getHospitalName().equals(u.getHospitalName()) &&
                            !u.getId().equals(owner.getId()))
                    .collect(Collectors.toList());
        }

        // Send notifications to recipients
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
        auditService.logAction(owner, "scan_shared",
                String.format("Shared scan %s with level %s", report.getId(), request.getSharingLevel()));

        log.info("User {} shared scan {} with {} recipients",
                owner.getEmail(), sharedScan.getId(), recipients.size());

        return mapToResponse(sharedScan);
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

        // Get department-wide shares
        if (user.getDepartment() != null) {
            Page<SharedScan> deptShared = sharedScanRepository.findByDepartment(
                    SharingLevel.DEPARTMENT_WIDE, user.getDepartment(), user.getId(), pageable);
            allSharedScans.addAll(deptShared.map(this::mapToResponse).getContent());
        }

        // Get facility-wide shares
        if (user.getHospitalName() != null) {
            Page<SharedScan> facilityShared = sharedScanRepository.findByFacility(
                    SharingLevel.FACILITY_WIDE, user.getHospitalName(), user.getId(), pageable);
            allSharedScans.addAll(facilityShared.map(this::mapToResponse).getContent());
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
            throw new SecurityException("Not authorized to access this shared scan");
        }

        // Mark as viewed if first time
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
            throw new SecurityException("Not authorized to comment on this shared scan");
        }

        // Handle parent comment for replies
        ScanComment parent = null;
        if (request.getParentId() != null) {
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
            throw new SecurityException("Not authorized to view comments");
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
            throw new SecurityException("Only the owner can resolve a shared scan");
        }

        sharedScan.setStatus(SharedScanStatus.RESOLVED);
        sharedScan.setResolvedAt(LocalDateTime.now());
        sharedScan.setResolutionNotes(request.getResolutionNotes());
        sharedScan = sharedScanRepository.save(sharedScan);

        // Notify collaborators
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
            case DEPARTMENT_WIDE:
                return user.getDepartment() != null &&
                        user.getDepartment().equals(sharedScan.getOwner().getDepartment());
            case FACILITY_WIDE:
                return user.getHospitalName() != null &&
                        user.getHospitalName().equals(sharedScan.getOwner().getHospitalName());
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

        // Get users with explicit access
        if (sharedScan.getSharingLevel() == SharingLevel.SPECIFIC_COLLEAGUES) {
            accessRepository.findBySharedScanId(sharedScan.getId())
                    .forEach(a -> collaborators.add(a.getUser()));
        }

        return collaborators.stream().distinct().collect(Collectors.toList());
    }

    private SharedScanResponse mapToResponse(SharedScan sharedScan) {
        return SharedScanResponse.builder()
                .id(sharedScan.getId())
                .reportId(sharedScan.getReport().getId())
                .reportPatientName(sharedScan.getReport().getPatientName())
                .reportScanType(sharedScan.getReport().getScanType() != null
                        ? sharedScan.getReport().getScanType().name()
                        : null)
                .reportScanDate(sharedScan.getReport().getScanDate() != null
                        ? sharedScan.getReport().getScanDate().atStartOfDay()
                        : null)
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
                .build();
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
