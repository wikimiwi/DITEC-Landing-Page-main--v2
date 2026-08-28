package com.ditec.assistencia.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cadastro de cliente (usado tanto no modal do site quanto no "completar cadastro" pos-agendamento). */
public record RegisterRequest(
        @NotBlank(message = "Informe o nome completo.") @Size(max = 150, message = "Nome muito longo.") String nome,
        @NotBlank(message = "Informe o e-mail.") @Email(message = "E-mail invalido.") @Size(max = 150, message = "E-mail muito longo.") String email,
        @NotBlank(message = "Informe o telefone.") @Size(max = 20, message = "Telefone muito longo.") String telefone,
        @NotBlank(message = "Informe uma senha.") @Size(min = 8, max = 100, message = "A senha deve ter entre 8 e 100 caracteres.") String senha
) {
}
