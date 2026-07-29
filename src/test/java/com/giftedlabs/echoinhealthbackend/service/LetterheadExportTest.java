package com.giftedlabs.echoinhealthbackend.service;

import com.giftedlabs.echoinhealthbackend.entity.Organization;
import com.giftedlabs.echoinhealthbackend.entity.Report;
import com.giftedlabs.echoinhealthbackend.entity.SubscriptionTier;
import com.giftedlabs.echoinhealthbackend.entity.User;
import com.giftedlabs.echoinhealthbackend.repository.ReportRepository;
import com.giftedlabs.echoinhealthbackend.repository.UserRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * UR-045: the uploaded letterhead must appear on exported reports.
 *
 * <p>Both exporters previously drew only the literal string "Custom Letterhead" where the
 * hospital's logo belonged — the uploaded image was stored and validated but never fetched,
 * so exports carried a text placeholder instead of branding.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LetterheadExportTest {

    @Mock private ReportRepository reportRepository;
    @Mock private UserRepository userRepository;
    @Mock private ReportSignatureResolver reportSignatureResolver;
    @Mock private FileStorageService fileStorageService;

    private byte[] pngLetterhead() throws Exception {
        BufferedImage image = new BufferedImage(600, 120, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.BLUE);
        graphics.fillRect(0, 0, 600, 120);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private Report report(String letterheadPath) {
        Organization organization = Organization.builder()
                .id("org-1")
                .name("Org")
                .hospitalName("Riverside Imaging")
                .address("12 Clinic Road")
                .subscriptionTier(SubscriptionTier.PRO)
                .letterheadUrl(letterheadPath)
                .build();
        User user = User.builder()
                .id("user-1").email("u@test.local").firstName("Ada").lastName("Lovelace")
                .passwordHash("x").organization(organization).build();
        return Report.builder()
                .id("report-1")
                .organization(organization)
                .user(user)
                .patientName("Test Patient")
                .scanDate(LocalDate.now())
                .findings("Liver is normal in size and echotexture.")
                .impression("Normal study.")
                .build();
    }

    @Test
    void pdfExportEmbedsTheUploadedLetterheadImage() throws Exception {
        when(fileStorageService.downloadFile("org-1/branding/logo.png")).thenReturn(pngLetterhead());
        LetterheadImageService letterheadImageService = new LetterheadImageService(fileStorageService);
        ReportPdfService pdfService = new ReportPdfService(
                reportRepository, userRepository, reportSignatureResolver, letterheadImageService);

        byte[] pdf = pdfService.generatePdf(report("org-1/branding/logo.png"));

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertTrue(containsImage(document), "the letterhead image must be embedded in the PDF");

            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Riverside Imaging"));
            assertFalse(text.contains("Custom Letterhead"),
                    "the text placeholder must be gone once a real letterhead is drawn");
        }
    }

    @Test
    void pdfExportFallsBackToTheDefaultBannerWhenThereIsNoLetterhead() throws Exception {
        LetterheadImageService letterheadImageService = new LetterheadImageService(fileStorageService);
        ReportPdfService pdfService = new ReportPdfService(
                reportRepository, userRepository, reportSignatureResolver, letterheadImageService);

        byte[] pdf = pdfService.generatePdf(report(null));

        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Echion Health Default Letterhead"),
                    "an org with no letterhead still gets branded output (UR-006)");
        }
    }

    /**
     * An unreadable or unsupported letterhead must degrade the export, not fail it — a
     * clinician should still be able to export the report.
     */
    @Test
    void unreadableLetterheadDegradesToTheTextBanner() throws Exception {
        when(fileStorageService.downloadFile("org-1/branding/logo.png"))
                .thenThrow(new RuntimeException("object store unavailable"));
        LetterheadImageService letterheadImageService = new LetterheadImageService(fileStorageService);
        ReportPdfService pdfService = new ReportPdfService(
                reportRepository, userRepository, reportSignatureResolver, letterheadImageService);

        byte[] pdf = pdfService.generatePdf(report("org-1/branding/logo.png"));

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertTrue(new PDFTextStripper().getText(document).contains("Riverside Imaging"));
        }
    }

    @Test
    void svgLetterheadsAreNotTreatedAsEmbeddableRasters() {
        LetterheadImageService letterheadImageService = new LetterheadImageService(fileStorageService);

        Optional<LetterheadImageService.Letterhead> resolved = letterheadImageService.resolve(
                report("org-1/branding/logo.svg").getOrganization());

        assertTrue(resolved.isEmpty(),
                "neither PDFBox nor POI can rasterise SVG; callers must fall back to text");
    }

    private boolean containsImage(PDDocument document) throws Exception {
        for (PDPage page : document.getPages()) {
            PDResources resources = page.getResources();
            for (var name : resources.getXObjectNames()) {
                PDXObject xObject = resources.getXObject(name);
                if (xObject instanceof PDImageXObject) {
                    return true;
                }
            }
        }
        return false;
    }
}
