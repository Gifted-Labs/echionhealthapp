package com.giftedlabs.echoinhealthbackend.service;

import com.giftedlabs.echoinhealthbackend.dto.vault.ScanFieldDefinitionResponse;
import com.giftedlabs.echoinhealthbackend.dto.vault.ScanTypeDefinitionResponse;
import com.giftedlabs.echoinhealthbackend.entity.ScanType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the scan-type catalogue the report editor renders from.
 *
 * <p>The client reported that selecting a scan type should produce that study's organ structure
 * <em>and</em> a place for findings belonging to no named organ. The organ structure already
 * existed; the catch-all did not. These tests keep both properties true for every scan type,
 * including ones added later.
 */
class ScanTypeDefinitionServiceTest {

    private final ScanTypeDefinitionService service = new ScanTypeDefinitionService();

    @ParameterizedTest
    @EnumSource(ScanType.class)
    @DisplayName("every scan type has a definition")
    void everyScanTypeHasADefinition(ScanType scanType) {
        assertThat(service.getDefinition(scanType))
                .as("no field definition registered for %s", scanType)
                .isNotNull();
    }

    @ParameterizedTest
    @EnumSource(ScanType.class)
    @DisplayName("every definition ends with an Other Findings section")
    void everyDefinitionEndsWithOtherFindings(ScanType scanType) {
        List<ScanFieldDefinitionResponse> sections = service.getDefinition(scanType).getSections();

        assertThat(sections).isNotEmpty();
        assertThat(sections.getLast().getKey())
                .as("%s must end with the catch-all section", scanType)
                .isEqualTo(ScanTypeDefinitionService.OTHER_FINDINGS_KEY);
        assertThat(sections.getLast().getLabel()).isEqualTo("Other Findings");
        assertThat(sections.getLast().getRequired()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(ScanType.class)
    @DisplayName("the catch-all appears exactly once per definition")
    void catchAllIsNotDuplicated(ScanType scanType) {
        long occurrences = service.getDefinition(scanType).getSections().stream()
                .filter(section -> ScanTypeDefinitionService.OTHER_FINDINGS_KEY.equals(section.getKey()))
                .count();

        assertThat(occurrences).isEqualTo(1);
    }

    @Test
    @DisplayName("organ structure is preserved alongside the catch-all")
    void organStructureSurvives() {
        List<String> thyroidKeys = service.getDefinition(ScanType.THYROID).getSections().stream()
                .map(ScanFieldDefinitionResponse::getKey)
                .toList();

        assertThat(thyroidKeys).containsExactly(
                "right_lobe", "left_lobe", "isthmus", "cervical_nodes",
                ScanTypeDefinitionService.OTHER_FINDINGS_KEY);
    }

    @Test
    @DisplayName("Doppler studies keep their measurement table and gain the catch-all")
    void dopplerKeepsMeasurementTable() {
        ScanTypeDefinitionResponse doppler = service.getDefinition(ScanType.ARTERIAL_DOPPLER_BOTH_LOWER);

        assertThat(doppler.getHasMeasurementTable()).isTrue();
        assertThat(doppler.getMeasurementColumns()).containsExactly("measurement", "finding");
        assertThat(doppler.getSections())
                .extracting(ScanFieldDefinitionResponse::getKey)
                .containsExactly("arterial_segments", "waveform", "impression",
                        ScanTypeDefinitionService.OTHER_FINDINGS_KEY);
        assertThat(doppler.getSections().getFirst().getMeasurementLabels())
                .containsExactly("PSV", "EDV", "RI");
    }

    @Test
    @DisplayName("the whole catalogue is exposed")
    void allDefinitionsAreListed() {
        assertThat(service.getAllDefinitions()).hasSize(ScanType.values().length);
    }
}
