package com.giftedlabs.echoinhealthbackend.service;

import com.giftedlabs.echoinhealthbackend.entity.Report;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.time.format.DateTimeFormatter;

/**
 * Service for generating DOCX reports using Apache POI (UR-025).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportDocxService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMMM dd, yyyy");

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
}
