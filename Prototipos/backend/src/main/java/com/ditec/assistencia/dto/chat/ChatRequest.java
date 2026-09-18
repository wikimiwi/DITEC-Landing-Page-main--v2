package com.ditec.assistencia.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Contrato de entrada do POST /api/chat — mantido IDENTICO ao que o
 * script.js do frontend ja envia (funcao askMistral), para nao precisar
 * alterar o widget do chatbot: model/messages/max_tokens/temperature/stream.
 * sessionId e' o unico campo novo (usado para agrupar o historico no
 * relatorio do chatbot do admin — UC011/UC013 do DRS).
 *
 * DECISAO DE SEGURANCA (auditoria, secao 18): model/max_tokens/temperature
 * sao validados aqui na forma, mas o valor REALMENTE usado na chamada a'
 * Hugging Face e' sempre limitado/clampado no ChatService — nunca confiamos
 * cegamente em max_tokens vindo do cliente, pois isso poderia ser usado
 * pra forcar geracoes caras contra uma API paga por token.
 */
public record ChatRequest(
        String model,
        @NotEmpty(message = "Envie ao menos uma mensagem.")
        @Size(max = 20, message = "Historico de mensagens muito longo.")
        @Valid List<ChatMessageDto> messages,
        @JsonProperty("max_tokens") Integer maxTokens,
        Double temperature,
        Boolean stream,
        @Size(max = 100, message = "sessionId invalido.") String sessionId
) {
}
