package com.giftedlabs.echoinhealthbackend.controller;

import com.giftedlabs.echoinhealthbackend.dto.common.ApiResponse;
import com.giftedlabs.echoinhealthbackend.dto.vault.CreateFolderRequest;
import com.giftedlabs.echoinhealthbackend.dto.vault.FolderResponse;
import com.giftedlabs.echoinhealthbackend.service.FolderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/vault/folders")
@RequiredArgsConstructor
@Tag(name = "Folders", description = "Report organization APIs")
public class FolderController {

    private final FolderService folderService;
    private final com.giftedlabs.echoinhealthbackend.repository.UserRepository userRepository;

    @PostMapping
    @Operation(summary = "Create folder", description = "Create a new folder for organizing reports")
    public ResponseEntity<ApiResponse<FolderResponse>> createFolder(
            @Valid @RequestBody CreateFolderRequest request,
            Authentication authentication) {

        String userId = getUserId(authentication);
        FolderResponse folder = folderService.createFolder(request, userId);

        return ResponseEntity.ok(ApiResponse.<FolderResponse>builder()
                .success(true)
                .message("Folder created successfully")
                .data(folder)
                .build());
    }

    @GetMapping
    @Operation(summary = "Get root folders", description = "Get top-level folders")
    public ResponseEntity<ApiResponse<List<FolderResponse>>> getRootFolders(
            Authentication authentication) {

        String userId = getUserId(authentication);
        List<FolderResponse> folders = folderService.getRootFolders(userId);

        return ResponseEntity.ok(ApiResponse.<List<FolderResponse>>builder()
                .success(true)
                .data(folders)
                .build());
    }

    @GetMapping("/{id}/subfolders")
    @Operation(summary = "Get subfolders", description = "Get folders inside a parent folder")
    public ResponseEntity<ApiResponse<List<FolderResponse>>> getSubFolders(
            @PathVariable String id,
            Authentication authentication) {

        String userId = getUserId(authentication);
        List<FolderResponse> folders = folderService.getSubFolders(id, userId);

        return ResponseEntity.ok(ApiResponse.<List<FolderResponse>>builder()
                .success(true)
                .data(folders)
                .build());
    }

    private String getUserId(Authentication authentication) {
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(
                        () -> new com.giftedlabs.echoinhealthbackend.exception.UserNotFoundException("User not found"))
                .getId();
    }
}
