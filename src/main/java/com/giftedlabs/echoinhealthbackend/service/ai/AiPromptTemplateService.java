package com.giftedlabs.echoinhealthbackend.service.ai;

import com.giftedlabs.echoinhealthbackend.dto.vault.ScanFieldDefinitionResponse;
import com.giftedlabs.echoinhealthbackend.dto.vault.ScanTypeDefinitionResponse;
import com.giftedlabs.echoinhealthbackend.entity.ScanType;
import com.giftedlabs.echoinhealthbackend.service.ScanTypeDefinitionService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds scan-type-aware prompts (UR-023).
 *
 * <p>Resolution is three-tier: an exact per-scan-type template, then a template for the scan
 * type's clinical category, then the generic default. On top of that, the scan type's own
 * section structure, measurement columns and recommendation options are injected into the
 * prompt — so all 32 scan types get their real organ/structure breakdown without needing 32
 * hand-maintained files that would drift from {@link ScanTypeDefinitionService}.
 */
@Service
public class AiPromptTemplateService {

    private static final String PROMPT_VERSION = "v2";

    private final ResourceLoader resourceLoader;
    private final ScanTypeDefinitionService scanTypeDefinitionService;
    private final Map<String, String> templateCache = new ConcurrentHashMap<>();

    public AiPromptTemplateService(ResourceLoader resourceLoader,
                                   ScanTypeDefinitionService scanTypeDefinitionService) {
        this.resourceLoader = resourceLoader;
        this.scanTypeDefinitionService = scanTypeDefinitionService;
    }

    public String promptVersion() {
        return PROMPT_VERSION;
    }

    public String reportPrompt(ScanType scanType, String rawNotes, Map<String, Object> measurements) {
        ScanTypeDefinitionResponse definition = definitionFor(scanType);
        return loadTemplate("generate-report", scanType, definition)
                .replace("{{SCAN_TYPE}}", displayName(scanType, definition))
                .replace("{{SCAN_CATEGORY}}", definition != null ? definition.getCategory() : "General")
                .replace("{{REQUIRED_SECTIONS}}", formatSections(definition))
                .replace("{{MEASUREMENT_GUIDANCE}}", formatMeasurementGuidance(definition))
                .replace("{{RECOMMENDATION_OPTIONS}}", formatRecommendationOptions(definition))
                .replace("{{RAW_NOTES}}", rawNotes)
                .replace("{{MEASUREMENTS}}", formatMeasurements(measurements));
    }

    public String impressionPrompt(ScanType scanType, String findings) {
        ScanTypeDefinitionResponse definition = definitionFor(scanType);
        return loadTemplate("suggest-impression", scanType, definition)
                .replace("{{SCAN_TYPE}}", displayName(scanType, definition))
                .replace("{{SCAN_CATEGORY}}", definition != null ? definition.getCategory() : "General")
                .replace("{{RECOMMENDATION_OPTIONS}}", formatRecommendationOptions(definition))
                .replace("{{FINDINGS}}", findings);
    }

    public String grammarPrompt(String text) {
        return loadTemplate("grammar-check", null, null).replace("{{TEXT}}", text);
    }

    private ScanTypeDefinitionResponse definitionFor(ScanType scanType) {
        return scanType == null ? null : scanTypeDefinitionService.getDefinition(scanType);
    }

    private String displayName(ScanType scanType, ScanTypeDefinitionResponse definition) {
        if (definition != null && definition.getDisplayName() != null) {
            return definition.getDisplayName();
        }
        return scanType != null ? scanType.name() : "General";
    }

    private String loadTemplate(String task, ScanType scanType, ScanTypeDefinitionResponse definition) {
        String cacheKey = task + '/' + (scanType != null ? scanType.name() : "GENERAL");
        return templateCache.computeIfAbsent(cacheKey, key -> resolveTemplate(task, scanType, definition));
    }

    private String resolveTemplate(String task, ScanType scanType, ScanTypeDefinitionResponse definition) {
        List<String> candidates = new ArrayList<>();
        if (scanType != null) {
            candidates.add(path(task, scanType.name()));
        }
        if (definition != null && definition.getCategory() != null) {
            candidates.add(path(task, slug(definition.getCategory())));
        }
        candidates.add(path(task, "default"));

        for (String candidate : candidates) {
            Resource resource = resourceLoader.getResource(candidate);
            if (resource.exists()) {
                try {
                    return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to load prompt template: " + candidate, e);
                }
            }
        }
        throw new IllegalStateException("No prompt template found for task " + task);
    }

    private String path(String task, String name) {
        return "classpath:prompts/" + task + "/" + PROMPT_VERSION + "/"
                + name.toLowerCase(Locale.ROOT) + ".txt";
    }

    private String slug(String category) {
        return category.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private String formatSections(ScanTypeDefinitionResponse definition) {
        if (definition == null || definition.getSections() == null || definition.getSections().isEmpty()) {
            return "- Use clinically appropriate sections for this study.";
        }
        StringBuilder builder = new StringBuilder();
        for (ScanFieldDefinitionResponse section : definition.getSections()) {
            builder.append("- ").append(section.getLabel());
            if (Boolean.TRUE.equals(section.getAllowsMeasurements())
                    && section.getMeasurementLabels() != null
                    && !section.getMeasurementLabels().isEmpty()) {
                builder.append(" (report measurements where available: ")
                        .append(String.join(", ", section.getMeasurementLabels()))
                        .append(')');
            }
            builder.append('\n');
        }
        return builder.toString().trim();
    }

    private String formatMeasurementGuidance(ScanTypeDefinitionResponse definition) {
        if (definition == null || !Boolean.TRUE.equals(definition.getHasMeasurementTable())) {
            return "Report measurements inline within the relevant section.";
        }
        return "This study uses a measurement table with columns: "
                + String.join(", ", definition.getMeasurementColumns())
                + ". Present each measured segment as its own structuredFindings entry, with the "
                + "segment name as 'section' and the measured values plus interpretation as 'details'.";
    }

    private String formatRecommendationOptions(ScanTypeDefinitionResponse definition) {
        if (definition == null || definition.getRecommendationOptions() == null
                || definition.getRecommendationOptions().isEmpty()) {
            return "- Clinical correlation advised";
        }
        return definition.getRecommendationOptions().stream()
                .map(option -> "- " + option)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("- Clinical correlation advised");
    }

    private String formatMeasurements(Map<String, Object> measurements) {
        if (measurements == null || measurements.isEmpty()) {
            return "None provided.";
        }
        StringBuilder builder = new StringBuilder();
        measurements.forEach((key, value) -> builder.append("- ").append(key).append(": ").append(value).append('\n'));
        return builder.toString().trim();
    }
}
