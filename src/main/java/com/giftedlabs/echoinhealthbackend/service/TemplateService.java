package com.giftedlabs.echoinhealthbackend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.giftedlabs.echoinhealthbackend.dto.vault.*;
import com.giftedlabs.echoinhealthbackend.entity.ReportTemplate;
import com.giftedlabs.echoinhealthbackend.entity.SharedTemplate;
import com.giftedlabs.echoinhealthbackend.entity.TemplateVersion;
import com.giftedlabs.echoinhealthbackend.entity.User;
import com.giftedlabs.echoinhealthbackend.exception.ResourceNotFoundException;
import com.giftedlabs.echoinhealthbackend.exception.SharedTemplateDeletionConflictException;
import com.giftedlabs.echoinhealthbackend.repository.ReportTemplateRepository;
import com.giftedlabs.echoinhealthbackend.repository.SharedTemplateRepository;
import com.giftedlabs.echoinhealthbackend.repository.TemplateVersionRepository;
import com.giftedlabs.echoinhealthbackend.repository.UserRepository;
import com.giftedlabs.echoinhealthbackend.util.TextExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.giftedlabs.echoinhealthbackend.util.CacheNames.TEMPLATES;

@Service
@RequiredArgsConstructor
@Slf4j
public class TemplateService {

    private final ReportTemplateRepository templateRepository;
    private final SharedTemplateRepository sharedTemplateRepository;
    private final TemplateVersionRepository templateVersionRepository;
    private final UserRepository userRepository;
    private final TextExtractor textExtractor;
    private final PhiStrippingService phiStrippingService;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;
    private final BillingService billingService;
    private final FileValidationService fileValidationService;
    private final AuditService auditService;

    @Transactional
    @CacheEvict(value = TEMPLATES, key = "#userId")
    public TemplateResponse createTemplate(CreateTemplateRequest request, String userId) {
        User user = requireUser(userId);

        ReportTemplate template = ReportTemplate.builder()
                .organization(user.getOrganization())
                .user(user)
                .name(stripPhi(request.getName()))
                .description(stripPhi(request.getDescription()))
                .gender(request.getGender())
                .reportType(request.getReportType())
                .scanType(request.getScanType())
                .category(stripPhi(request.getCategory()))
                .defaultFindings(stripPhi(request.getDefaultFindings()))
                .defaultImpression(stripPhi(request.getDefaultImpression()))
                .isDefault(Boolean.TRUE.equals(request.getIsDefault()))
                .isActive(true)
                .isFavorite(false)
                .sourceFormat("GENERATED")
                .tags(stripPhi(request.getTags()))
                .usageCount(0)
                .build();
        template.setPhiFree(isPhiFree(template));

        if ((template.getCategory() == null || template.getCategory().isBlank()) && template.getScanType() != null) {
            template.setCategory(toCategory(template.getScanType().name()));
        }

        enforceSingleDefault(user, template.getReportType(), template.getScanType(), template.getId());
        ReportTemplate saved = templateRepository.save(template);
        createVersionSnapshot(saved, user, "Template created");
        return mapToResponse(saved);
    }

    @Transactional
    @CacheEvict(value = TEMPLATES, key = "#userId")
    public TemplateResponse uploadTemplate(MultipartFile file, String name, String scanTypeStr, String userId) {
        User user = requireUser(userId);
        fileValidationService.requireAllowedContentType(
                file,
                java.util.Set.of(
                        "application/pdf",
                        "application/msword",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "text/plain"),
                "Template must be PDF, DOC, DOCX, or TXT");
        String extractedText = textExtractor.extractText(file);
        if (extractedText == null || extractedText.isBlank()) {
            throw new IllegalArgumentException("Could not extract text from the uploaded file");
        }

        billingService.assertStorageCapacity(user.getOrganization(), file.getSize());
        String strippedText = stripPhi(extractedText);
        String filePath = fileStorageService.storeFile(file, user.getOrganizationId(), userId);
        var scanType = parseScanType(scanTypeStr);
        String originalFilename = stripPhi(file.getOriginalFilename());

        ReportTemplate template = ReportTemplate.builder()
                .organization(user.getOrganization())
                .user(user)
                .name(stripPhi(name != null && !name.isBlank() ? name : originalFilename))
                .description("Uploaded from: " + originalFilename)
                .scanType(scanType)
                .category(scanType != null ? toCategory(scanType.name()) : null)
                .defaultFindings(strippedText)
                .defaultImpression(null)
                .originalFilename(originalFilename)
                .filePath(filePath)
                .fileSize(file.getSize())
                .isActive(true)
                .isFavorite(false)
                .sourceFormat(detectSourceFormat(file.getOriginalFilename()))
                .usageCount(0)
                .build();
        template.setPhiFree(isPhiFree(template));

        ReportTemplate saved = templateRepository.save(template);
        createVersionSnapshot(saved, user, "Template uploaded");
        return mapToResponse(saved);
    }

    @Transactional
    @CacheEvict(value = TEMPLATES, key = "#userId")
    public TemplateBulkUploadResponse bulkUploadTemplates(List<MultipartFile> files, String userId) {
        List<TemplateBulkUploadResponse.TemplateBulkResult> results = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;

        for (MultipartFile file : files) {
            try {
                TemplateResponse response = uploadTemplate(file, null, null, userId);
                results.add(successResult(file.getOriginalFilename(), response.getId()));
                successCount++;
            } catch (Exception e) {
                results.add(failureResult(file.getOriginalFilename(), e));
                failureCount++;
            }
        }

        return bulkResponse(files.size(), successCount, failureCount, results);
    }

    @Transactional
    @CacheEvict(value = TEMPLATES, key = "#userId")
    public TemplateBulkUploadResponse importTemplatesFromJson(MultipartFile jsonFile, String userId) {
        User user = requireUser(userId);
        List<TemplateBulkUploadResponse.TemplateBulkResult> results = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;

        try {
            List<Map<String, Object>> entries = objectMapper.readValue(
                    jsonFile.getInputStream(),
                    new TypeReference<List<Map<String, Object>>>() { });

            for (Map<String, Object> entry : entries) {
                try {
                    ReportTemplate saved = templateRepository.save(buildImportedTemplate(user, entry, "JSON"));
                    createVersionSnapshot(saved, user, "Template imported from JSON");
                    results.add(successResult(asString(entry.get("name"), "Imported Template"), saved.getId()));
                    successCount++;
                } catch (Exception e) {
                    results.add(failureResult(asString(entry.get("name"), "Imported Template"), e));
                    failureCount++;
                }
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON format: " + e.getMessage(), e);
        }

        return bulkResponse(successCount + failureCount, successCount, failureCount, results);
    }

    @Transactional
    @CacheEvict(value = TEMPLATES, key = "#userId")
    public TemplateBulkUploadResponse importTemplatesFromCsv(MultipartFile csvFile, String userId) {
        User user = requireUser(userId);
        List<TemplateBulkUploadResponse.TemplateBulkResult> results = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(csvFile.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IllegalArgumentException("CSV file is empty");
            }

            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    String[] parts = line.split(",", -1);
                    ReportTemplate template = ReportTemplate.builder()
                            .organization(user.getOrganization())
                            .user(user)
                            .name(stripPhi(parts.length > 0 ? parts[0].trim() : "Imported Template"))
                            .description(stripPhi(parts.length > 1 ? parts[1].trim() : ""))
                            .defaultFindings(stripPhi(parts.length > 2 ? parts[2].trim() : ""))
                            .defaultImpression(stripPhi(parts.length > 3 ? parts[3].trim() : ""))
                            .scanType(parseScanType(parts.length > 4 ? parts[4].trim() : null))
                            .category(stripPhi(parts.length > 5 ? parts[5].trim() : ""))
                            .tags(stripPhi(parseTags(parts.length > 6 ? parts[6] : null)))
                            .isActive(true)
                            .isFavorite(false)
                            .sourceFormat("CSV")
                            .usageCount(0)
                            .build();
                    template.setPhiFree(isPhiFree(template));

                    if (template.getCategory() == null && template.getScanType() != null) {
                        template.setCategory(toCategory(template.getScanType().name()));
                    }

                    ReportTemplate saved = templateRepository.save(template);
                    createVersionSnapshot(saved, user, "Template imported from CSV");
                    results.add(successResult(template.getName(), saved.getId()));
                    successCount++;
                } catch (Exception e) {
                    results.add(failureResult(line.length() > 30 ? line.substring(0, 30) : line, e));
                    failureCount++;
                }
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to read CSV: " + e.getMessage(), e);
        }

        return bulkResponse(successCount + failureCount, successCount, failureCount, results);
    }

    @Transactional
    @CacheEvict(value = TEMPLATES, key = "#userId")
    public TemplateResponse duplicateTemplate(String templateId, String userId, String newName) {
        User user = requireUser(userId);
        ReportTemplate original = findOwnedOrSharedTemplate(templateId, userId, user.getOrganizationId());

        ReportTemplate duplicate = ReportTemplate.builder()
                .organization(user.getOrganization())
                .user(user)
                .name(stripPhi(newName != null && !newName.isBlank() ? newName : original.getName() + " (Copy)"))
                .description(original.getDescription())
                .gender(original.getGender())
                .reportType(original.getReportType())
                .scanType(original.getScanType())
                .category(original.getCategory())
                .defaultFindings(original.getDefaultFindings())
                .defaultImpression(original.getDefaultImpression())
                .tags(original.getTags())
                .isDefault(false)
                .isActive(true)
                .isFavorite(false)
                .sourceFormat(original.getSourceFormat())
                .usageCount(0)
                .build();
        duplicate.setPhiFree(isPhiFree(duplicate));

        ReportTemplate saved = templateRepository.save(duplicate);
        createVersionSnapshot(saved, user, "Template duplicated");
        return mapToResponse(saved);
    }

    @Transactional
    @CacheEvict(value = TEMPLATES, key = "#userId")
    public TemplateResponse updateTemplate(String templateId, UpdateTemplateRequest request, String userId) {
        User user = requireUser(userId);
        ReportTemplate template = templateRepository.findByIdAndUserIdAndOrganizationId(
                        templateId,
                        userId,
                        user.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Template not found"));

        if (request.getName() != null) {
            template.setName(stripPhi(request.getName()));
        }
        if (request.getDescription() != null) {
            template.setDescription(stripPhi(request.getDescription()));
        }
        if (request.getGender() != null) {
            template.setGender(request.getGender());
        }
        if (request.getReportType() != null) {
            template.setReportType(request.getReportType());
        }
        if (request.getScanType() != null) {
            template.setScanType(request.getScanType());
        }
        if (request.getCategory() != null) {
            template.setCategory(stripPhi(request.getCategory()));
        }
        if (request.getDefaultFindings() != null) {
            template.setDefaultFindings(stripPhi(request.getDefaultFindings()));
        }
        if (request.getDefaultImpression() != null) {
            template.setDefaultImpression(stripPhi(request.getDefaultImpression()));
        }
        if (request.getTags() != null) {
            template.setTags(stripPhi(request.getTags()));
        }
        if (request.getIsDefault() != null) {
            if (request.getIsDefault()) {
                enforceSingleDefault(user, template.getReportType(), template.getScanType(), templateId);
            }
            template.setIsDefault(request.getIsDefault());
        }

        template.setPhiFree(isPhiFree(template));
        if ((template.getCategory() == null || template.getCategory().isBlank()) && template.getScanType() != null) {
            template.setCategory(toCategory(template.getScanType().name()));
        }

        ReportTemplate saved = templateRepository.save(template);
        createVersionSnapshot(saved, user, "Template updated");
        return mapToResponse(saved);
    }

    @Transactional
    public void shareTemplate(String templateId, List<String> recipientUserIds, String userId) {
        User owner = requireUser(userId);
        ReportTemplate template = templateRepository.findByIdAndUserIdAndOrganizationId(
                        templateId,
                        userId,
                        owner.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Template not found or not owned by you"));

        for (String recipientId : recipientUserIds) {
            if (recipientId.equals(userId)) {
                continue;
            }
            User recipient = userRepository.findByIdAndOrganizationId(recipientId, owner.getOrganizationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Recipient not found: " + recipientId));
            if (sharedTemplateRepository.existsByTemplateIdAndRecipientIdAndOrganizationId(
                    templateId,
                    recipientId,
                    owner.getOrganizationId())) {
                continue;
            }

            sharedTemplateRepository.save(SharedTemplate.builder()
                    .organization(owner.getOrganization())
                    .template(template)
                    .owner(owner)
                    .recipient(recipient)
                    .build());
        }
    }

    @Transactional
    public void recordTemplateUsage(String templateId, String userId) {
        User user = requireUser(userId);
        ReportTemplate template = findOwnedOrSharedTemplate(templateId, userId, user.getOrganizationId());
        template.recordUsage();
        templateRepository.save(template);
    }

    @Transactional
    @CacheEvict(value = TEMPLATES, key = "#userId")
    public TemplateResponse setFavorite(String templateId, boolean favorite, String userId) {
        User user = requireUser(userId);
        ReportTemplate template = templateRepository.findByIdAndUserIdAndOrganizationId(
                        templateId,
                        userId,
                        user.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Template not found"));
        template.setIsFavorite(favorite);
        ReportTemplate saved = templateRepository.save(template);
        createVersionSnapshot(saved, user, favorite ? "Template marked favorite" : "Template unmarked favorite");
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = TEMPLATES, key = "#userId")
    public List<TemplateResponse> getAllTemplates(String userId) {
        User user = requireUser(userId);
        List<TemplateResponse> templates = templateRepository
                .findAllAvailableTemplates(userId, user.getOrganizationId()).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toCollection(ArrayList::new));

        for (SharedTemplate sharedTemplate : sharedTemplateRepository.findByRecipientIdAndOrganizationId(userId, user.getOrganizationId())) {
            TemplateResponse response = mapToResponse(sharedTemplate.getTemplate());
            if (templates.stream().noneMatch(t -> Objects.equals(t.getId(), response.getId()))) {
                templates.add(response);
            }
        }

        return templates;
    }

    @Transactional(readOnly = true)
    public Page<TemplateResponse> searchTemplates(SearchTemplatesRequest request, String userId, Pageable pageable) {
        User user = requireUser(userId);
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                resolveSort(request));

        return templateRepository.searchTemplates(
                        userId,
                        user.getOrganizationId(),
                        normalize(request.getKeyword()),
                        request.getScanType() != null ? request.getScanType().name() : null,
                        request.getReportType() != null ? request.getReportType().name() : null,
                        normalize(request.getCategory()),
                        normalize(request.getTag()),
                        Boolean.TRUE.equals(request.getFavoritesOnly()),
                        request.getCreatedAfter(),
                        request.getCreatedBefore(),
                        sorted)
                .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public List<TemplateResponse> getFavoriteTemplates(String userId) {
        return getAllTemplates(userId).stream()
                .filter(template -> Objects.equals(template.getUserId(), userId))
                .filter(template -> Boolean.TRUE.equals(template.getIsFavorite()))
                .sorted(Comparator.comparing(TemplateResponse::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TemplateResponse> getRecentTemplates(String userId) {
        return getAllTemplates(userId).stream()
                .filter(template -> template.getLastUsedAt() != null)
                .sorted(Comparator.comparing(TemplateResponse::getLastUsedAt, Comparator.reverseOrder()))
                .limit(10)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TemplateAnalyticsResponse> getTemplateAnalytics(String userId) {
        return getAllTemplates(userId).stream()
                .sorted(Comparator.comparing(TemplateResponse::getUsageCount, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(template -> TemplateAnalyticsResponse.builder()
                        .templateId(template.getId())
                        .templateName(template.getName())
                        .usageCount(template.getUsageCount() != null ? template.getUsageCount() : 0)
                        .lastUsedAt(template.getLastUsedAt())
                        .createdAt(template.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<TemplateVersionResponse> getVersionHistory(String templateId, String userId, Pageable pageable) {
        User user = requireUser(userId);
        findOwnedOrSharedTemplate(templateId, userId, user.getOrganizationId());
        return templateVersionRepository.findByTemplateIdAndOrganizationIdOrderByVersionNumberDesc(
                        templateId,
                        user.getOrganizationId(),
                        pageable)
                .map(this::mapToVersionResponse);
    }

    @Transactional
    @CacheEvict(value = TEMPLATES, key = "#userId")
    public TemplateResponse restoreVersion(String templateId, Integer versionNumber, String userId) {
        User user = requireUser(userId);
        ReportTemplate template = templateRepository.findByIdAndUserIdAndOrganizationId(
                        templateId,
                        userId,
                        user.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Template not found"));

        TemplateVersion version = templateVersionRepository.findByTemplateIdAndOrganizationIdAndVersionNumber(
                        templateId,
                        user.getOrganizationId(),
                        versionNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Template version not found"));

        applySnapshot(template, version.getSnapshotJson());
        template.setPhiFree(isPhiFree(template));
        if (Boolean.TRUE.equals(template.getIsDefault())) {
            enforceSingleDefault(user, template.getReportType(), template.getScanType(), template.getId());
        }
        ReportTemplate saved = templateRepository.save(template);
        createVersionSnapshot(saved, user, "Template restored to version " + versionNumber);
        return mapToResponse(saved);
    }

    @Transactional
    @CacheEvict(value = TEMPLATES, key = "#userId")
    public void deleteTemplate(String templateId, String userId, boolean cascadeSharedAccess) {
        User user = requireUser(userId);
        ReportTemplate template = templateRepository.findByIdAndUserIdAndOrganizationId(
                        templateId,
                        userId,
                        user.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Template not found"));

        long sharedCount = sharedTemplateRepository.countByTemplateIdAndOrganizationId(templateId, user.getOrganizationId());
        if (sharedCount > 0 && !cascadeSharedAccess) {
            throw new SharedTemplateDeletionConflictException(template.getName(), sharedCount);
        }

        createVersionSnapshot(template, user, "Template deleted");
        template.setIsActive(false);
        // Release the blob so the tenant gets its storage quota back. If the delete fails the
        // flag stays false and the bytes keep counting, which is the honest accounting.
        if (template.getFilePath() != null && !Boolean.TRUE.equals(template.getBlobDeleted())) {
            template.setBlobDeleted(fileStorageService.deleteFile(template.getFilePath()));
        }
        templateRepository.save(template);
        if (sharedCount > 0) {
            sharedTemplateRepository.deleteByTemplateId(templateId);
            auditService.logAction(user, "template_deleted_with_share_cascade",
                    "Deleted template '" + template.getName() + "' and removed " + sharedCount + " shared access record(s)");
            return;
        }

        auditService.logAction(user, "template_deleted", "Deleted template '" + template.getName() + "'");
    }

    @Transactional(readOnly = true)
    public ReportTemplate getAccessibleTemplate(String templateId, String userId) {
        User user = requireUser(userId);
        return findOwnedOrSharedTemplate(templateId, userId, user.getOrganizationId());
    }

    private User requireUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private void enforceSingleDefault(User user, com.giftedlabs.echoinhealthbackend.entity.ReportType reportType,
                                      com.giftedlabs.echoinhealthbackend.entity.ScanType scanType, String templateIdToExclude) {
        templateRepository.findDefaultTemplate(user.getId(), user.getOrganizationId(), reportType, scanType)
                .ifPresent(existing -> {
                    if (templateIdToExclude == null || !existing.getId().equals(templateIdToExclude)) {
                        existing.setIsDefault(false);
                        templateRepository.save(existing);
                    }
                });
    }

    private ReportTemplate buildImportedTemplate(User user, Map<String, Object> entry, String sourceFormat) {
        var scanType = parseScanType(asString(entry.get("scanType"), null));
        String[] tags = parseTags(asString(entry.get("tags"), null));
        ReportTemplate template = ReportTemplate.builder()
                .organization(user.getOrganization())
                .user(user)
                .name(stripPhi(asString(entry.get("name"), "Imported Template")))
                .description(stripPhi(asString(entry.get("description"), "")))
                .defaultFindings(stripPhi(asString(entry.get("defaultFindings"), "")))
                .defaultImpression(stripPhi(asString(entry.get("defaultImpression"), "")))
                .scanType(scanType)
                .category(stripPhi(asString(entry.get("category"), scanType != null ? toCategory(scanType.name()) : null)))
                .tags(stripPhi(tags))
                .isActive(true)
                .isFavorite(false)
                .sourceFormat(sourceFormat)
                .usageCount(0)
                .build();
        template.setPhiFree(isPhiFree(template));
        return template;
    }

    private ReportTemplate findOwnedOrSharedTemplate(String templateId, String userId, String organizationId) {
        return templateRepository.findByIdAndUserIdAndOrganizationId(templateId, userId, organizationId)
                .orElseGet(() -> {
                    if (sharedTemplateRepository.existsByTemplateIdAndRecipientIdAndOrganizationId(templateId, userId, organizationId)) {
                        return templateRepository.findById(templateId)
                                .filter(template -> Boolean.TRUE.equals(template.getIsActive()))
                                .orElseThrow(() -> new ResourceNotFoundException("Template not found"));
                    }
                    throw new ResourceNotFoundException("Template not found");
                });
    }

    private void createVersionSnapshot(ReportTemplate template, User changedBy, String changeDescription) {
        try {
            templateVersionRepository.save(TemplateVersion.builder()
                    .organization(template.getOrganization())
                    .template(template)
                    .changedBy(changedBy)
                    .versionNumber(templateVersionRepository.findMaxVersionNumber(template.getId()) + 1)
                    .snapshotJson(objectMapper.writeValueAsString(toSnapshot(template)))
                    .changeDescription(changeDescription)
                    .build());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to create template version snapshot", e);
        }
    }

    private Map<String, Object> toSnapshot(ReportTemplate template) {
        return Map.ofEntries(
                Map.entry("name", template.getName()),
                Map.entry("description", nullSafe(template.getDescription())),
                Map.entry("gender", template.getGender() != null ? template.getGender().name() : ""),
                Map.entry("reportType", template.getReportType() != null ? template.getReportType().name() : ""),
                Map.entry("scanType", template.getScanType() != null ? template.getScanType().name() : ""),
                Map.entry("category", nullSafe(template.getCategory())),
                Map.entry("defaultFindings", nullSafe(template.getDefaultFindings())),
                Map.entry("defaultImpression", nullSafe(template.getDefaultImpression())),
                Map.entry("isDefault", Boolean.TRUE.equals(template.getIsDefault())),
                Map.entry("isFavorite", Boolean.TRUE.equals(template.getIsFavorite())),
                Map.entry("sourceFormat", nullSafe(template.getSourceFormat())),
                Map.entry("tags", template.getTags() == null ? List.of() : Arrays.asList(template.getTags())),
                Map.entry("originalFilename", nullSafe(template.getOriginalFilename())),
                Map.entry("filePath", nullSafe(template.getFilePath())),
                Map.entry("fileSize", template.getFileSize() == null ? 0L : template.getFileSize()),
                Map.entry("phiFree", Boolean.TRUE.equals(template.getPhiFree())),
                Map.entry("isActive", Boolean.TRUE.equals(template.getIsActive())),
                Map.entry("usageCount", template.getUsageCount() == null ? 0 : template.getUsageCount())
        );
    }

    private void applySnapshot(ReportTemplate template, String snapshotJson) {
        try {
            Map<String, Object> snapshot = objectMapper.readValue(snapshotJson, new TypeReference<Map<String, Object>>() { });
            template.setName(stripPhi(asString(snapshot.get("name"), template.getName())));
            template.setDescription(stripPhi(asString(snapshot.get("description"), template.getDescription())));
            template.setGender(parseEnum(snapshot.get("gender"), com.giftedlabs.echoinhealthbackend.entity.Gender.class));
            template.setReportType(parseEnum(snapshot.get("reportType"), com.giftedlabs.echoinhealthbackend.entity.ReportType.class));
            template.setScanType(parseEnum(snapshot.get("scanType"), com.giftedlabs.echoinhealthbackend.entity.ScanType.class));
            template.setCategory(stripPhi(asString(snapshot.get("category"), template.getCategory())));
            template.setDefaultFindings(stripPhi(asString(snapshot.get("defaultFindings"), template.getDefaultFindings())));
            template.setDefaultImpression(stripPhi(asString(snapshot.get("defaultImpression"), template.getDefaultImpression())));
            template.setIsDefault(Boolean.parseBoolean(asString(snapshot.get("isDefault"), "false")));
            template.setIsFavorite(Boolean.parseBoolean(asString(snapshot.get("isFavorite"), "false")));
            template.setSourceFormat(asString(snapshot.get("sourceFormat"), template.getSourceFormat()));
            template.setOriginalFilename(stripPhi(asString(snapshot.get("originalFilename"), template.getOriginalFilename())));
            template.setFilePath(asString(snapshot.get("filePath"), template.getFilePath()));
            template.setFileSize(Long.parseLong(asString(snapshot.get("fileSize"), "0")));
            template.setPhiFree(Boolean.parseBoolean(asString(snapshot.get("phiFree"), "true")));
            template.setIsActive(Boolean.parseBoolean(asString(snapshot.get("isActive"), "true")));
            template.setUsageCount(Integer.parseInt(asString(snapshot.get("usageCount"), "0")));
            Object tags = snapshot.get("tags");
            if (tags instanceof List<?> tagList) {
                template.setTags(stripPhi(tagList.stream().map(String::valueOf).toArray(String[]::new)));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to restore template version", e);
        }
    }

    private TemplateResponse mapToResponse(ReportTemplate template) {
        return TemplateResponse.builder()
                .id(template.getId())
                .userId(template.getUser() != null ? template.getUser().getId() : null)
                .name(template.getName())
                .description(template.getDescription())
                .gender(template.getGender())
                .reportType(template.getReportType())
                .scanType(template.getScanType())
                .category(template.getCategory())
                .defaultFindings(template.getDefaultFindings())
                .defaultImpression(template.getDefaultImpression())
                .isDefault(template.getIsDefault())
                .isActive(template.getIsActive())
                .phiFree(template.getPhiFree())
                .isFavorite(template.getIsFavorite())
                .sourceFormat(template.getSourceFormat())
                .tags(template.getTags())
                .originalFilename(template.getOriginalFilename())
                .fileSize(template.getFileSize())
                .usageCount(template.getUsageCount())
                .lastUsedAt(template.getLastUsedAt())
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .build();
    }

    private TemplateVersionResponse mapToVersionResponse(TemplateVersion version) {
        return TemplateVersionResponse.builder()
                .id(version.getId())
                .templateId(version.getTemplate().getId())
                .versionNumber(version.getVersionNumber())
                .changeDescription(version.getChangeDescription())
                .changedByName(version.getChangedBy() != null ? version.getChangedBy().getFullName() : null)
                .changedByEmail(version.getChangedBy() != null ? version.getChangedBy().getEmail() : null)
                .createdAt(version.getCreatedAt())
                .build();
    }

    /**
     * Maps the API's sort vocabulary onto real columns so ordering happens in the database.
     * NULLS LAST keeps never-used templates at the bottom of "most used"/"recently used"
     * views, matching what the in-memory comparator did.
     */
    private Sort resolveSort(SearchTemplatesRequest request) {
        String sortBy = normalize(request.getSortBy());
        Sort.Direction direction = "asc".equalsIgnoreCase(request.getSortDirection())
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        String column = switch (sortBy == null ? "updatedat" : sortBy.toLowerCase(java.util.Locale.ROOT)) {
            case "name", "alphabetical" -> "name";
            case "createdat", "date" -> "created_at";
            case "mostused", "usagecount" -> "usage_count";
            case "lastusedat", "recent" -> "last_used_at";
            default -> "updated_at";
        };

        return Sort.by(direction == Sort.Direction.ASC
                ? Sort.Order.asc(column).nullsLast()
                : Sort.Order.desc(column).nullsLast());
    }

    private boolean matchesKeyword(TemplateResponse template, String keyword) {
        return contains(normalize(template.getName()), keyword)
                || contains(normalize(template.getDescription()), keyword)
                || contains(normalize(template.getCategory()), keyword)
                || contains(normalize(template.getDefaultFindings()), keyword)
                || contains(normalize(template.getDefaultImpression()), keyword)
                || Arrays.stream(template.getTags() == null ? new String[0] : template.getTags())
                        .map(this::normalize)
                        .anyMatch(tag -> contains(tag, keyword));
    }

    private boolean hasTag(String[] tags, String tag) {
        return Arrays.stream(tags == null ? new String[0] : tags)
                .map(this::normalize)
                .anyMatch(existing -> contains(existing, tag));
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.toLowerCase(Locale.ROOT);
    }

    private boolean contains(String value, String expected) {
        return value != null && expected != null && value.contains(expected);
    }

    private String stripPhi(String value) {
        return value == null ? null : phiStrippingService.stripPhi(value).trim();
    }

    private String[] stripPhi(String[] values) {
        if (values == null) {
            return null;
        }
        return Arrays.stream(values)
                .filter(Objects::nonNull)
                .map(this::stripPhi)
                .filter(value -> !value.isBlank())
                .toArray(String[]::new);
    }

    private boolean isPhiFree(ReportTemplate template) {
        return !containsPhi(template.getName())
                && !containsPhi(template.getDescription())
                && !containsPhi(template.getCategory())
                && !containsPhi(template.getDefaultFindings())
                && !containsPhi(template.getDefaultImpression())
                && !containsPhi(template.getOriginalFilename())
                && !containsPhi(template.getTags());
    }

    private boolean containsPhi(String value) {
        return value != null && phiStrippingService.containsPhi(value);
    }

    private boolean containsPhi(String[] values) {
        if (values == null) {
            return false;
        }
        return Arrays.stream(values)
                .filter(Objects::nonNull)
                .anyMatch(phiStrippingService::containsPhi);
    }

    private String[] parseTags(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        return Arrays.stream(rawValue.replace("[", "").replace("]", "").split("[|;,]"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toArray(String[]::new);
    }

    private <E extends Enum<E>> E parseEnum(Object rawValue, Class<E> enumType) {
        String value = asString(rawValue, null);
        if (value == null || value.isBlank()) {
            return null;
        }
        return Enum.valueOf(enumType, value);
    }

    private com.giftedlabs.echoinhealthbackend.entity.ScanType parseScanType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return com.giftedlabs.echoinhealthbackend.entity.ScanType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String detectSourceFormat(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "TXT";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toUpperCase(Locale.ROOT);
    }

    private String toCategory(String scanType) {
        return scanType == null ? null : scanType.replace('_', ' ');
    }

    private String asString(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private TemplateBulkUploadResponse.TemplateBulkResult successResult(String filename, String templateId) {
        return TemplateBulkUploadResponse.TemplateBulkResult.builder()
                .filename(stripPhi(filename))
                .success(true)
                .templateId(templateId)
                .build();
    }

    private TemplateBulkUploadResponse.TemplateBulkResult failureResult(String filename, Exception e) {
        log.error("Template import failed for {}", filename, e);
        return TemplateBulkUploadResponse.TemplateBulkResult.builder()
                .filename(stripPhi(filename))
                .success(false)
                .errorMessage(e.getMessage())
                .build();
    }

    private TemplateBulkUploadResponse bulkResponse(int total, int successCount, int failureCount,
                                                    List<TemplateBulkUploadResponse.TemplateBulkResult> results) {
        return TemplateBulkUploadResponse.builder()
                .totalFiles(total)
                .successCount(successCount)
                .failureCount(failureCount)
                .results(results)
                .build();
    }
}
