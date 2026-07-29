package com.giftedlabs.echoinhealthbackend.service.ai;

import com.giftedlabs.echoinhealthbackend.entity.ScanType;
import lombok.Builder;

import java.util.Map;

@Builder
public record AiProviderRequest(
        String prompt,
        String promptVersion,
        ScanType scanType,
        Map<String, Object> measurements) {
}
