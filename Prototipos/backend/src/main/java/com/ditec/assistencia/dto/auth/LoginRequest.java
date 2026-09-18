package com.ditec.assistencia.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Informe o e-mail.") @Size(max = 150, message = "E-mail invalido.") String email,
        @NotBlank(message = "Informe a senha.") @Size(max = 100, message = "Senha invalida.") String senha
) {
}
