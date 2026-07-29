package com.giftedlabs.echoinhealthbackend.service;

import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

@Service
public class FileValidationService {

    private final Tika tika = new Tika();

    public String requireAllowedContentType(MultipartFile file, Set<String> allowedContentTypes, String message) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required");
        }

        try (InputStream inputStream = file.getInputStream()) {
            String detected = tika.detect(inputStream, file.getOriginalFilename());
            if (!allowedContentTypes.contains(detected)) {
                throw new IllegalArgumentException(message);
            }
            return detected;
        } catch (IOException ex) {
            throw new IllegalArgumentException("Could not inspect uploaded file", ex);
        }
    }

    public String requireAllowedContentTypeWithDeclaredFallback(
            MultipartFile file,
            Set<String> allowedContentTypes,
            String message) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required");
        }

        try (InputStream inputStream = file.getInputStream()) {
            String detected = tika.detect(inputStream, file.getOriginalFilename());
            if (allowedContentTypes.contains(detected)) {
                return detected;
            }

            String declared = file.getContentType();
            if (isGenericDetection(detected) && declared != null && allowedContentTypes.contains(declared)) {
                return declared;
            }

            throw new IllegalArgumentException(message);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Could not inspect uploaded file", ex);
        }
    }

    private boolean isGenericDetection(String detected) {
        return "application/octet-stream".equals(detected)
                || "text/plain".equals(detected);
    }
}
