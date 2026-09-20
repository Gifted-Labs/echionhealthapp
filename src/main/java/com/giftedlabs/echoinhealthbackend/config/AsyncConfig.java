package com.giftedlabs.echoinhealthbackend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuration for async operations (email sending, audit logging) and scheduled sweeps.
 *
 * <p>Scheduling backs the email outbox retry sweep: a send rejected by the provider is re-attempted
 * rather than lost, which it previously was.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
}
