package com.monitoring.poc.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Powers EmailService's @Async dispatch so a slow/unreachable SMTP server
 * never blocks the calling request thread (login, metric approval, etc).
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
