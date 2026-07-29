package com.giftedlabs.echoinhealthbackend.controller;

import com.giftedlabs.echoinhealthbackend.dto.common.ApiResponse;
import com.giftedlabs.echoinhealthbackend.dto.vault.ScanTypeDefinitionResponse;
import com.giftedlabs.echoinhealthbackend.entity.ScanType;
import com.giftedlabs.echoinhealthbackend.exception.ResourceNotFoundException;
import com.giftedlabs.echoinhealthbackend.service.ScanTypeDefinitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/vault/scan-types")
@RequiredArgsConstructor
@Tag(name = "Scan Types", description = "Structured scan type definitions")
@PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'SONOGRAPHER', 'RADIOLOGIST', 'PHYSICIAN', 'ADMIN', 'SUPER_ADMIN')")
public class ScanTypeController {

    private final ScanTypeDefinitionService scanTypeDefinitionService;

    @GetMapping
    @Operation(summary = "List scan types", description = "Get all supported scan types and their field definitions")
    public ResponseEntity<ApiResponse<List<ScanTypeDefinitionResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(scanTypeDefinitionService.getAllDefinitions()));
    }

    @GetMapping("/{scanType}")
    @Operation(summary = "Get scan type definition", description = "Get field definitions for a specific scan type")
    public ResponseEntity<ApiResponse<ScanTypeDefinitionResponse>> getOne(@PathVariable ScanType scanType) {
        ScanTypeDefinitionResponse definition = scanTypeDefinitionService.getDefinition(scanType);
        if (definition == null) {
            throw new ResourceNotFoundException("Scan type definition not found");
        }
        return ResponseEntity.ok(ApiResponse.success(definition));
    }
}
