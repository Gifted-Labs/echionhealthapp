package com.giftedlabs.echoinhealthbackend.service;

import com.giftedlabs.echoinhealthbackend.dto.signature.SignatureResponse;
import com.giftedlabs.echoinhealthbackend.dto.signature.UpdateSignatureRequest;
import com.giftedlabs.echoinhealthbackend.entity.Signature;
import com.giftedlabs.echoinhealthbackend.entity.User;
import com.giftedlabs.echoinhealthbackend.exception.AccessDeniedException;
import com.giftedlabs.echoinhealthbackend.exception.ResourceNotFoundException;
import com.giftedlabs.echoinhealthbackend.repository.SignatureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SignatureService {

    private static final long MAX_SIGNATURE_SIZE_BYTES = 2L * 1024L * 1024L;

    private final SignatureRepository signatureRepository;
    private final FileStorageService fileStorageService;
    private final AuditService auditService;
    private final BillingService billingService;
    private final FileValidationService fileValidationService;

    @Transactional(readOnly = true)
    public List<SignatureResponse> getUserSignatures(User user) {
        return signatureRepository.findByUserIdAndOrganizationIdOrderByCreatedAtDesc(
                        user.getId(),
                        user.getOrganizationId())
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public SignatureResponse uploadSignature(User user, MultipartFile file, String label, boolean makeDefault) {
        requireSignaturePermission(user);
        validateFile(file);
        String normalizedLabel = normalizeLabel(label);
        boolean shouldBeDefault = makeDefault
                || signatureRepository.findByUserIdAndOrganizationIdAndIsDefaultTrue(user.getId(), user.getOrganizationId())
                        .isEmpty();

        if (shouldBeDefault) {
            unsetCurrentDefault(user);
        }

        billingService.assertStorageCapacity(user.getOrganization(), file.getSize());
        String path = fileStorageService.storeFile(file, user.getOrganizationId(), "signatures");
        Signature signature = Signature.builder()
                .organization(user.getOrganization())
                .user(user)
                .label(normalizedLabel)
                .imageUrl(path)
                .isDefault(shouldBeDefault)
                .build();

        Signature saved = signatureRepository.save(signature);
        auditService.logAction(user, "signature_uploaded", "Uploaded signature " + saved.getId());
        return mapToResponse(saved);
    }

    @Transactional
    public SignatureResponse updateSignature(User user, String signatureId, UpdateSignatureRequest request) {
        Signature signature = signatureRepository.findByIdAndUserIdAndOrganizationId(
                        signatureId,
                        user.getId(),
                        user.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Signature not found"));

        if (request.getLabel() != null) {
            signature.setLabel(normalizeLabel(request.getLabel()));
        }
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            unsetCurrentDefault(user);
            signature.setIsDefault(true);
        }
        if (Boolean.FALSE.equals(request.getIsDefault()) && Boolean.TRUE.equals(signature.getIsDefault())) {
            throw new IllegalArgumentException("A default signature cannot be unset directly. Select another default signature instead.");
        }

        Signature saved = signatureRepository.save(signature);
        auditService.logAction(user, "signature_updated", "Updated signature " + saved.getId());
        return mapToResponse(saved);
    }

    @Transactional
    public void deleteSignature(User user, String signatureId) {
        Signature signature = signatureRepository.findByIdAndUserIdAndOrganizationId(
                        signatureId,
                        user.getId(),
                        user.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Signature not found"));
        boolean wasDefault = Boolean.TRUE.equals(signature.getIsDefault());
        signatureRepository.delete(signature);
        if (wasDefault) {
            promoteMostRecentSignatureToDefault(user, signature.getId());
        }
        auditService.logAction(user, "signature_deleted", "Deleted signature " + signature.getId());
    }

    @Transactional(readOnly = true)
    public Signature requireOwnedSignature(User user, String signatureId) {
        return signatureRepository.findByIdAndUserIdAndOrganizationId(signatureId, user.getId(), user.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Signature not found"));
    }

    private void requireSignaturePermission(User user) {
        if (!Boolean.TRUE.equals(user.getCanUploadSignature())) {
            throw new AccessDeniedException("You are not authorized to upload signatures");
        }
    }

    private void unsetCurrentDefault(User user) {
        signatureRepository.findByUserIdAndOrganizationIdAndIsDefaultTrue(user.getId(), user.getOrganizationId())
                .ifPresent(existing -> {
                    existing.setIsDefault(false);
                    signatureRepository.save(existing);
                });
    }

    private void promoteMostRecentSignatureToDefault(User user, String deletedSignatureId) {
        signatureRepository.findByUserIdAndOrganizationIdAndIdNotOrderByCreatedAtDesc(
                        user.getId(),
                        user.getOrganizationId(),
                        deletedSignatureId)
                .stream()
                .findFirst()
                .ifPresent(replacement -> {
                    replacement.setIsDefault(true);
                    signatureRepository.save(replacement);
                });
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Signature file is required");
        }
        if (file.getSize() > MAX_SIGNATURE_SIZE_BYTES) {
            throw new IllegalArgumentException("Signature file must not exceed 2MB");
        }
        fileValidationService.requireAllowedContentTypeWithDeclaredFallback(
                file,
                java.util.Set.of("image/png", "image/jpeg"),
                "Signature must be PNG or JPG");
    }

    private String normalizeLabel(String label) {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Signature label is required");
        }
        String normalized = label.trim();
        if (normalized.length() < 2 || normalized.length() > 100) {
            throw new IllegalArgumentException("Signature label must be between 2 and 100 characters");
        }
        return normalized;
    }

    private SignatureResponse mapToResponse(Signature signature) {
        return SignatureResponse.builder()
                .id(signature.getId())
                .userId(signature.getUser().getId())
                .label(signature.getLabel())
                .imageUrl(signature.getImageUrl())
                .isDefault(signature.getIsDefault())
                .createdAt(signature.getCreatedAt())
                .updatedAt(signature.getUpdatedAt())
                .build();
    }
}
