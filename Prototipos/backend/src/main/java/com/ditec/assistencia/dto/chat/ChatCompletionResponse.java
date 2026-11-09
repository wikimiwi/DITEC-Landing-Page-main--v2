package com.ditec.assistencia.dto.chat;

import java.util.List;

/**
 * Formato da resposta ao frontend (padrao "OpenAI-compatible" que o script.js ja le). Antes tambem
 * (b) para responder ao frontend — ambos seguem o schema OpenAI-compatible
 * (data.choices[0].message.content).
 *
 * "fonte" (so' na resposta ao frontend): "ia" quando o texto veio do modelo,
 * "automatica" quando a IA estava indisponivel e foi usado o FAQ oficial.
 */
public record ChatCompletionResponse(List<ChatChoiceDto> choices, String fonte) {

    public static ChatCompletionResponse deTexto(String texto, String fonte) {
        return new ChatCompletionResponse(
                List.of(new ChatChoiceDto(0, new ChatMessageDto("assistant", texto), "stop")), fonte);
    }

    public String primeiroTexto() {
        if (choices == null || choices.isEmpty() || choices.get(0).message() == null) return null;
        return choices.get(0).message().content();
    }
}
