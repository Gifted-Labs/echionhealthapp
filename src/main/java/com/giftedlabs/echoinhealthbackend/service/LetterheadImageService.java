package com.giftedlabs.echoinhealthbackend.service;

import com.giftedlabs.echoinhealthbackend.entity.Organization;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

/**
 * Resolves an organization's uploaded letterhead into raster bytes that the PDF and DOCX
 * exporters can actually embed (UR-045).
 *
 * <p>Both exporters previously rendered only the literal string "Custom Letterhead" where the
 * hospital's logo was supposed to be — the uploaded image was stored, validated and never
 * fetched. Exported reports therefore carried a text placeholder rather than branding.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LetterheadImageService {

    /** Raster formats PDFBox and Apache POI can both embed directly. */
    private static final String PNG = "png";
    private static final String JPG = "jpg";
    private static final String JPEG = "jpeg";

    private final FileStorageService fileStorageService;

    public record Letterhead(byte[] data, String format) {
    }

    /**
     * Returns the embeddable letterhead, or empty when the organization has none, when it is
     * an SVG (neither exporter can rasterise vector art), or when the object cannot be read.
     * Callers fall back to the text-only branding header in every empty case, so a broken or
     * unsupported letterhead degrades the export rather than failing it.
     */
    public Optional<Letterhead> resolve(Organization organization) {
        if (organization == null) {
            return Optional.empty();
        }
        String path = organization.getLetterheadUrl();
        if (path == null || path.isBlank()) {
            return Optional.empty();
        }

        String format = formatOf(path);
        if (format == null) {
            log.debug("Letterhead {} is not an embeddable raster format; using text branding",
                    path);
            return Optional.empty();
        }

        try {
            byte[] data = fileStorageService.downloadFile(path);
            if (data == null || data.length == 0) {
                return Optional.empty();
            }
            return Optional.of(new Letterhead(data, format));
        } catch (Exception e) {
            log.warn("Could not read letterhead {} for organization {}; using text branding",
                    path, organization.getId(), e);
            return Optional.empty();
        }
    }

    private String formatOf(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return PNG;
        }
        if (lower.endsWith(".jpg")) {
            return JPG;
        }
        if (lower.endsWith(".jpeg")) {
            return JPEG;
        }
        return null;
    }
}
