package com.ditec.assistencia.dto.chat;

import java.util.List;

/**
 * Mesmo formato usado (a) para desserializar a resposta da Hugging Face e
 * (b) para responder ao frontend — ambos seguem o schema OpenAI-compatible
 * (data.choices[0].message.content), entao reaproveitamos o mesmo DTO.
 */
public record ChatCompletionResponse(List<ChatChoiceDto> choices) {

    public static ChatCompletionResponse deTexto(String texto) {
        return new ChatCompletionResponse(
                List.of(new ChatChoiceDto(0, new ChatMessageDto("assistant", texto), "stop")));
    }

    public String primeiroTexto() {
        if (choices == null || choices.isEmpty() || choices.get(0).message() == null) return null;
        return choices.get(0).message().content();
    }
}
