package com.ditec.assistencia.dto.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record TecnicoRequest(
        @NotBlank(message = "Informe o nome.") String nome,
        @NotBlank(message = "Informe o e-mail.") @Email(message = "E-mail invalido.") String email,
        @NotBlank(message = "Informe o telefone.") String telefone,
        String especialidade,
        String certificacao,
        /** obrigatoria apenas na criacao; ignorada em atualizacoes */
        String senha
) {
}
