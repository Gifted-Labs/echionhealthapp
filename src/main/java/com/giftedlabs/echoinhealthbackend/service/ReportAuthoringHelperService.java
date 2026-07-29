package com.giftedlabs.echoinhealthbackend.service;

import com.giftedlabs.echoinhealthbackend.dto.vault.HelperToolsResponse;
import com.giftedlabs.echoinhealthbackend.entity.ScanType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class ReportAuthoringHelperService {

    public HelperToolsResponse buildHelpers(ScanType scanType, String findings) {
        String normalized = findings.toLowerCase(Locale.ROOT);

        List<String> differentials = new ArrayList<>();
        if (normalized.contains("mass") || normalized.contains("lesion")) {
            differentials.add("Consider benign versus malignant focal lesion correlation.");
        }
        if (normalized.contains("fluid")) {
            differentials.add("Correlate with inflammatory, physiologic, or hemorrhagic fluid collections.");
        }
        if (normalized.contains("enlarged") || normalized.contains("hepatomeg")) {
            differentials.add("Assess for acute inflammatory, congestive, or chronic infiltrative enlargement.");
        }
        if (scanType != null && scanType.isDoppler()) {
            differentials.add("Correlate waveform changes with stenosis, occlusion, or venous insufficiency.");
        }
        if (differentials.isEmpty()) {
            differentials.add("Correlate sonographic findings with clinical history and laboratory profile.");
        }

        List<String> wordingSuggestions = List.of(
                "Use measured, non-definitive language when findings are indeterminate.",
                "State normal structures explicitly before abnormal findings for readability.",
                "Separate observation from interpretation in the impression.");

        List<String> references = referencesFor(scanType);
        return HelperToolsResponse.builder()
                .impressionSuggestion(suggestImpression(scanType, findings))
                .clinicalDifferentials(differentials)
                .wordingSuggestions(wordingSuggestions)
                .verifiedReferences(references)
                .build();
    }

    public String suggestImpression(ScanType scanType, String findings) {
        String cleaned = findings == null ? "" : findings.trim();
        if (cleaned.isBlank()) {
            throw new IllegalArgumentException("Findings are required to suggest an impression");
        }
        String prefix = scanType != null ? scanType.name().replace('_', ' ') + ": " : "";
        if (cleaned.length() > 180) {
            cleaned = cleaned.substring(0, 180).trim() + "...";
        }
        return prefix + cleaned;
    }

    private List<String> referencesFor(ScanType scanType) {
        if (scanType != null && scanType.isDoppler()) {
            return List.of(
                    "Society for Vascular Ultrasound practice recommendations",
                    "AIUM peripheral vascular ultrasound performance guidelines");
        }
        if (scanType == ScanType.ECHO_ADULT || scanType == ScanType.ECHO_PEDIATRIC) {
            return List.of(
                    "ASE chamber quantification guideline",
                    "ASE valvular heart disease echocardiography guideline");
        }
        if (scanType == ScanType.OBSTETRIC_EARLY || scanType == ScanType.OBSTETRIC_LATE
                || scanType == ScanType.OBSTETRIC_TWINS || scanType == ScanType.ANOMALY
                || scanType == ScanType.BIOPHYSICAL_PROFILE) {
            return List.of(
                    "ISUOG practice guidelines for obstetric ultrasound",
                    "ACOG ultrasound in pregnancy recommendations");
        }
        return List.of(
                "AIUM practice parameter for diagnostic ultrasound examinations",
                "Local radiology reporting standard operating procedure");
    }
}
