package com.portabilidade.backend.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("central-convenios")
public record CentralConveniosProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {
}
