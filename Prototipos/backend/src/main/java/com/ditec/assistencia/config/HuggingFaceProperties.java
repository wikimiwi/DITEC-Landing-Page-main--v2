package com.ditec.assistencia.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ditec.huggingface")
public record HuggingFaceProperties(String apiKey, String baseUrl, String defaultModel) {
}
