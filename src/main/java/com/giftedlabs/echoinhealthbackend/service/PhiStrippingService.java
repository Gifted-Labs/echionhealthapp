package com.giftedlabs.echoinhealthbackend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Service for detecting and stripping Protected Health Information (PHI)
 * from template content (UR-049).
 *
 * Uses regex-based heuristics to identify and redact:
 * - Patient names (after "Patient:", "Name:", etc.)
 * - Dates of birth
 * - SSN / National ID patterns
 * - Phone numbers
 * - Email addresses
 * - Medical Record Numbers (MRN patterns)
 * - Address patterns
 *
 * The stripping is intentionally aggressive — it's better to over-redact
 * than to leak PHI into shared templates.
 */
@Service
@Slf4j
public class PhiStrippingService {

    private static final String REDACTED = "[REDACTED]";

    // Pattern: "Patient: John Doe" or "Patient Name: Jane Smith"
    private static final Pattern PATIENT_NAME_PATTERN = Pattern.compile(
            "(?i)(patient\\s*(?:name)?\\s*[:=]\\s*)([A-Z][a-z]+(?:\\s+[A-Z][a-z]+)+)",
            Pattern.MULTILINE);

    // Pattern: "Name: John Doe"
    private static final Pattern NAME_LABEL_PATTERN = Pattern.compile(
            "(?i)((?:full\\s+)?name\\s*[:=]\\s*)([A-Z][a-z]+(?:\\s+[A-Z][a-z]+)+)",
            Pattern.MULTILINE);

    // Pattern: DOB / Date of Birth
    private static final Pattern DOB_PATTERN = Pattern.compile(
            "(?i)((?:date\\s+of\\s+birth|DOB|D\\.O\\.B\\.?)\\s*[:=]?\\s*)(\\d{1,2}[/\\-.]\\d{1,2}[/\\-.]\\d{2,4})",
            Pattern.MULTILINE);

    // Pattern: SSN (###-##-####)
    private static final Pattern SSN_PATTERN = Pattern.compile(
            "\\b\\d{3}-\\d{2}-\\d{4}\\b");

    // Pattern: Phone numbers (various formats)
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "(?i)((?:phone|tel|telephone|mobile|cell)\\s*(?:number|no\\.?)?\\s*[:=]\\s*)" +
            "([+]?\\d[\\d\\s\\-().]{7,15}\\d)");

    // Pattern: Email addresses
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "(?i)((?:email|e-mail)\\s*(?:address)?\\s*[:=]\\s*)?([a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,})");

    // Pattern: MRN / Medical Record Number
    private static final Pattern MRN_PATTERN = Pattern.compile(
            "(?i)((?:MRN|medical\\s+record\\s*(?:number|no\\.?)?|patient\\s*ID|record\\s*(?:number|no\\.?))\\s*[:=]?\\s*)" +
            "([A-Z0-9\\-]{4,20})");

    // Pattern: Addresses (Street number + street name)
    private static final Pattern ADDRESS_PATTERN = Pattern.compile(
            "(?i)((?:address|street|residence)\\s*[:=]\\s*)(\\d+\\s+[A-Za-z]+(?:\\s+[A-Za-z]+)*(?:\\s+(?:St\\.?|Street|Ave\\.?|Avenue|Rd\\.?|Road|Blvd\\.?|Dr\\.?|Drive|Lane|Ln\\.?|Way|Ct\\.?|Court|Circle|Cir\\.?|Place|Pl\\.?)))",
            Pattern.MULTILINE);

    // Pattern: Ghana National ID / NHIS / Passport numbers
    private static final Pattern NATIONAL_ID_PATTERN = Pattern.compile(
            "(?i)((?:national\\s+id|NHIS|passport|ID)\\s*(?:number|no\\.?)?\\s*[:=]?\\s*)([A-Z0-9\\-]{6,20})");

    /**
     * Strip PHI from the given text content.
     *
     * @param content The text content to strip
     * @return Stripped content with PHI replaced by [REDACTED]
     */
    public String stripPhi(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }

        String result = content;

        result = PATIENT_NAME_PATTERN.matcher(result).replaceAll("$1" + REDACTED);
        result = NAME_LABEL_PATTERN.matcher(result).replaceAll("$1" + REDACTED);
        result = DOB_PATTERN.matcher(result).replaceAll("$1" + REDACTED);
        result = SSN_PATTERN.matcher(result).replaceAll(REDACTED);
        result = PHONE_PATTERN.matcher(result).replaceAll("$1" + REDACTED);
        result = EMAIL_PATTERN.matcher(result).replaceAll("$1" + REDACTED);
        result = MRN_PATTERN.matcher(result).replaceAll("$1" + REDACTED);
        result = ADDRESS_PATTERN.matcher(result).replaceAll("$1" + REDACTED);
        result = NATIONAL_ID_PATTERN.matcher(result).replaceAll("$1" + REDACTED);

        return result;
    }

    /**
     * Check if the content contains any detectable PHI patterns.
     *
     * @param content The text to check
     * @return true if PHI patterns were detected
     */
    public boolean containsPhi(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }

        return PATIENT_NAME_PATTERN.matcher(content).find()
                || NAME_LABEL_PATTERN.matcher(content).find()
                || DOB_PATTERN.matcher(content).find()
                || SSN_PATTERN.matcher(content).find()
                || PHONE_PATTERN.matcher(content).find()
                || MRN_PATTERN.matcher(content).find()
                || ADDRESS_PATTERN.matcher(content).find()
                || NATIONAL_ID_PATTERN.matcher(content).find();
    }

    /**
     * Get a list of PHI types detected in the content.
     *
     * @param content The text to check
     * @return List of detected PHI type names
     */
    public List<String> detectPhiTypes(String content) {
        List<String> types = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return types;
        }

        if (PATIENT_NAME_PATTERN.matcher(content).find() || NAME_LABEL_PATTERN.matcher(content).find()) {
            types.add("Patient Name");
        }
        if (DOB_PATTERN.matcher(content).find()) {
            types.add("Date of Birth");
        }
        if (SSN_PATTERN.matcher(content).find()) {
            types.add("SSN");
        }
        if (PHONE_PATTERN.matcher(content).find()) {
            types.add("Phone Number");
        }
        if (EMAIL_PATTERN.matcher(content).find()) {
            types.add("Email Address");
        }
        if (MRN_PATTERN.matcher(content).find()) {
            types.add("Medical Record Number");
        }
        if (ADDRESS_PATTERN.matcher(content).find()) {
            types.add("Address");
        }
        if (NATIONAL_ID_PATTERN.matcher(content).find()) {
            types.add("National ID");
        }

        return types;
    }
}
