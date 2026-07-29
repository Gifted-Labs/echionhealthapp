package com.giftedlabs.echoinhealthbackend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.giftedlabs.echoinhealthbackend.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MultiTenantFoundationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void signupAutoProvisionsHospitalAdminAndReturnsUsableLogin() throws Exception {
        registerOrg("org-admin@phase1.test", "Alpha Org", "Alpha Hospital");

        String token = login("org-admin@phase1.test", "Password1!");

        mockMvc.perform(get("/auth/profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void reportEndpointsRejectCrossOrganizationDirectIdAccess() throws Exception {
        registerOrg("alpha-admin@phase1.test", "Alpha Org", "Alpha Hospital");
        registerOrg("beta-admin@phase1.test", "Beta Org", "Beta Hospital");

        String alphaToken = login("alpha-admin@phase1.test", "Password1!");
        String betaToken = login("beta-admin@phase1.test", "Password1!");

        String alphaReportId = createReport(alphaToken, "Alice Patient");

        mockMvc.perform(get("/vault/reports/{id}", alphaReportId)
                        .header("Authorization", "Bearer " + betaToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/vault/reports/{id}", alphaReportId)
                        .header("Authorization", "Bearer " + betaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "findings", "Attempted cross-org overwrite"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminEndpointsRejectCrossOrganizationUserLookup() throws Exception {
        registerOrg("alpha-admin-2@phase1.test", "Alpha Org 2", "Alpha Hospital 2");
        registerOrg("beta-admin-2@phase1.test", "Beta Org 2", "Beta Hospital 2");

        String alphaToken = login("alpha-admin-2@phase1.test", "Password1!");
        String betaToken = login("beta-admin-2@phase1.test", "Password1!");

        String alphaUserId = readProfile(alphaToken).path("data").path("id").asText();

        mockMvc.perform(get("/admin/users/{id}", alphaUserId)
                        .header("Authorization", "Bearer " + betaToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void hospitalAdminCanCreateAndDeactivateTenantUser() throws Exception {
        registerOrg("phase2-admin@phase2.test", "Phase 2 Org", "Phase 2 Hospital");

        String adminToken = login("phase2-admin@phase2.test", "Password1!");

        JsonNode createdUser = objectMapper.readTree(mockMvc.perform(post("/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(java.util.Map.ofEntries(
                                java.util.Map.entry("firstName", "Tenant"),
                                java.util.Map.entry("lastName", "Sonographer"),
                                java.util.Map.entry("username", "tenant.sono"),
                                java.util.Map.entry("email", "tenant-user@phase2.test"),
                                java.util.Map.entry("password", "Password1!"),
                                java.util.Map.entry("phone", "+233111111111"),
                                java.util.Map.entry("department", "Ultrasound"),
                                java.util.Map.entry("serviceNumber", "SV-001"),
                                java.util.Map.entry("role", "SONOGRAPHER"),
                                java.util.Map.entry("designation", "SONOGRAPHER"),
                                java.util.Map.entry("canUploadSignature", false)))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        String userId = createdUser.path("data").path("id").asText();

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "identifier", "tenant.sono",
                                "organizationName", "Phase 2 Org",
                                "password", "Password1!"))))
                .andExpect(status().isOk());

        mockMvc.perform(put("/admin/users/{id}/deactivate", userId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "identifier", "tenant-user@phase2.test",
                                "password", "Password1!"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void organizationBrandingPreviewUsesDefaultLetterheadWhenNoneUploaded() throws Exception {
        registerOrg("branding-admin@phase3.test", "Branding Org", "Branding Hospital");
        String token = login("branding-admin@phase3.test", "Password1!");

        mockMvc.perform(get("/org/letterhead/preview")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void organizationLetterheadUploadRejectsWrongFormat() throws Exception {
        registerOrg("branding-admin-2@phase3.test", "Branding Org 2", "Branding Hospital 2");
        String token = login("branding-admin-2@phase3.test", "Password1!");

        MockMultipartFile invalid = new MockMultipartFile(
                "file",
                "letterhead.txt",
                "text/plain",
                "not-an-image".getBytes());

        mockMvc.perform(multipart("/org/letterhead")
                        .file(invalid)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void signatureUploadRequiresExplicitAdminAuthorization() throws Exception {
        registerOrg("signature-admin@phase4.test", "Signature Org", "Signature Hospital");
        String token = login("signature-admin@phase4.test", "Password1!");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "signature.png",
                "image/png",
                "fake-png".getBytes());

        mockMvc.perform(multipart("/signatures")
                        .file(file)
                        .param("label", "Full Signature")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void finalizationReturnsFieldLevelErrorsWhenRequiredFieldsAreMissing() throws Exception {
        registerOrg("finalize-admin@phase8.test", "Finalize Org", "Finalize Hospital");
        String token = login("finalize-admin@phase8.test", "Password1!");
        String adminUserId = readProfile(token).path("data").path("id").asText();

        grantSignaturePermission(token, adminUserId);
        String signatureId = uploadSignature(token);

        JsonNode created = objectMapper.readTree(mockMvc.perform(post("/vault/reports")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "patientName", "Incomplete Patient",
                                "patientAge", 41,
                                "scanDate", LocalDate.now().toString(),
                                "findings", "Findings captured without scan type"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        String reportId = created.path("data").path("id").asText();

        JsonNode error = objectMapper.readTree(mockMvc.perform(post("/vault/reports/{id}/finalize", reportId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "signatureId", signatureId,
                                "designation", "SONOGRAPHER"))))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString());

        assertEquals("Scan type is required", error.path("data").path("scanType").asText());
    }

    @Test
    void phase4WorkflowSupportsMultipleSignaturesDefaultSelectionAndAuditTrail() throws Exception {
        registerOrg("phase4-admin@phase4.test", "Phase 4 Org", "Phase 4 Hospital");
        String token = login("phase4-admin@phase4.test", "Password1!");
        String adminUserId = readProfile(token).path("data").path("id").asText();

        grantSignaturePermission(token, adminUserId);

        String firstSignatureId = uploadSignature(token, "Full Signature", true);
        String secondSignatureId = uploadSignature(token, "Initials", false);

        JsonNode initialSignatureList = listSignatures(token);
        assertEquals(2, initialSignatureList.path("data").size());
        assertEquals(firstSignatureId, findDefaultSignatureId(initialSignatureList));

        updateSignatureDefault(token, secondSignatureId);
        JsonNode updatedSignatureList = listSignatures(token);
        assertEquals(secondSignatureId, findDefaultSignatureId(updatedSignatureList));

        JsonNode reportJson = objectMapper.readTree(mockMvc.perform(post("/vault/reports")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "patientName", "Phase Four Patient",
                                "patientAge", 35,
                                "scanDate", LocalDate.now().toString(),
                                "scanType", "ABDOMINAL",
                                "findings", "No focal lesion detected",
                                "impression", "Normal abdominal ultrasound"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        String reportId = reportJson.path("data").path("id").asText();

        JsonNode finalized = objectMapper.readTree(mockMvc.perform(post("/vault/reports/{id}/finalize", reportId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "signatureId", secondSignatureId,
                                "designation", "CONSULTANT_RADIOLOGIST"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        assertEquals("FINALIZED", finalized.path("data").path("status").asText());
        assertEquals(secondSignatureId, finalized.path("data").path("appliedSignatureId").asText());
        assertEquals("CONSULTANT_RADIOLOGIST", finalized.path("data").path("signatoryDesignation").asText());
        assertTrue(finalized.path("data").path("signatoryName").asText().contains("Admin"));

        deleteSignature(token, secondSignatureId);
        JsonNode afterDelete = listSignatures(token);
        assertEquals(1, afterDelete.path("data").size());
        assertEquals(firstSignatureId, findDefaultSignatureId(afterDelete));

        waitForAuditActions(adminUserId, List.of("report_finalized", "signature_applied"));
        assertTrue(auditLogRepository.findByAction("signature_applied").stream()
                .anyMatch(log -> adminUserId.equals(log.getUser().getId())
                        && log.getDetails() != null
                        && log.getDetails().contains(secondSignatureId)
                        && log.getDetails().contains(reportId)));
    }

    @Test
    void scanTypeDefinitionsExposeStructuredFieldsAndDopplerMeasurementTable() throws Exception {
        registerOrg("phase5-admin@phase5.test", "Phase 5 Org", "Phase 5 Hospital");
        String token = login("phase5-admin@phase5.test", "Password1!");

        JsonNode definitions = objectMapper.readTree(mockMvc.perform(get("/vault/scan-types")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        assertTrue(definitions.path("data").size() >= 33);

        JsonNode doppler = objectMapper.readTree(mockMvc.perform(get("/vault/scan-types/ARTERIAL_DOPPLER_BOTH_LOWER")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        assertEquals(true, doppler.path("data").path("hasMeasurementTable").asBoolean());
        assertEquals("measurement", doppler.path("data").path("measurementColumns").get(0).asText());
    }

    @Test
    void templatesCanBeCreatedWithSpecificScanType() throws Exception {
        registerOrg("phase5-template-admin@phase5.test", "Phase 5 Template Org", "Phase 5 Template Hospital");
        String token = login("phase5-template-admin@phase5.test", "Password1!");

        JsonNode createdTemplate = objectMapper.readTree(mockMvc.perform(post("/vault/templates")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "name", "Adult Echo Template",
                                "description", "Structured adult echo template",
                                "reportType", "DIAGNOSTIC",
                                "scanType", "ECHO_ADULT",
                                "defaultFindings", "Cardiac chambers are within normal limits.",
                                "defaultImpression", "No significant echocardiographic abnormality.",
                                "isDefault", true))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        assertEquals("ECHO_ADULT", createdTemplate.path("data").path("scanType").asText());
    }

    @Test
    void reportCanBeCreatedFromTemplateAndAutosavedWithStructuredFindings() throws Exception {
        registerOrg("phase6-admin@phase6.test", "Phase 6 Org", "Phase 6 Hospital");
        String token = login("phase6-admin@phase6.test", "Password1!");

        JsonNode template = objectMapper.readTree(mockMvc.perform(post("/vault/templates")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "name", "Abdominal Normal",
                                "reportType", "NORMAL",
                                "scanType", "ABDOMINAL",
                                "defaultFindings", "Liver, spleen, kidneys and pancreas appear sonographically normal.",
                                "defaultImpression", "Normal abdominal ultrasound report."))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        String templateId = template.path("data").path("id").asText();

        JsonNode report = objectMapper.readTree(mockMvc.perform(post("/vault/reports")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "patientName", "Template Patient",
                                "patientAge", 28,
                                "scanDate", LocalDate.now().toString(),
                                "templateId", templateId))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        String reportId = report.path("data").path("id").asText();
        assertEquals("ABDOMINAL", report.path("data").path("scanType").asText());
        assertTrue(report.path("data").path("findings").asText().contains("sonographically normal"));

        JsonNode autosaved = objectMapper.readTree(mockMvc.perform(post("/vault/reports/{id}/autosave", reportId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "findings", "Liver is mildly enlarged.",
                                "impression", "Mild hepatomegaly.",
                                "recommendationOptions", List.of("Clinical correlation advised"),
                                "structuredFindings", Map.of(
                                        "liver", Map.of("finding", "Mild enlargement", "measurement", "16.8 cm"),
                                        "kidneys", Map.of("finding", "Normal appearance"))))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        assertEquals("Mild hepatomegaly.", autosaved.path("data").path("impression").asText());
        assertEquals("Mild enlargement", autosaved.path("data").path("structuredFindings").path("liver").path("finding").asText());
        assertEquals("Clinical correlation advised", autosaved.path("data").path("recommendationOptions").get(0).asText());
        assertTrue(!autosaved.path("data").path("lastAutoSaveAt").asText().isBlank());
    }

    @Test
    void helperToolsRequireFindingsAndReturnManualAuthoringGuidance() throws Exception {
        registerOrg("phase6-helper-admin@phase6.test", "Phase 6 Helper Org", "Phase 6 Helper Hospital");
        String token = login("phase6-helper-admin@phase6.test", "Password1!");

        mockMvc.perform(post("/vault/reports/helpers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "scanType", "ABDOMINAL",
                                "findings", ""))))
                .andExpect(status().isBadRequest());

        JsonNode helper = objectMapper.readTree(mockMvc.perform(post("/vault/reports/helpers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "scanType", "ABDOMINAL",
                                "findings", "There is a focal liver lesion with mild perihepatic fluid."))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        assertTrue(helper.path("data").path("impressionSuggestion").asText().contains("ABDOMINAL"));
        assertTrue(helper.path("data").path("clinicalDifferentials").size() > 0);
        assertTrue(helper.path("data").path("verifiedReferences").size() > 0);
    }

    @Test
    void manualTemplateCreationStripsPhiBeforeSavingToVault() throws Exception {
        registerOrg("phase9-admin@phase9.test", "Phase 9 Org", "Phase 9 Hospital");
        String token = login("phase9-admin@phase9.test", "Password1!");

        JsonNode createdTemplate = objectMapper.readTree(mockMvc.perform(post("/vault/templates")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "name", "Patient: John Doe Template",
                                "description", "Name: Jane Doe reference",
                                "scanType", "ABDOMINAL",
                                "defaultFindings", "Patient Name: John Doe. Liver is normal.",
                                "defaultImpression", "DOB: 01/02/1990. No focal lesion.",
                                "tags", List.of("normal", "indication: follow-up")))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        assertEquals(true, createdTemplate.path("data").path("phiFree").asBoolean());
        assertTrue(createdTemplate.path("data").path("name").asText().contains("[REDACTED]"));
        assertTrue(createdTemplate.path("data").path("defaultFindings").asText().contains("[REDACTED]"));
        assertTrue(!createdTemplate.path("data").path("defaultFindings").asText().contains("John Doe"));
        assertTrue(createdTemplate.path("data").path("category").asText().contains("ABDOMINAL"));
    }

    @Test
    void sharedScanAuditTrailIncludesShareAndAccessEvents() throws Exception {
        registerOrg("phase10-admin@phase10.test", "Phase 10 Org", "Phase 10 Hospital");
        String adminToken = login("phase10-admin@phase10.test", "Password1!");
        String colleagueId = createTenantUser(adminToken, "peer-reviewer@phase10.test", "Peer", "Reviewer", "peer.reviewer");

        String colleagueToken = login("peer-reviewer@phase10.test", "Password1!");
        String reportId = createReport(adminToken, "Shared Scan Patient");

        JsonNode shared = objectMapper.readTree(mockMvc.perform(post("/collaboration/share")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "reportId", reportId,
                                "sharingLevel", "SPECIFIC_COLLEAGUES",
                                "colleagueIds", List.of(colleagueId),
                                "title", "Need second opinion",
                                "requestMessage", "Please review the impression",
                                "urgency", "HIGH"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        String sharedScanId = shared.path("data").path("id").asText();

        mockMvc.perform(get("/collaboration/{id}", sharedScanId)
                        .header("Authorization", "Bearer " + colleagueToken))
                .andExpect(status().isOk());

        JsonNode auditTrail = objectMapper.readTree(mockMvc.perform(get("/collaboration/{id}/audit-trail", sharedScanId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        List<String> actions = new java.util.ArrayList<>();
        auditTrail.path("data").path("content").forEach(node -> actions.add(node.path("action").asText()));
        assertTrue(actions.contains("scan_shared"));
        assertTrue(actions.contains("shared_scan_accessed"));
    }

    @Test
    void basicPlanBlocksSixthActiveUserWithUpgradePrompt() throws Exception {
        registerOrg("phase11-admin@phase11.test", "Phase 11 Org", "Phase 11 Hospital");
        String adminToken = login("phase11-admin@phase11.test", "Password1!");

        createTenantUser(adminToken, "u1@phase11.test", "U1", "User", "u1");
        createTenantUser(adminToken, "u2@phase11.test", "U2", "User", "u2");
        createTenantUser(adminToken, "u3@phase11.test", "U3", "User", "u3");
        createTenantUser(adminToken, "u4@phase11.test", "U4", "User", "u4");

        JsonNode failure = objectMapper.readTree(mockMvc.perform(post("/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(java.util.Map.ofEntries(
                                java.util.Map.entry("firstName", "U5"),
                                java.util.Map.entry("lastName", "User"),
                                java.util.Map.entry("username", "u5"),
                                java.util.Map.entry("email", "u5@phase11.test"),
                                java.util.Map.entry("password", "Password1!"),
                                java.util.Map.entry("phone", "+233111111111"),
                                java.util.Map.entry("department", "Ultrasound"),
                                java.util.Map.entry("serviceNumber", "SV-u5"),
                                java.util.Map.entry("role", "SONOGRAPHER"),
                                java.util.Map.entry("designation", "SONOGRAPHER"),
                                java.util.Map.entry("canUploadSignature", false)))))
                .andExpect(status().isConflict())
                .andReturn()
                .getResponse()
                .getContentAsString());

        assertTrue(failure.path("message").asText().contains("Upgrade"));
    }

    /**
     * Granting a tier or an add-on is a commercial act, so it belongs to platform operators.
     * This previously asserted the opposite: that a hospital admin could POST their own tenant
     * onto the top tier with unlimited add-on credits, for free, which made every subscription
     * limit in the product advisory.
     */
    @Test
    void tenantAdminCannotGrantItselfATierOrAddons() throws Exception {
        registerOrg("phase11-selfgrant@phase11.test", "Phase 11 Self Grant Org", "Phase 11 Hospital");
        String adminToken = login("phase11-selfgrant@phase11.test", "Password1!");

        mockMvc.perform(post("/billing/upgrade")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("subscriptionTier", "ULTIMATE"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/billing/addons")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "extraStorageMb", 999_999,
                                "extraAiCredits", 999_999))))
                .andExpect(status().isForbidden());

        // Entitlement must be untouched by the rejected attempts.
        JsonNode plan = objectMapper.readTree(mockMvc.perform(get("/billing/plan")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertEquals("BASIC", plan.path("data").path("subscriptionTier").asText());
        assertEquals(0, plan.path("data").path("addonAiCredits").asInt());
        assertEquals(0, plan.path("data").path("addonStorageMb").asInt());
    }

    @Test
    void tenantAdminCanRequestAnUpgradeWithoutReceivingOne() throws Exception {
        registerOrg("phase11-request@phase11.test", "Phase 11 Request Org", "Phase 11 Hospital");
        String adminToken = login("phase11-request@phase11.test", "Password1!");

        mockMvc.perform(post("/billing/upgrade-request")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "requestedTier", "PRO",
                                "reason", "Adding two sonographers next month"))))
                .andExpect(status().isOk());

        JsonNode plan = objectMapper.readTree(mockMvc.perform(get("/billing/plan")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertEquals("BASIC", plan.path("data").path("subscriptionTier").asText());

        assertTrue(auditLogRepository.findAll().stream()
                .anyMatch(entry -> "subscription_upgrade_requested".equals(entry.getAction())));
    }

    @Test
    void platformOperatorGrantsTierAndAddonsAndUsageReflectsThem() throws Exception {
        registerOrg("phase11-billing@phase11.test", "Phase 11 Billing Org", "Phase 11 Billing Hospital");
        String adminToken = login("phase11-billing@phase11.test", "Password1!");
        String operatorToken = login("superadmin@echoinhealth.com", "test-admin-password");

        JsonNode initialPlan = objectMapper.readTree(mockMvc.perform(get("/billing/plan")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertEquals("BASIC", initialPlan.path("data").path("subscriptionTier").asText());
        assertEquals(false, initialPlan.path("data").path("autoGrammarCheckEnabled").asBoolean());
        String organizationId = initialPlan.path("data").path("organizationId").asText();

        JsonNode upgradedPlan = objectMapper.readTree(mockMvc.perform(post("/billing/upgrade")
                        .header("Authorization", "Bearer " + operatorToken)
                        .param("organizationId", organizationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("subscriptionTier", "PRO"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertEquals("PRO", upgradedPlan.path("data").path("subscriptionTier").asText());
        assertEquals(true, upgradedPlan.path("data").path("autoGrammarCheckEnabled").asBoolean());

        JsonNode addonsPlan = objectMapper.readTree(mockMvc.perform(post("/billing/addons")
                        .header("Authorization", "Bearer " + operatorToken)
                        .param("organizationId", organizationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "extraStorageMb", 200,
                                "extraAiCredits", 300,
                                "liteEmrIntegrationEnabled", true))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertEquals(200, addonsPlan.path("data").path("addonStorageMb").asInt());
        assertEquals(300, addonsPlan.path("data").path("addonAiCredits").asInt());
        assertEquals(true, addonsPlan.path("data").path("liteEmrIntegrationEnabled").asBoolean());

        JsonNode usage = objectMapper.readTree(mockMvc.perform(get("/billing/usage")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertEquals(1, usage.path("data").path("activeUsers").asInt());
        assertEquals(15, usage.path("data").path("userLimit").asInt());
        assertEquals(true, usage.path("data").path("autoGrammarCheckEnabled").asBoolean());
        assertEquals(true, usage.path("data").path("liteEmrIntegrationEnabled").asBoolean());
    }

    @Test
    void addonGrantsAreBoundedEvenForPlatformOperators() throws Exception {
        registerOrg("phase11-cap@phase11.test", "Phase 11 Cap Org", "Phase 11 Cap Hospital");
        String operatorToken = login("superadmin@echoinhealth.com", "test-admin-password");
        String adminToken = login("phase11-cap@phase11.test", "Password1!");

        String organizationId = objectMapper.readTree(mockMvc.perform(get("/billing/plan")
                        .header("Authorization", "Bearer " + adminToken))
                .andReturn().getResponse().getContentAsString())
                .path("data").path("organizationId").asText();

        mockMvc.perform(post("/billing/addons")
                        .header("Authorization", "Bearer " + operatorToken)
                        .param("organizationId", organizationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("extraAiCredits", 50_000_000))))
                .andExpect(status().isBadRequest());
    }

    private void registerOrg(String email, String organizationName, String hospitalName) throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(java.util.Map.ofEntries(
                                java.util.Map.entry("organizationName", organizationName),
                                java.util.Map.entry("hospitalName", hospitalName),
                                java.util.Map.entry("firstName", "Admin"),
                                java.util.Map.entry("lastName", "User"),
                                java.util.Map.entry("email", email),
                                java.util.Map.entry("phone", "+233000000000"),
                                java.util.Map.entry("address", "123 Tenant Street"),
                                java.util.Map.entry("website", "https://example.test"),
                                java.util.Map.entry("password", "Password1!")))))
                .andExpect(status().isCreated());
    }

    private String login(String email, String password) throws Exception {
        JsonNode json = objectMapper.readTree(mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "identifier", email,
                                "password", password))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        return json.path("data").path("accessToken").asText();
    }

    private String createReport(String token, String patientName) throws Exception {
        JsonNode json = objectMapper.readTree(mockMvc.perform(post("/vault/reports")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "patientName", patientName,
                                "patientAge", 32,
                                "scanDate", LocalDate.now().toString(),
                                "scanType", "ABDOMINAL",
                                "findings", "Normal abdominal ultrasound"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        return json.path("data").path("id").asText();
    }

    private String createTenantUser(String adminToken, String email, String firstName, String lastName, String username) throws Exception {
        JsonNode createdUser = objectMapper.readTree(mockMvc.perform(post("/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(java.util.Map.ofEntries(
                                java.util.Map.entry("firstName", firstName),
                                java.util.Map.entry("lastName", lastName),
                                java.util.Map.entry("username", username),
                                java.util.Map.entry("email", email),
                                java.util.Map.entry("password", "Password1!"),
                                java.util.Map.entry("phone", "+233111111111"),
                                java.util.Map.entry("department", "Ultrasound"),
                                java.util.Map.entry("serviceNumber", "SV-" + username),
                                java.util.Map.entry("role", "SONOGRAPHER"),
                                java.util.Map.entry("designation", "SONOGRAPHER"),
                                java.util.Map.entry("canUploadSignature", false)))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        return createdUser.path("data").path("id").asText();
    }

    private JsonNode readProfile(String token) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/auth/profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private void grantSignaturePermission(String token, String userId) throws Exception {
        mockMvc.perform(put("/admin/users/{id}/signature-permission", userId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "canUploadSignature", true))))
                .andExpect(status().isOk());
    }

    private String uploadSignature(String token) throws Exception {
        return uploadSignature(token, "Full Signature", true);
    }

    private String uploadSignature(String token, String label, boolean isDefault) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "signature.png",
                "image/png",
                "fake-png".getBytes());

        JsonNode json = objectMapper.readTree(mockMvc.perform(multipart("/signatures")
                        .file(file)
                        .param("label", label)
                        .param("isDefault", Boolean.toString(isDefault))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        return json.path("data").path("id").asText();
    }

    private JsonNode listSignatures(String token) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/signatures")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private void updateSignatureDefault(String token, String signatureId) throws Exception {
        mockMvc.perform(patch("/signatures/{id}", signatureId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("isDefault", true))))
                .andExpect(status().isOk());
    }

    private void deleteSignature(String token, String signatureId) throws Exception {
        mockMvc.perform(delete("/signatures/{id}", signatureId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private String findDefaultSignatureId(JsonNode response) {
        for (JsonNode signature : response.path("data")) {
            if (signature.path("isDefault").asBoolean(false)) {
                return signature.path("id").asText();
            }
        }
        return null;
    }

    private void waitForAuditActions(String userId, List<String> actions) throws Exception {
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            boolean allPresent = actions.stream().allMatch(action -> auditLogRepository.findByAction(action).stream()
                    .anyMatch(log -> log.getUser() != null && userId.equals(log.getUser().getId())));
            if (allPresent) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Expected audit actions were not persisted in time");
    }
}
