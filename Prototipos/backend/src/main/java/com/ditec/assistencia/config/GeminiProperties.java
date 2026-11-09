package com.ditec.assistencia.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracao da integracao com a Google Gemini API (ditec.gemini.* no application.yml,
 * alimentado pelas variaveis GEMINI_*). A chave NUNCA vai para o front-end nem para logs.
 *
 * @param apiKey          GEMINI_API_KEY (vazia = chatbot em modo de respostas automaticas)
 * @param apiUrl          GEMINI_API_URL, ex.: https://generativelanguage.googleapis.com/v1beta
 * @param model           GEMINI_MODEL, ex.: gemini-2.5-flash
 * @param fallbackModel   GEMINI_FALLBACK_MODEL: usado uma vez se o modelo principal nao existir
 *                        ou nao estiver liberado para a conta (vazio = sem alternativo)
 * @param maxOutputTokens limite de tokens da resposta
 * @param temperature     criatividade (ignorada pelo Gemini 3.6+)
 * @param thinkingLevel   GEMINI_THINKING_LEVEL para modelos Gemini 3+ (MINIMAL, LOW, MEDIUM, HIGH)
 * @param maxRetries      tentativas extras para 429/5xx (limitado a 3)
 * @param retryBackoffMs  espera inicial entre tentativas (dobra a cada tentativa, com jitter)
 * @param connectTimeoutMs tempo maximo para conectar
 * @param readTimeoutMs   tempo maximo de espera pela resposta
 */
@ConfigurationProperties(prefix = "ditec.gemini")
public record GeminiProperties(
        String apiKey,
        String apiUrl,
        String model,
        String fallbackModel,
        Integer maxOutputTokens,
        Double temperature,
        String thinkingLevel,
        Integer maxRetries,
        Long retryBackoffMs,
        Long connectTimeoutMs,
        Long readTimeoutMs
) {
    public boolean configurada() {
        return apiKey != null && !apiKey.isBlank();
    }

    public int maxRetriesSeguro() {
        return maxRetries == null ? 2 : Math.max(0, Math.min(maxRetries, 3));
    }
}
