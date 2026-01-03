package com.giftedlabs.echoinhealthbackend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Configuration for async operations (email sending, audit logging)
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
