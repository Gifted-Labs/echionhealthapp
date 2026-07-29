package com.giftedlabs.echoinhealthbackend.controller;

import com.giftedlabs.echoinhealthbackend.dto.common.ApiResponse;
import com.giftedlabs.echoinhealthbackend.dto.signature.SignatureResponse;
import com.giftedlabs.echoinhealthbackend.dto.signature.UpdateSignatureRequest;
import com.giftedlabs.echoinhealthbackend.security.CurrentUserService;
import com.giftedlabs.echoinhealthbackend.service.SignatureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/signatures")
@RequiredArgsConstructor
@Tag(name = "Signatures", description = "Digital signature management APIs")
@PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'RADIOLOGIST', 'PHYSICIAN', 'SONOGRAPHER', 'ADMIN', 'SUPER_ADMIN')")
public class SignatureController {

    private final SignatureService signatureService;
    private final CurrentUserService currentUserService;

    @GetMapping
    @Operation(summary = "List signatures", description = "Get all signatures for the current user")
    public ResponseEntity<ApiResponse<List<SignatureResponse>>> getSignatures(Authentication authentication) {
        List<SignatureResponse> signatures = signatureService.getUserSignatures(currentUserService.requireUser(authentication));
        return ResponseEntity.ok(ApiResponse.success(signatures));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload signature", description = "Upload a new signature image")
    public ResponseEntity<ApiResponse<SignatureResponse>> uploadSignature(
            @RequestPart("file") MultipartFile file,
            @RequestParam("label") String label,
            @RequestParam(value = "isDefault", required = false) Boolean isDefault,
            Authentication authentication) {
        SignatureResponse response = signatureService.uploadSignature(
                currentUserService.requireUser(authentication),
                file,
                label,
                Boolean.TRUE.equals(isDefault));
        return ResponseEntity.ok(ApiResponse.success("Signature uploaded successfully", response));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update signature", description = "Update signature metadata")
    public ResponseEntity<ApiResponse<SignatureResponse>> updateSignature(
            @PathVariable String id,
            @Valid @RequestBody UpdateSignatureRequest request,
            Authentication authentication) {
        SignatureResponse response = signatureService.updateSignature(
                currentUserService.requireUser(authentication),
                id,
                request);
        return ResponseEntity.ok(ApiResponse.success("Signature updated successfully", response));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete signature", description = "Delete one of the current user's signatures")
    public ResponseEntity<ApiResponse<Void>> deleteSignature(@PathVariable String id, Authentication authentication) {
        signatureService.deleteSignature(currentUserService.requireUser(authentication), id);
        return ResponseEntity.ok(ApiResponse.success("Signature deleted successfully", null));
    }
}
