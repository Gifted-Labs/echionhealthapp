package com.giftedlabs.echoinhealthbackend.service.ai;

import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class AiProviderRouter {

    private final Map<AiProviderType, AiReportProvider> providers = new EnumMap<>(AiProviderType.class);
    private final AiRoutingProperties properties;

    public AiProviderRouter(List<AiReportProvider> providerList, AiRoutingProperties properties) {
        for (AiReportProvider provider : providerList) {
            providers.put(provider.providerType(), provider);
        }
        this.properties = properties;
    }

    public AiReportProvider primaryProvider(String requestedProvider) {
        AiProviderType preferred = AiProviderType.from(requestedProvider,
                AiProviderType.from(properties.getDefaultProvider(), AiProviderType.GEMINI));
        AiReportProvider provider = providers.get(preferred);
        if (provider != null && provider.isConfigured()) {
            return provider;
        }
        return fallbackProvider(preferred);
    }

    public AiReportProvider fallbackProvider(AiProviderType primaryType) {
        if (!properties.isAllowFallback()) {
            return null;
        }
        AiProviderType configuredFallback = AiProviderType.from(properties.getFallbackProvider(), AiProviderType.OPENAI);
        if (configuredFallback == primaryType) {
            configuredFallback = primaryType == AiProviderType.GEMINI ? AiProviderType.OPENAI : AiProviderType.GEMINI;
        }
        AiReportProvider provider = providers.get(configuredFallback);
        return provider != null && provider.isConfigured() ? provider : null;
    }

    public AiProviderSettings settings(AiProviderType type) {
        return properties.settingsFor(type);
    }
}
