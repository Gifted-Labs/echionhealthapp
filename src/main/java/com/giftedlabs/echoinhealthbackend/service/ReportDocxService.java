package com.giftedlabs.echoinhealthbackend.service;

import com.giftedlabs.echoinhealthbackend.entity.Report;
import com.giftedlabs.echoinhealthbackend.entity.Organization;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.util.Units;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.springframework.stereotype.Service;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Service for generating DOCX reports using Apache POI (UR-025).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportDocxService {

    private final LetterheadImageService letterheadImageService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMMM dd, yyyy");
    private static final String DEFAULT_LETTERHEAD_LABEL = "Echion Health Default Letterhead";
    private static final List<String> JPEG_FORMATS = List.of("jpg", "jpeg");
    private final ReportSignatureResolver reportSignatureResolver;

    /**
     * Generate a DOCX document from a report
     *
     * @param report The report to convert
     * @return Byte array of the DOCX file
     */
    public byte[] generateDocx(Report report) throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            // Set page margins
            setPageMargins(document);

            addBrandingHeader(document, report.getOrganization());

            // Title
            addTitle(document, "ULTRASOUND REPORT");

            // Patient Information Section
            addSectionHeader(document, "Patient Information");
            addField(document, "Patient Name", report.getPatientName());
            addField(document, "Patient ID", report.getPatientId());
            addField(document, "Age", report.getPatientAge() != null ? report.getPatientAge().toString() : "N/A");
            addField(document, "Gender", report.getPatientSex() != null ? report.getPatientSex().name() : "N/A");

            addEmptyLine(document);

            // Scan Details Section
            addSectionHeader(document, "Scan Details");
            addField(document, "Scan Date", report.getScanDate() != null
                    ? report.getScanDate().format(DATE_FORMATTER)
                    : "N/A");
            addField(document, "Scan Type", report.getScanType() != null
                    ? report.getScanType().name()
                    : "N/A");
            addField(document, "Report Type", report.getReportType() != null
                    ? report.getReportType().name()
                    : "N/A");

            addEmptyLine(document);

            // Clinical History
            if (report.getClinicalHistory() != null && !report.getClinicalHistory().isEmpty()) {
                addSectionHeader(document, "Clinical History");
                addParagraph(document, report.getClinicalHistory());
                addEmptyLine(document);
            }

            // Findings
            addSectionHeader(document, "Findings");
            addParagraph(document, report.getFindings() != null ? report.getFindings() : "No findings recorded.");
            addEmptyLine(document);

            // Impression
            if (report.getImpression() != null && !report.getImpression().isEmpty()) {
                addSectionHeader(document, "Impression");
                addParagraph(document, report.getImpression());
                addEmptyLine(document);
            }

            // Recommendation
            if (report.getRecommendation() != null && !report.getRecommendation().isEmpty()) {
                addSectionHeader(document, "Recommendation");
                addParagraph(document, report.getRecommendation());
                addEmptyLine(document);
            }

            addSignatureSection(document, report);

            // Footer with generation date
            addFooter(document, report);

            // Write to byte array
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private void setPageMargins(XWPFDocument document) {
        CTSectPr sectPr = document.getDocument().getBody().addNewSectPr();
        CTPageMar pageMar = sectPr.addNewPgMar();
        pageMar.setTop(BigInteger.valueOf(1440)); // 1 inch
        pageMar.setBottom(BigInteger.valueOf(1440));
        pageMar.setLeft(BigInteger.valueOf(1440));
        pageMar.setRight(BigInteger.valueOf(1440));
    }

    private void addTitle(XWPFDocument document, String title) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        paragraph.setSpacingAfter(200);

        XWPFRun run = paragraph.createRun();
        run.setText(title);
        run.setBold(true);
        run.setFontSize(18);
        run.setFontFamily("Arial");
    }

    private void addBrandingHeader(XWPFDocument document, Organization organization) {
        // Embed the actual uploaded letterhead; the text banner is only a fallback.
        boolean letterheadDrawn = addLetterheadImage(document, organization);

        String hospitalName = organization != null && organization.getHospitalName() != null
                ? organization.getHospitalName()
                : "Echion Health";
        addTitle(document, hospitalName);

        if (organization != null && organization.getAddress() != null && !organization.getAddress().isBlank()) {
            addCenteredLine(document, organization.getAddress(), 10, false);
        }

        String contactLine = buildContactLine(organization);
        if (!contactLine.isBlank()) {
            addCenteredLine(document, contactLine, 10, false);
        }

        if (!letterheadDrawn) {
            addCenteredLine(document, DEFAULT_LETTERHEAD_LABEL, 9, true);
        }
        addEmptyLine(document);
    }

    /**
     * Inserts the organization's letterhead, scaled to the printable width. Returns false when
     * there is nothing embeddable, so the caller can fall back to the text banner.
     */
    private boolean addLetterheadImage(XWPFDocument document, Organization organization) {
        Optional<LetterheadImageService.Letterhead> letterhead =
                letterheadImageService.resolve(organization);
        if (letterhead.isEmpty()) {
            return false;
        }

        try (ByteArrayInputStream stream = new ByteArrayInputStream(letterhead.get().data())) {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(letterhead.get().data()));
            if (image == null) {
                return false;
            }

            double maxWidthPoints = 460d; // A4 width less the configured margins
            double maxHeightPoints = 90d;
            double scale = Math.min(maxWidthPoints / image.getWidth(), maxHeightPoints / image.getHeight());
            int width = (int) Math.round(image.getWidth() * scale);
            int height = (int) Math.round(image.getHeight() * scale);

            XWPFParagraph paragraph = document.createParagraph();
            paragraph.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun run = paragraph.createRun();
            run.addPicture(stream,
                    JPEG_FORMATS.contains(letterhead.get().format())
                            ? XWPFDocument.PICTURE_TYPE_JPEG
                            : XWPFDocument.PICTURE_TYPE_PNG,
                    "letterhead." + letterhead.get().format(),
                    Units.toEMU(width), Units.toEMU(height));
            return true;
        } catch (IOException | InvalidFormatException e) {
            log.warn("Could not embed letterhead in DOCX export; falling back to text banner", e);
            return false;
        }
    }

    private void addSectionHeader(XWPFDocument document, String header) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingBefore(100);
        paragraph.setSpacingAfter(50);

        XWPFRun run = paragraph.createRun();
        run.setText(header);
        run.setBold(true);
        run.setFontSize(12);
        run.setFontFamily("Arial");
        run.setUnderline(UnderlinePatterns.SINGLE);
    }

    private void addField(XWPFDocument document, String label, String value) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingAfter(50);

        XWPFRun labelRun = paragraph.createRun();
        labelRun.setText(label + ": ");
        labelRun.setBold(true);
        labelRun.setFontSize(11);
        labelRun.setFontFamily("Arial");

        XWPFRun valueRun = paragraph.createRun();
        valueRun.setText(value != null ? value : "N/A");
        valueRun.setFontSize(11);
        valueRun.setFontFamily("Arial");
    }

    private void addParagraph(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingAfter(100);

        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setFontSize(11);
        run.setFontFamily("Arial");
    }

    private void addEmptyLine(XWPFDocument document) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingAfter(100);
    }

    private void addCenteredLine(XWPFDocument document, String text, int fontSize, boolean italic) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setFontSize(fontSize);
        run.setFontFamily("Arial");
        run.setItalic(italic);
    }

    private void addFooter(XWPFDocument document, Report report) {
        addEmptyLine(document);

        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        paragraph.setBorderTop(Borders.SINGLE);

        XWPFRun run = paragraph.createRun();
        run.setText("Generated by Echion Health System");
        run.setFontSize(9);
        run.setFontFamily("Arial");
        run.setItalic(true);
    }

    private void addSignatureSection(XWPFDocument document, Report report) {
        if ((report.getSignatoryName() == null || report.getSignatoryName().isBlank())
                && report.getAppliedSignatureId() == null) {
            return;
        }

        addSectionHeader(document, "Signature");
        ReportSignatureResolver.ResolvedSignature resolvedSignature = reportSignatureResolver.resolve(report).orElse(null);
        if (resolvedSignature != null && resolvedSignature.getImageBytes() != null && resolvedSignature.getImageBytes().length > 0) {
            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun run = paragraph.createRun();
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(resolvedSignature.getImageBytes())) {
                run.addPicture(
                        inputStream,
                        pictureType(resolvedSignature.getImagePath()),
                        resolvedSignature.getImagePath(),
                        Units.toEMU(140),
                        Units.toEMU(55));
            } catch (Exception ex) {
                log.warn("Failed to render signature image for report {}", report.getId(), ex);
            }
        }

        addField(document, "Signed By", report.getSignatoryName());
        addField(document, "Designation", report.getSignatoryDesignation() != null
                ? formatEnumName(report.getSignatoryDesignation().name())
                : "N/A");
        if (resolvedSignature != null && resolvedSignature.getLabel() != null && !resolvedSignature.getLabel().isBlank()) {
            addField(document, "Signature Label", resolvedSignature.getLabel());
        }
        addEmptyLine(document);
    }

    private int pictureType(String imagePath) {
        if (imagePath != null && imagePath.toLowerCase().endsWith(".png")) {
            return Document.PICTURE_TYPE_PNG;
        }
        return Document.PICTURE_TYPE_JPEG;
    }

    private String formatEnumName(String value) {
        return value.replace("_", " ");
    }

    private String buildContactLine(Organization organization) {
        if (organization == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        appendContactPart(builder, organization.getPhone());
        appendContactPart(builder, organization.getEmail());
        appendContactPart(builder, organization.getWebsite());
        return builder.toString();
    }

    private void appendContactPart(StringBuilder builder, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append(" | ");
        }
        builder.append(value);
    }
}
