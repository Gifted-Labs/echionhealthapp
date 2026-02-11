package com.giftedlabs.echoinhealthbackend.service;

import com.giftedlabs.echoinhealthbackend.entity.Report;
import com.giftedlabs.echoinhealthbackend.exception.ResourceNotFoundException;
import com.giftedlabs.echoinhealthbackend.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for generating PDF reports from Report entities
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportPdfService {

    private final ReportRepository reportRepository;

    private static final float MARGIN = 50;
    private static final float LINE_HEIGHT = 14;
    private static final float SECTION_SPACING = 20;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy");

    /**
     * Generate a PDF for a specific report
     */
    public byte[] generateReportPdf(String reportId, String userId) throws IOException {
        Report report = reportRepository.findByIdAndUserId(reportId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));

        return generatePdf(report);
    }

    /**
     * Generate PDF for a Report entity
     */
    public byte[] generatePdf(Report report) throws IOException {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            PDType1Font titleFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font headerFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font normalFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            float pageWidth = page.getMediaBox().getWidth();
            float pageHeight = page.getMediaBox().getHeight();
            float yPosition = pageHeight - MARGIN;

            PDPageContentStream contentStream = new PDPageContentStream(document, page);

            // Title - Report Type
            String title = "ULTRASOUND SCAN REPORT";
            if (report.getScanType() != null) {
                title = "ULTRASOUND SCAN - " + formatEnumName(report.getScanType().name()) + " REPORT";
            }
            yPosition = drawCenteredText(contentStream, title, titleFont, 16, pageWidth, yPosition);
            yPosition -= SECTION_SPACING;

            // Report Type (Normal/Pathology)
            if (report.getReportType() != null) {
                yPosition = drawCenteredText(contentStream, "(" + formatEnumName(report.getReportType().name()) + ")",
                        normalFont, 11, pageWidth, yPosition);
                yPosition -= SECTION_SPACING;
            }

            // Horizontal line
            yPosition = drawHorizontalLine(contentStream, MARGIN, pageWidth - MARGIN, yPosition);
            yPosition -= SECTION_SPACING;

            // Patient Information Section
            yPosition = drawSectionHeader(contentStream, "PATIENT INFORMATION", headerFont, MARGIN, yPosition);
            yPosition -= LINE_HEIGHT;

            if (report.getPatientName() != null) {
                yPosition = drawLabelValue(contentStream, normalFont, "Patient Name:", report.getPatientName(), MARGIN,
                        yPosition);
            }
            if (report.getPatientId() != null) {
                yPosition = drawLabelValue(contentStream, normalFont, "Patient ID:", report.getPatientId(), MARGIN,
                        yPosition);
            }
            if (report.getPatientAge() != null) {
                yPosition = drawLabelValue(contentStream, normalFont, "Age:", report.getPatientAge() + " years", MARGIN,
                        yPosition);
            }
            if (report.getPatientSex() != null) {
                yPosition = drawLabelValue(contentStream, normalFont, "Sex:",
                        formatEnumName(report.getPatientSex().name()), MARGIN, yPosition);
            }
            if (report.getScanDate() != null) {
                yPosition = drawLabelValue(contentStream, normalFont, "Date of Scan:",
                        report.getScanDate().format(DATE_FORMATTER), MARGIN, yPosition);
            }

            yPosition -= SECTION_SPACING;

            // Clinical History
            if (report.getClinicalHistory() != null && !report.getClinicalHistory().isBlank()) {
                yPosition = drawSectionHeader(contentStream, "CLINICAL HISTORY", headerFont, MARGIN, yPosition);
                yPosition -= LINE_HEIGHT;
                yPosition = drawWrappedText(contentStream, report.getClinicalHistory(), normalFont, 11, MARGIN,
                        yPosition, pageWidth - 2 * MARGIN);
                yPosition -= SECTION_SPACING;
            }

            // Findings
            if (report.getFindings() != null && !report.getFindings().isBlank()) {
                yPosition = drawSectionHeader(contentStream, "FINDINGS", headerFont, MARGIN, yPosition);
                yPosition -= LINE_HEIGHT;
                yPosition = drawWrappedText(contentStream, report.getFindings(), normalFont, 11, MARGIN, yPosition,
                        pageWidth - 2 * MARGIN);
                yPosition -= SECTION_SPACING;
            }

            // Impression
            if (report.getImpression() != null && !report.getImpression().isBlank()) {
                yPosition = drawSectionHeader(contentStream, "IMPRESSION", headerFont, MARGIN, yPosition);
                yPosition -= LINE_HEIGHT;
                yPosition = drawWrappedText(contentStream, report.getImpression(), normalFont, 11, MARGIN, yPosition,
                        pageWidth - 2 * MARGIN);
                yPosition -= SECTION_SPACING;
            }

            // Recommendation
            if (report.getRecommendation() != null && !report.getRecommendation().isBlank()) {
                yPosition = drawSectionHeader(contentStream, "RECOMMENDATION", headerFont, MARGIN, yPosition);
                yPosition -= LINE_HEIGHT;
                yPosition = drawWrappedText(contentStream, report.getRecommendation(), normalFont, 11, MARGIN,
                        yPosition, pageWidth - 2 * MARGIN);
                yPosition -= SECTION_SPACING;
            }

            // Footer with report ID and generation date
            contentStream.beginText();
            contentStream.setFont(normalFont, 8);
            contentStream.newLineAtOffset(MARGIN, MARGIN);
            contentStream.showText("Report ID: " + report.getId() + " | Generated: " +
                    java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")));
            contentStream.endText();

            contentStream.close();

            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }

    private float drawCenteredText(PDPageContentStream contentStream, String text, PDType1Font font,
            float fontSize, float pageWidth, float yPosition) throws IOException {
        float textWidth = font.getStringWidth(text) / 1000 * fontSize;
        float xPosition = (pageWidth - textWidth) / 2;

        contentStream.beginText();
        contentStream.setFont(font, fontSize);
        contentStream.newLineAtOffset(xPosition, yPosition);
        contentStream.showText(text);
        contentStream.endText();

        return yPosition - LINE_HEIGHT;
    }

    private float drawSectionHeader(PDPageContentStream contentStream, String text, PDType1Font font,
            float xPosition, float yPosition) throws IOException {
        contentStream.beginText();
        contentStream.setFont(font, 12);
        contentStream.newLineAtOffset(xPosition, yPosition);
        contentStream.showText(text);
        contentStream.endText();

        return yPosition - LINE_HEIGHT;
    }

    private float drawLabelValue(PDPageContentStream contentStream, PDType1Font font, String label,
            String value, float xPosition, float yPosition) throws IOException {
        contentStream.beginText();
        contentStream.setFont(font, 11);
        contentStream.newLineAtOffset(xPosition, yPosition);
        contentStream.showText(label + " " + value);
        contentStream.endText();

        return yPosition - LINE_HEIGHT;
    }

    private float drawHorizontalLine(PDPageContentStream contentStream, float startX, float endX,
            float yPosition) throws IOException {
        contentStream.setLineWidth(1);
        contentStream.moveTo(startX, yPosition);
        contentStream.lineTo(endX, yPosition);
        contentStream.stroke();

        return yPosition - 5;
    }

    private float drawWrappedText(PDPageContentStream contentStream, String text, PDType1Font font,
            float fontSize, float xPosition, float yPosition, float maxWidth) throws IOException {
        List<String> lines = wrapText(text, font, fontSize, maxWidth);

        for (String line : lines) {
            contentStream.beginText();
            contentStream.setFont(font, fontSize);
            contentStream.newLineAtOffset(xPosition, yPosition);
            contentStream.showText(line);
            contentStream.endText();
            yPosition -= LINE_HEIGHT;
        }

        return yPosition;
    }

    private List<String> wrapText(String text, PDType1Font font, float fontSize, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();

        // Split by newlines first
        String[] paragraphs = text.split("\n");

        for (String paragraph : paragraphs) {
            String[] words = paragraph.split(" ");
            StringBuilder currentLine = new StringBuilder();

            for (String word : words) {
                String testLine = currentLine.length() > 0 ? currentLine + " " + word : word;
                float textWidth = font.getStringWidth(testLine) / 1000 * fontSize;

                if (textWidth > maxWidth) {
                    if (currentLine.length() > 0) {
                        lines.add(currentLine.toString());
                        currentLine = new StringBuilder(word);
                    } else {
                        // Word is too long, add it anyway
                        lines.add(word);
                    }
                } else {
                    currentLine = new StringBuilder(testLine);
                }
            }

            if (currentLine.length() > 0) {
                lines.add(currentLine.toString());
            }
        }

        return lines;
    }

    private String formatEnumName(String enumName) {
        return enumName.replace("_", " ");
    }

    /**
     * Generate filename for the PDF
     */
    public String generateFilename(String reportId, String userId) {
        Report report = reportRepository.findByIdAndUserId(reportId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));

        String patientName = report.getPatientName() != null ? report.getPatientName().replaceAll("[^a-zA-Z0-9]", "_")
                : "Unknown";
        String scanType = report.getScanType() != null ? report.getScanType().name() : "SCAN";
        String date = report.getScanDate() != null
                ? report.getScanDate().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                : "";

        return String.format("Report_%s_%s_%s.pdf", patientName, scanType, date);
    }
}
