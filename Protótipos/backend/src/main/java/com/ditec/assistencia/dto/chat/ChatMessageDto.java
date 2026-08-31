package com.ditec.assistencia.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Uma mensagem no formato OpenAI/Hugging Face ({"role": "...", "content": "..."}). */
public record ChatMessageDto(
        @NotBlank(message = "role invalido.") @Pattern(regexp = "system|user|assistant", message = "role deve ser system, user ou assistant.") String role,
        @NotBlank(message = "Mensagem vazia.") @Size(max = 2000, message = "Mensagem muito longa (maximo 2000 caracteres).") String content
) {
}
