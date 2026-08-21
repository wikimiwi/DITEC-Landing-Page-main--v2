package com.ditec.assistencia.dto.cliente;

import jakarta.validation.constraints.NotBlank;

/** E-mail nao entra aqui: e' a chave de login e nao e' editavel (mesma regra do front antigo). */
public record ClienteUpdateRequest(
        @NotBlank(message = "Informe o nome completo.") String nome,
        @NotBlank(message = "Informe o telefone.") String telefone,
        String endereco,
        String bairro,
        String cidade,
        String uf,
        String cep,
        String complemento
) {
}
