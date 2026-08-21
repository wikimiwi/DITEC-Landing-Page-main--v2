package com.ditec.assistencia.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cadastro de cliente (usado tanto no modal do site quanto no "completar cadastro" pos-agendamento). */
public record RegisterRequest(
        @NotBlank(message = "Informe o nome completo.") String nome,
        @NotBlank(message = "Informe o e-mail.") @Email(message = "E-mail invalido.") String email,
        @NotBlank(message = "Informe o telefone.") String telefone,
        @NotBlank(message = "Informe uma senha.") @Size(min = 6, message = "A senha deve ter ao menos 6 caracteres.") String senha
) {
}
