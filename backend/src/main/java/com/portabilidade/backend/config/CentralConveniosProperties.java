package com.portabilidade.backend.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param defaultRetryAfter wait before asking the Central again when it does not send a {@code Retry-After}
 */
@ConfigurationProperties("central-convenios")
public record CentralConveniosProperties(String baseUrl, Duration connectTimeout, Duration readTimeout,
		Duration defaultRetryAfter) {
}
