package com.giftedlabs.echoinhealthbackend.controller;

import com.giftedlabs.echoinhealthbackend.dto.collaboration.*;
import com.giftedlabs.echoinhealthbackend.dto.common.ApiResponse;
import com.giftedlabs.echoinhealthbackend.entity.SharingLevel;
import com.giftedlabs.echoinhealthbackend.entity.User;
import com.giftedlabs.echoinhealthbackend.exception.ResourceNotFoundException;
import com.giftedlabs.echoinhealthbackend.repository.UserRepository;
import com.giftedlabs.echoinhealthbackend.service.CollaborationService;
import com.giftedlabs.echoinhealthbackend.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * Controller for SonoShare collaboration features.
 * Supports sharing scans or images with specific colleagues or everyone.
 */
@RestController
@RequestMapping("/collaboration")
@RequiredArgsConstructor
@Tag(name = "Collaboration", description = "SonoShare - Scan/Image sharing and collaboration APIs")
public class CollaborationController {

        private final CollaborationService collaborationService;
        private final NotificationService notificationService;
        private final UserRepository userRepository;

        // ========== Share Scan/Image ==========

        /**
         * Share a report only (JSON request)
         */
        @PostMapping("/share")
        @Operation(summary = "Share scan", description = "Share a scan/report for peer review (JSON request)")
        public ResponseEntity<ApiResponse<SharedScanResponse>> shareScan(
                        @Valid @RequestBody ShareScanRequest request,
                        Authentication authentication) {

                User user = getUser(authentication);
                SharedScanResponse response = collaborationService.shareScan(request, user);
                return ResponseEntity.ok(ApiResponse.<SharedScanResponse>builder()
                                .success(true)
                                .message("Scan shared successfully")
                                .data(response)
                                .build());
        }

        /**
         * Share an image with optional report (multipart request)
         */
        @PostMapping(value = "/share-with-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @Operation(summary = "Share with image", description = "Share an image (and optionally a report) for peer review")
        public ResponseEntity<ApiResponse<SharedScanResponse>> shareWithImage(
                        @RequestPart(value = "image") MultipartFile imageFile,
                        @RequestPart(value = "reportId", required = false) String reportId,
                        @RequestPart(value = "sharingLevel") String sharingLevel,
                        @RequestPart(value = "colleagueIds", required = false) List<String> colleagueIds,
                        @RequestPart(value = "title", required = false) String title,
                        @RequestPart(value = "requestMessage", required = false) String requestMessage,
                        Authentication authentication) {

                User user = getUser(authentication);

                ShareScanRequest request = ShareScanRequest.builder()
                                .reportId(reportId)
                                .sharingLevel(SharingLevel.valueOf(sharingLevel.toUpperCase()))
                                .colleagueIds(colleagueIds)
                                .title(title)
                                .requestMessage(requestMessage)
                                .build();

                SharedScanResponse response = collaborationService.shareScan(request, imageFile, user);
                return ResponseEntity.ok(ApiResponse.<SharedScanResponse>builder()
                                .success(true)
                                .message("Image shared successfully")
                                .data(response)
                                .build());
        }

        // ========== Get Shared Scans ==========

        @GetMapping("/shared-with-me")
        @Operation(summary = "Scans shared with me", description = "Get scans/images shared with the current user")
        public ResponseEntity<ApiResponse<Page<SharedScanResponse>>> getScansSharedWithMe(
                        Authentication authentication,
                        Pageable pageable) {

                User user = getUser(authentication);
                Page<SharedScanResponse> scans = collaborationService.getScansSharedWithMe(user, pageable);
                return ResponseEntity.ok(ApiResponse.<Page<SharedScanResponse>>builder()
                                .success(true)
                                .data(scans)
                                .build());
        }

        @GetMapping("/my-shares")
        @Operation(summary = "My shared scans", description = "Get scans/images I've shared")
        public ResponseEntity<ApiResponse<Page<SharedScanResponse>>> getMySharedScans(
                        Authentication authentication,
                        Pageable pageable) {

                User user = getUser(authentication);
                Page<SharedScanResponse> scans = collaborationService.getMySharedScans(user, pageable);
                return ResponseEntity.ok(ApiResponse.<Page<SharedScanResponse>>builder()
                                .success(true)
                                .data(scans)
                                .build());
        }

        @GetMapping("/{id}")
        @Operation(summary = "Get shared scan", description = "Get details of a shared scan/image")
        public ResponseEntity<ApiResponse<SharedScanResponse>> getSharedScan(
                        @PathVariable String id,
                        Authentication authentication) {

                User user = getUser(authentication);
                SharedScanResponse response = collaborationService.getSharedScan(id, user);
                return ResponseEntity.ok(ApiResponse.<SharedScanResponse>builder()
                                .success(true)
                                .data(response)
                                .build());
        }

        // ========== Comments ==========

        @PostMapping("/{id}/comments")
        @Operation(summary = "Add comment", description = "Add a comment or feedback to a shared scan/image")
        public ResponseEntity<ApiResponse<ScanCommentResponse>> addComment(
                        @PathVariable String id,
                        @Valid @RequestBody AddCommentRequest request,
                        Authentication authentication) {

                User user = getUser(authentication);
                ScanCommentResponse response = collaborationService.addComment(id, request, user);
                return ResponseEntity.ok(ApiResponse.<ScanCommentResponse>builder()
                                .success(true)
                                .message("Comment added successfully")
                                .data(response)
                                .build());
        }

        @GetMapping("/{id}/comments")
        @Operation(summary = "Get comments", description = "Get comments for a shared scan/image")
        public ResponseEntity<ApiResponse<Page<ScanCommentResponse>>> getComments(
                        @PathVariable String id,
                        Authentication authentication,
                        Pageable pageable) {

                User user = getUser(authentication);
                Page<ScanCommentResponse> comments = collaborationService.getComments(id, user, pageable);
                return ResponseEntity.ok(ApiResponse.<Page<ScanCommentResponse>>builder()
                                .success(true)
                                .data(comments)
                                .build());
        }

        // ========== Resolution ==========

        @PutMapping("/{id}/resolve")
        @Operation(summary = "Resolve scan", description = "Mark a shared scan/image as resolved")
        public ResponseEntity<ApiResponse<SharedScanResponse>> resolveScan(
                        @PathVariable String id,
                        @RequestBody ResolveScanRequest request,
                        Authentication authentication) {

                User user = getUser(authentication);
                SharedScanResponse response = collaborationService.resolveScan(id, request, user);
                return ResponseEntity.ok(ApiResponse.<SharedScanResponse>builder()
                                .success(true)
                                .message("Scan marked as resolved")
                                .data(response)
                                .build());
        }

        // ========== Notifications ==========

        @GetMapping(path = "/notifications/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        @Operation(summary = "Subscribe to notifications", description = "Subscribe to real-time notifications via SSE")
        public SseEmitter subscribeToNotifications(Authentication authentication) {
                User user = getUser(authentication);
                return notificationService.subscribe(user.getId());
        }

        @GetMapping("/notifications")
        @Operation(summary = "Get notifications", description = "Get paginated notifications")
        public ResponseEntity<ApiResponse<Page<NotificationResponse>>> getNotifications(
                        Authentication authentication,
                        Pageable pageable) {

                User user = getUser(authentication);
                Page<NotificationResponse> notifications = notificationService.getNotifications(user.getId(), pageable);
                return ResponseEntity.ok(ApiResponse.<Page<NotificationResponse>>builder()
                                .success(true)
                                .data(notifications)
                                .build());
        }

        @GetMapping("/notifications/unread")
        @Operation(summary = "Get unread notifications", description = "Get all unread notifications")
        public ResponseEntity<ApiResponse<List<NotificationResponse>>> getUnreadNotifications(
                        Authentication authentication) {

                User user = getUser(authentication);
                List<NotificationResponse> notifications = notificationService.getUnreadNotifications(user.getId());
                return ResponseEntity.ok(ApiResponse.<List<NotificationResponse>>builder()
                                .success(true)
                                .data(notifications)
                                .build());
        }

        @GetMapping("/notifications/unread-count")
        @Operation(summary = "Unread count", description = "Get count of unread notifications")
        public ResponseEntity<ApiResponse<Long>> getUnreadCount(Authentication authentication) {
                User user = getUser(authentication);
                long count = notificationService.getUnreadCount(user.getId());
                return ResponseEntity.ok(ApiResponse.<Long>builder()
                                .success(true)
                                .data(count)
                                .build());
        }

        @PutMapping("/notifications/{id}/read")
        @Operation(summary = "Mark as read", description = "Mark a notification as read")
        public ResponseEntity<ApiResponse<NotificationResponse>> markAsRead(
                        @PathVariable String id,
                        Authentication authentication) {

                User user = getUser(authentication);
                NotificationResponse response = notificationService.markAsRead(id, user.getId());
                return ResponseEntity.ok(ApiResponse.<NotificationResponse>builder()
                                .success(true)
                                .data(response)
                                .build());
        }

        @PutMapping("/notifications/read-all")
        @Operation(summary = "Mark all as read", description = "Mark all notifications as read")
        public ResponseEntity<ApiResponse<Integer>> markAllAsRead(Authentication authentication) {
                User user = getUser(authentication);
                int count = notificationService.markAllAsRead(user.getId());
                return ResponseEntity.ok(ApiResponse.<Integer>builder()
                                .success(true)
                                .message(count + " notifications marked as read")
                                .data(count)
                                .build());
        }

        // ========== Helper ==========

        private User getUser(Authentication authentication) {
                UserDetails userDetails = (UserDetails) authentication.getPrincipal();
                String email = userDetails.getUsername();
                return userRepository.findByEmail(email)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        }
}
