package com.giftedlabs.echoinhealthbackend.util;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class TextExtractorTest {

    private final TextExtractor textExtractor = new TextExtractor();

    @Test
    void extractsTextFromDocxUsingContentAndFilenameDetection() throws Exception {
        byte[] documentBytes;
        try (XWPFDocument document = new XWPFDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("Echion diagnostic report findings");
            document.write(output);
            documentBytes = output.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "report.docx",
                "application/octet-stream",
                documentBytes);

        assertThat(textExtractor.extractText(file))
                .contains("Echion diagnostic report findings");
    }

    @Test
    void returnsEmptyTextForAnEmptyUpload() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                new byte[0]);

        assertThat(textExtractor.extractText(file)).isEmpty();
    }
}
