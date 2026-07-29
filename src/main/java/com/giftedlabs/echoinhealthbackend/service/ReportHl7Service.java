package com.giftedlabs.echoinhealthbackend.service;

import com.giftedlabs.echoinhealthbackend.entity.Report;
import com.giftedlabs.echoinhealthbackend.entity.Organization;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Service for generating HL7 v2.x ORU^R01 (Observation Result) messages
 * from Report entities (UR-044).
 * 
 * Produces pipe-delimited HL7 v2.5 messages with:
 * - MSH: Message Header
 * - PID: Patient Identification
 * - OBR: Observation Request
 * - OBX: Observation Result segments (findings, impression, recommendation)
 */
@Service
@Slf4j
public class ReportHl7Service {

    private static final String HL7_FIELD_SEP = "|";
    private static final String HL7_ENCODING_CHARS = "^~\\&";
    private static final String HL7_VERSION = "2.5";
    private static final DateTimeFormatter HL7_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter HL7_DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * Generate an HL7 v2.x ORU^R01 message from a Report entity.
     *
     * @param report The report to convert
     * @return HL7 message as a string
     */
    public String generateHl7(Report report) {
        StringBuilder hl7 = new StringBuilder();
        Organization org = report.getOrganization();
        String sendingFacility = org != null && org.getHospitalName() != null
                ? sanitize(org.getHospitalName())
                : "ECHION_HEALTH";
        String messageId = report.getId() != null ? report.getId().replace("-", "") : "UNKNOWN";
        String timestamp = LocalDateTime.now().format(HL7_DATETIME_FORMAT);

        // MSH - Message Header
        hl7.append("MSH").append(HL7_FIELD_SEP).append(HL7_ENCODING_CHARS)
                .append(HL7_FIELD_SEP).append("ECHION_HEALTH")          // Sending Application
                .append(HL7_FIELD_SEP).append(sendingFacility)           // Sending Facility
                .append(HL7_FIELD_SEP).append("")                        // Receiving Application
                .append(HL7_FIELD_SEP).append("")                        // Receiving Facility
                .append(HL7_FIELD_SEP).append(timestamp)                 // Date/Time of Message
                .append(HL7_FIELD_SEP).append("")                        // Security
                .append(HL7_FIELD_SEP).append("ORU^R01^ORU_R01")        // Message Type
                .append(HL7_FIELD_SEP).append(messageId)                 // Message Control ID
                .append(HL7_FIELD_SEP).append("P")                       // Processing ID (P=Production)
                .append(HL7_FIELD_SEP).append(HL7_VERSION)               // Version ID
                .append("\r");

        // PID - Patient Identification
        hl7.append("PID")
                .append(HL7_FIELD_SEP).append("1")                      // Set ID
                .append(HL7_FIELD_SEP).append("")                        // Patient ID (External)
                .append(HL7_FIELD_SEP).append(safe(report.getPatientId())) // Patient ID (Internal)
                .append(HL7_FIELD_SEP).append("")                        // Alternate Patient ID
                .append(HL7_FIELD_SEP).append(formatPatientName(report.getPatientName())) // Patient Name
                .append(HL7_FIELD_SEP).append("")                        // Mother's Maiden Name
                .append(HL7_FIELD_SEP).append("")                        // Date of Birth
                .append(HL7_FIELD_SEP).append(formatSex(report.getPatientSex())) // Sex
                .append("\r");

        // OBR - Observation Request
        String scanDate = report.getScanDate() != null
                ? report.getScanDate().format(HL7_DATE_FORMAT)
                : "";
        String scanType = report.getScanType() != null
                ? sanitize(report.getScanType().name())
                : "ULTRASOUND";
        String reportType = report.getReportType() != null
                ? sanitize(report.getReportType().name())
                : "";

        hl7.append("OBR")
                .append(HL7_FIELD_SEP).append("1")                      // Set ID
                .append(HL7_FIELD_SEP).append(messageId)                 // Placer Order Number
                .append(HL7_FIELD_SEP).append(messageId)                 // Filler Order Number
                .append(HL7_FIELD_SEP).append(scanType + "^" + reportType + "^L") // Universal Service ID
                .append(HL7_FIELD_SEP).append("")                        // Priority
                .append(HL7_FIELD_SEP).append("")                        // Requested Date/Time
                .append(HL7_FIELD_SEP).append(scanDate)                  // Observation Date/Time
                .append(HL7_FIELD_SEP).append("")                        // Observation End Date/Time
                .append(HL7_FIELD_SEP).append("")                        // Collection Volume
                .append(HL7_FIELD_SEP).append("")                        // Collector Identifier
                .append(HL7_FIELD_SEP).append("")                        // Specimen Action Code
                .append(HL7_FIELD_SEP).append("")                        // Danger Code
                .append(HL7_FIELD_SEP).append("")                        // Relevant Clinical Info
                .append(HL7_FIELD_SEP).append("")                        // Specimen Received Date/Time
                .append(HL7_FIELD_SEP).append("")                        // Specimen Source
                .append(HL7_FIELD_SEP).append("")                        // Ordering Provider
                .append(HL7_FIELD_SEP).append("")                        // Order Callback Phone
                .append(HL7_FIELD_SEP).append("")                        // Placer Field 1
                .append(HL7_FIELD_SEP).append("")                        // Placer Field 2
                .append(HL7_FIELD_SEP).append("")                        // Filler Field 1
                .append(HL7_FIELD_SEP).append("")                        // Filler Field 2
                .append(HL7_FIELD_SEP).append("")                        // Results Rpt/Status Chng Date/Time
                .append(HL7_FIELD_SEP).append("")                        // Charge to Practice
                .append(HL7_FIELD_SEP).append("")                        // Diagnostic Serv Sect ID
                .append(HL7_FIELD_SEP).append(report.getStatus() != null && report.getStatus().equals("FINALIZED") ? "F" : "P") // Result Status (F=Final, P=Preliminary)
                .append("\r");

        // OBX segments - Observation Results
        int obxSetId = 1;

        // Clinical History
        if (report.getClinicalHistory() != null && !report.getClinicalHistory().isBlank()) {
            hl7.append(buildObxSegment(obxSetId++, "CLINICAL_HISTORY", "Clinical History", report.getClinicalHistory()));
        }

        // Findings
        if (report.getFindings() != null && !report.getFindings().isBlank()) {
            hl7.append(buildObxSegment(obxSetId++, "FINDINGS", "Findings", report.getFindings()));
        }

        // Impression
        if (report.getImpression() != null && !report.getImpression().isBlank()) {
            hl7.append(buildObxSegment(obxSetId++, "IMPRESSION", "Impression", report.getImpression()));
        }

        // Recommendation
        if (report.getRecommendation() != null && !report.getRecommendation().isBlank()) {
            hl7.append(buildObxSegment(obxSetId++, "RECOMMENDATION", "Recommendation", report.getRecommendation()));
        }

        // Signatory
        if (report.getSignatoryName() != null && !report.getSignatoryName().isBlank()) {
            String signatoryInfo = report.getSignatoryName();
            if (report.getSignatoryDesignation() != null) {
                signatoryInfo += " (" + report.getSignatoryDesignation().name().replace("_", " ") + ")";
            }
            hl7.append(buildObxSegment(obxSetId++, "SIGNATORY", "Signed By", signatoryInfo));
        }

        log.info("Generated HL7 ORU^R01 message for report {}", report.getId());
        return hl7.toString();
    }

    /**
     * Build an OBX (Observation Result) segment.
     */
    private String buildObxSegment(int setId, String observationId, String observationText, String value) {
        // Replace HL7 special characters and newlines in value
        String safeValue = sanitize(value);
        String resultStatus = "F"; // Final

        return "OBX" + HL7_FIELD_SEP + setId                            // Set ID
                + HL7_FIELD_SEP + "TX"                                    // Value Type (TX = Text)
                + HL7_FIELD_SEP + observationId + "^" + observationText + "^L" // Observation Identifier
                + HL7_FIELD_SEP + ""                                      // Observation Sub-ID
                + HL7_FIELD_SEP + safeValue                               // Observation Value
                + HL7_FIELD_SEP + ""                                      // Units
                + HL7_FIELD_SEP + ""                                      // Reference Range
                + HL7_FIELD_SEP + ""                                      // Abnormal Flags
                + HL7_FIELD_SEP + ""                                      // Probability
                + HL7_FIELD_SEP + ""                                      // Nature of Abnormal Test
                + HL7_FIELD_SEP + resultStatus                            // Observation Result Status
                + "\r";
    }

    /**
     * Format patient name for HL7 PID segment (Last^First format).
     */
    private String formatPatientName(String patientName) {
        if (patientName == null || patientName.isBlank()) {
            return "";
        }
        String[] parts = patientName.trim().split("\\s+", 2);
        if (parts.length == 1) {
            return sanitize(parts[0]);
        }
        // HL7 format: LastName^FirstName
        return sanitize(parts[parts.length - 1]) + "^" + sanitize(parts[0]);
    }

    /**
     * Format gender/sex for HL7 PID-8.
     */
    private String formatSex(com.giftedlabs.echoinhealthbackend.entity.Gender sex) {
        if (sex == null) {
            return "U"; // Unknown
        }
        return switch (sex) {
            case MALE -> "M";
            case FEMALE -> "F";
            default -> "U";
        };
    }

    /**
     * Sanitize a string for HL7 — remove pipe and caret characters, replace newlines.
     */
    private String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("|", " ")
                .replace("^", " ")
                .replace("~", " ")
                .replace("\\", " ")
                .replace("&", " ")
                .replace("\r\n", "~")
                .replace("\n", "~")
                .replace("\r", "~");
    }

    /**
     * Safe getter — returns empty string if null.
     */
    private String safe(String value) {
        return value != null ? sanitize(value) : "";
    }
}
