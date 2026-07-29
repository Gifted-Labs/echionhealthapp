package com.giftedlabs.echoinhealthbackend.service;

import com.giftedlabs.echoinhealthbackend.entity.Report;
import com.giftedlabs.echoinhealthbackend.entity.Signature;
import com.giftedlabs.echoinhealthbackend.repository.SignatureRepository;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportSignatureResolver {

    private final SignatureRepository signatureRepository;
    private final FileStorageService fileStorageService;

    public Optional<ResolvedSignature> resolve(Report report) {
        if (report.getAppliedSignatureId() == null || report.getAppliedSignatureId().isBlank()
                || report.getOrganization() == null) {
            return Optional.empty();
        }

        return signatureRepository.findByIdAndOrganizationId(report.getAppliedSignatureId(), report.getOrganization().getId())
                .map(signature -> ResolvedSignature.builder()
                        .label(signature.getLabel())
                        .imageBytes(readImageBytes(signature))
                        .imagePath(signature.getImageUrl())
                        .build());
    }

    private byte[] readImageBytes(Signature signature) {
        try {
            return fileStorageService.downloadFile(signature.getImageUrl());
        } catch (IOException ex) {
            log.warn("Failed to load signature image {}", signature.getId(), ex);
            return null;
        }
    }

    @Value
    @Builder
    public static class ResolvedSignature {
        String label;
        byte[] imageBytes;
        String imagePath;
    }
}
