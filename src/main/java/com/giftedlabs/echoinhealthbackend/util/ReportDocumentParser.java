package com.giftedlabs.echoinhealthbackend.util;

import com.giftedlabs.echoinhealthbackend.entity.Gender;
import com.giftedlabs.echoinhealthbackend.entity.ReportType;
import com.giftedlabs.echoinhealthbackend.entity.ScanType;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for extracting structured data from medical report text.
 * Parses reports in the standard ultrasound report format with sections like:
 * - Patient Information
 * - Clinical History
 * - Findings
 * - Impression
 * - Recommendation
 */
@Component
@Slf4j
public class ReportDocumentParser {

    // Section header patterns (case insensitive)
    private static final Pattern TITLE_PATTERN = Pattern.compile(
            "ULTRASOUND\\s+SCAN\\s*[-–]?\\s*(.+?)\\s*REPORT",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern REPORT_TYPE_PATTERN = Pattern.compile(
            "\\(\\s*(DIAGNOSTIC|NORMAL[_ ]ULTRASOUND[_ ]SCAN|PATHOLOGY[_ ]SCAN|NORMAL|PATHOLOGY)\\s*\\)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PATIENT_NAME_PATTERN = Pattern.compile(
            "Patient\\s*Name\\s*[:\\-]?\\s*(.+?)(?=\\n|Patient\\s*ID|$)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PATIENT_ID_PATTERN = Pattern.compile(
            "Patient\\s*ID\\s*[:\\-]?\\s*(.+?)(?=\\n|Age|$)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern AGE_PATTERN = Pattern.compile(
            "Age\\s*[:\\-]?\\s*(\\d+)\\s*(?:years?|yrs?)?",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SEX_PATTERN = Pattern.compile(
            "Sex\\s*[:\\-]?\\s*(MALE|FEMALE|M|F)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SCAN_DATE_PATTERN = Pattern.compile(
            "Date\\s*(?:of)?\\s*Scan\\s*[:\\-]?\\s*(.+?)(?=\\n|$)",
            Pattern.CASE_INSENSITIVE);

    // Section content patterns
    private static final Pattern CLINICAL_HISTORY_PATTERN = Pattern.compile(
            "CLINICAL\\s*HISTORY\\s*[:\\-]?\\s*(.+?)(?=FINDINGS|IMPRESSION|RECOMMENDATION|$)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern FINDINGS_PATTERN = Pattern.compile(
            "FINDINGS\\s*[:\\-]?\\s*(.+?)(?=IMPRESSION|RECOMMENDATION|$)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern IMPRESSION_PATTERN = Pattern.compile(
            "IMPRESSION\\s*[:\\-]?\\s*(.+?)(?=RECOMMENDATION|$)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern RECOMMENDATION_PATTERN = Pattern.compile(
            "RECOMMENDATION[S]?\\s*[:\\-]?\\s*(.+?)$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final DateTimeFormatter[] DATE_FORMATTERS = {
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)
    };

    /**
     * Parse extracted text into structured report data
     */
    public ParsedReportData parse(String extractedText) {
        if (extractedText == null || extractedText.isBlank()) {
            log.warn("Empty or null text provided for parsing");
            return ParsedReportData.builder().build();
        }

        log.debug("Parsing extracted text of length: {}", extractedText.length());

        ParsedReportData.ParsedReportDataBuilder builder = ParsedReportData.builder();

        // Extract scan type from title
        Matcher titleMatcher = TITLE_PATTERN.matcher(extractedText);
        if (titleMatcher.find()) {
            String scanTypeStr = titleMatcher.group(1).trim();
            builder.scanType(parseScanType(scanTypeStr));
            log.debug("Extracted scan type: {}", scanTypeStr);
        }

        // Extract report type
        Matcher reportTypeMatcher = REPORT_TYPE_PATTERN.matcher(extractedText);
        if (reportTypeMatcher.find()) {
            String reportTypeStr = reportTypeMatcher.group(1).trim();
            builder.reportType(parseReportType(reportTypeStr));
            log.debug("Extracted report type: {}", reportTypeStr);
        }

        // Extract patient information
        Matcher nameMatcher = PATIENT_NAME_PATTERN.matcher(extractedText);
        if (nameMatcher.find()) {
            builder.patientName(nameMatcher.group(1).trim());
        }

        Matcher idMatcher = PATIENT_ID_PATTERN.matcher(extractedText);
        if (idMatcher.find()) {
            builder.patientId(idMatcher.group(1).trim());
        }

        Matcher ageMatcher = AGE_PATTERN.matcher(extractedText);
        if (ageMatcher.find()) {
            try {
                builder.patientAge(Integer.parseInt(ageMatcher.group(1)));
            } catch (NumberFormatException e) {
                log.debug("Could not parse age: {}", ageMatcher.group(1));
            }
        }

        Matcher sexMatcher = SEX_PATTERN.matcher(extractedText);
        if (sexMatcher.find()) {
            builder.patientSex(parseGender(sexMatcher.group(1).trim()));
        }

        Matcher dateMatcher = SCAN_DATE_PATTERN.matcher(extractedText);
        if (dateMatcher.find()) {
            builder.scanDate(parseDate(dateMatcher.group(1).trim()));
        }

        // Extract report sections
        Matcher historyMatcher = CLINICAL_HISTORY_PATTERN.matcher(extractedText);
        if (historyMatcher.find()) {
            builder.clinicalHistory(cleanText(historyMatcher.group(1)));
        }

        Matcher findingsMatcher = FINDINGS_PATTERN.matcher(extractedText);
        if (findingsMatcher.find()) {
            builder.findings(cleanText(findingsMatcher.group(1)));
        }

        Matcher impressionMatcher = IMPRESSION_PATTERN.matcher(extractedText);
        if (impressionMatcher.find()) {
            builder.impression(cleanText(impressionMatcher.group(1)));
        }

        Matcher recommendationMatcher = RECOMMENDATION_PATTERN.matcher(extractedText);
        if (recommendationMatcher.find()) {
            builder.recommendation(cleanText(recommendationMatcher.group(1)));
        }

        ParsedReportData result = builder.build();
        log.info("Parsed report - ScanType: {}, ReportType: {}",
                result.getScanType(), result.getReportType());

        return result;
    }

    private ScanType parseScanType(String scanTypeStr) {
        if (scanTypeStr == null)
            return null;

        String normalized = scanTypeStr.toUpperCase()
                .replace(" ", "_")
                .replace("-", "_");

        // Try direct match first
        for (ScanType type : ScanType.values()) {
            if (type.name().equalsIgnoreCase(normalized) ||
                    type.name().replace("_", " ").equalsIgnoreCase(scanTypeStr)) {
                return type;
            }
        }

        // Try partial matches for common scan types
        String lower = scanTypeStr.toLowerCase();
        if (lower.contains("abdomen pelvis male"))
            return ScanType.ABDOMEN_PELVIS_MALE;
        if (lower.contains("abdomen pelvis female"))
            return ScanType.ABDOMEN_PELVIS_FEMALE;
        if (lower.contains("abdomen"))
            return ScanType.ABDOMINAL;
        if (lower.contains("pelvis male") || lower.contains("pelvic male"))
            return ScanType.PELVIC_MALE;
        if (lower.contains("pelvis female") || lower.contains("pelvic female"))
            return ScanType.PELVIC_FEMALE;
        if (lower.contains("pelvis") || lower.contains("pelvic"))
            return ScanType.TRANSABDOMINAL_PELVIC;
        if (lower.contains("obstetric twins") || lower.contains("twins"))
            return ScanType.OBSTETRIC_TWINS;
        if (lower.contains("obstetric") || lower.contains("pregnan"))
            return ScanType.OBSTETRIC_LATE;
        if (lower.contains("anomaly"))
            return ScanType.ANOMALY;
        if (lower.contains("biophysical"))
            return ScanType.BIOPHYSICAL_PROFILE;
        if (lower.contains("transvag"))
            return ScanType.TRANSVAGINAL;
        if (lower.contains("thyroid"))
            return ScanType.THYROID;
        if (lower.contains("breast"))
            return ScanType.BREAST;
        if (lower.contains("renal") || lower.contains("kidney"))
            return ScanType.ABDOMINAL;
        if (lower.contains("cardiac") || lower.contains("heart") || lower.contains("echo"))
            return ScanType.ECHO_ADULT;
        if (lower.contains("arterial doppler both lower"))
            return ScanType.ARTERIAL_DOPPLER_BOTH_LOWER;
        if (lower.contains("arterial doppler left lower"))
            return ScanType.ARTERIAL_DOPPLER_LEFT_LOWER;
        if (lower.contains("arterial doppler right lower"))
            return ScanType.ARTERIAL_DOPPLER_RIGHT_LOWER;
        if (lower.contains("arterial doppler left upper"))
            return ScanType.ARTERIAL_DOPPLER_LEFT_UPPER;
        if (lower.contains("arterial doppler right upper"))
            return ScanType.ARTERIAL_DOPPLER_RIGHT_UPPER;
        if (lower.contains("venous doppler both lower"))
            return ScanType.VENOUS_DOPPLER_BOTH_LOWER;
        if (lower.contains("venous doppler left lower"))
            return ScanType.VENOUS_DOPPLER_LEFT_LOWER;
        if (lower.contains("venous doppler right lower"))
            return ScanType.VENOUS_DOPPLER_RIGHT_LOWER;
        if (lower.contains("venous doppler left upper"))
            return ScanType.VENOUS_DOPPLER_LEFT_UPPER;
        if (lower.contains("venous doppler right upper"))
            return ScanType.VENOUS_DOPPLER_RIGHT_UPPER;
        if (lower.contains("vascular") || lower.contains("doppler"))
            return ScanType.VENOUS_DOPPLER_BOTH_LOWER;
        if (lower.contains("musculoskeletal"))
            return ScanType.MUSCULOSKELETAL;
        if (lower.contains("hepatobiliary") || lower.contains("liver") || lower.contains("gallbladder"))
            return ScanType.ABDOMINAL;
        if (lower.contains("gynecological") || lower.contains("gynae"))
            return ScanType.TRANSABDOMINAL_PELVIC;
        if (lower.contains("neck"))
            return ScanType.NECK;
        if (lower.contains("chest"))
            return ScanType.CHEST;
        if (lower.contains("scrot"))
            return ScanType.SCROTAL;
        if (lower.contains("penile"))
            return ScanType.PENILE;
        if (lower.contains("neonatal head") || lower.contains("cranial"))
            return ScanType.NEONATAL_HEAD;

        return ScanType.GENERAL;
    }

    private ReportType parseReportType(String reportTypeStr) {
        if (reportTypeStr == null)
            return null;

        String upper = reportTypeStr.toUpperCase();
        if (upper.contains("DIAGNOSTIC"))
            return ReportType.DIAGNOSTIC;
        if (upper.contains("PATHOLOGY"))
            return ReportType.PATHOLOGY;
        if (upper.contains("NORMAL"))
            return ReportType.NORMAL;

        return ReportType.DIAGNOSTIC; // Default
    }

    private Gender parseGender(String genderStr) {
        if (genderStr == null)
            return null;

        String upper = genderStr.toUpperCase();
        if (upper.equals("M") || upper.equals("MALE"))
            return Gender.MALE;
        if (upper.equals("F") || upper.equals("FEMALE"))
            return Gender.FEMALE;

        return null;
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank())
            return null;

        String cleanDate = dateStr.trim();

        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(cleanDate, formatter);
            } catch (DateTimeParseException e) {
                // Try next formatter
            }
        }

        log.debug("Could not parse date: {}", dateStr);
        return null;
    }

    private String cleanText(String text) {
        if (text == null)
            return null;

        return text.trim()
                .replaceAll("\\r\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    /**
     * Data class to hold parsed report information
     */
    @Data
    @Builder
    public static class ParsedReportData {
        private String patientName;
        private String patientId;
        private Integer patientAge;
        private Gender patientSex;
        private LocalDate scanDate;
        private ScanType scanType;
        private ReportType reportType;
        private String clinicalHistory;
        private String findings;
        private String impression;
        private String recommendation;

        /**
         * Check if any meaningful data was extracted
         */
        public boolean hasData() {
            return patientName != null ||
                    findings != null ||
                    impression != null ||
                    clinicalHistory != null;
        }
    }
}
