package com.giftedlabs.echoinhealthbackend.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * Utility for extracting text from various document formats
 */
@Component
@Slf4j
public class TextExtractor {

    private final Tika tika = new Tika();

    /**
     * Extract text from a multipart file based on its content type
     */
    public String extractText(MultipartFile file) {
        String contentType;
        try {
            contentType = tika.detect(file.getInputStream());
        } catch (IOException e) {
            log.error("Failed to detect content type", e);
            return "";
        }

        try (InputStream inputStream = file.getInputStream()) {
            if (contentType.equals("application/pdf")) {
                return extractTextFromPdf(inputStream);
            } else if (contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document") ||
                    contentType.equals("application/msword")) {
                // Note: POI support for old .doc (HWPF) requires poi-scratchpad, assume .docx
                // (XWPF) for now
                // or handle both if dependencies are added. The pom added poi and poi-ooxml.
                // Standard poi supports .doc, poi-ooxml supports .docx.
                // For simplicity and common use, focusing on .docx XWPF.
                // To support classic .doc, we would need HWPFDocument.
                // Given "application/msword" usually maps to .doc, we should check magic bytes
                // or strict handling.
                // For now, let's try XWPF and fallback or error if it's old OLE2 format.
                try {
                    return extractTextFromWord(inputStream);
                } catch (Exception e) {
                    log.warn("Failed to extract as .docx, might be .doc or other format", e);
                    return "";
                }
            } else {
                log.warn("Unsupported content type for text extraction: {}", contentType);
                return "";
            }
        } catch (IOException e) {
            log.error("Error reading file stream", e);
            return "";
        }
    }

    private String extractTextFromPdf(InputStream inputStream) {
        try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        } catch (IOException e) {
            log.error("Error extracting text from PDF", e);
            return "";
        }
    }

    private String extractTextFromWord(InputStream inputStream) {
        try (XWPFDocument document = new XWPFDocument(inputStream);
                XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        } catch (IOException e) {
            log.error("Error extracting text from Word document", e);
            return "";
        }
    }

    /**
     * Detect content type using Apache Tika
     */
    public String detectContentType(InputStream inputStream) throws IOException {
        return tika.detect(inputStream);
    }
}
