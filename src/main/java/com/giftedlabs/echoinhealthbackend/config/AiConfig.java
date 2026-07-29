package com.giftedlabs.echoinhealthbackend.config;

import com.giftedlabs.echoinhealthbackend.service.ai.AiRoutingProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AiRoutingProperties.class)
public class AiConfig {
}
