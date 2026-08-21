package com.ditec.assistencia.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Contrato de entrada do POST /api/chat — mantido IDENTICO ao que o
 * script.js do frontend ja envia (funcao askMistral), para nao precisar
 * alterar o widget do chatbot: model/messages/max_tokens/temperature/stream.
 * sessionId e' o unico campo novo (usado para agrupar o historico no
 * relatorio do chatbot do admin — UC011/UC013 do DRS).
 */
public record ChatRequest(
        String model,
        List<ChatMessageDto> messages,
        @JsonProperty("max_tokens") Integer maxTokens,
        Double temperature,
        Boolean stream,
        String sessionId
) {
}
