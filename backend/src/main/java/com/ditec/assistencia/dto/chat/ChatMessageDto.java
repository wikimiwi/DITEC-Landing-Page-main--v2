package com.ditec.assistencia.dto.chat;

/** Uma mensagem no formato OpenAI/Hugging Face ({"role": "...", "content": "..."}). */
public record ChatMessageDto(String role, String content) {
}
