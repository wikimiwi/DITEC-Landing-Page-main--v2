package com.ditec.assistencia.dto.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TecnicoRequest(
        @NotBlank(message = "Informe o nome.") @Size(max = 150, message = "Nome muito longo.") String nome,
        @NotBlank(message = "Informe o e-mail.") @Email(message = "E-mail invalido.") @Size(max = 150, message = "E-mail muito longo.") String email,
        @NotBlank(message = "Informe o telefone.") @Size(max = 20, message = "Telefone muito longo.") String telefone,
        @Size(max = 100, message = "Especialidade muito longa.") String especialidade,
        @Size(max = 100, message = "Certificacao muito longa.") String certificacao,
        /** obrigatoria apenas na criacao; ignorada em atualizacoes */
        @Size(min = 8, max = 100, message = "A senha deve ter entre 8 e 100 caracteres.") String senha
) {
}
