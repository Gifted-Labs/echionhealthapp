package com.giftedlabs.echoinhealthbackend.service.ai;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class AiUsageCostEstimator {

    public BigDecimal estimate(AiProviderSettings settings, Integer inputTokens, Integer outputTokens) {
        if (settings == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal input = prorate(inputTokens, settings.inputCostPerMillion());
        BigDecimal output = prorate(outputTokens, settings.outputCostPerMillion());
        return input.add(output).setScale(6, RoundingMode.HALF_UP);
    }

    private BigDecimal prorate(Integer tokens, BigDecimal ratePerMillion) {
        if (tokens == null || ratePerMillion == null || BigDecimal.ZERO.compareTo(ratePerMillion) == 0) {
            return BigDecimal.ZERO;
        }
        return ratePerMillion.multiply(BigDecimal.valueOf(tokens))
                .divide(BigDecimal.valueOf(1_000_000L), 6, RoundingMode.HALF_UP);
    }
}
