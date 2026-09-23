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
import java.nio.charset.StandardCharsets;

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
        if (file == null || file.isEmpty()) {
            return "";
        }

        try {
            // Read once because servlet-backed multipart streams are not guaranteed to be
            // repeatable. Supplying the filename also lets Tika distinguish OOXML containers
            // from an ordinary ZIP when their package metadata is unusual.
            byte[] content = file.getBytes();
            String contentType = tika.detect(content, file.getOriginalFilename());

            try (InputStream inputStream = new java.io.ByteArrayInputStream(content)) {
                if ("application/pdf".equals(contentType)) {
                    return extractTextFromPdf(inputStream);
                }
                if ("application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(contentType)
                        || "application/x-tika-ooxml".equals(contentType)) {
                    return extractTextFromWord(inputStream);
                }
                if ("text/plain".equals(contentType)) {
                    return new String(content, StandardCharsets.UTF_8);
                }

                log.warn("Unsupported content type for text extraction: {}", contentType);
                return "";
            }
        } catch (IOException e) {
            log.error("Could not read uploaded document for text extraction", e);
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
