package com.ditec.assistencia.dto.cliente;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** E-mail nao entra aqui: e' a chave de login e nao e' editavel (mesma regra do front antigo). */
public record ClienteUpdateRequest(
        @NotBlank(message = "Informe o nome completo.") @Size(max = 150, message = "Nome muito longo.") String nome,
        @NotBlank(message = "Informe o telefone.") @Size(max = 20, message = "Telefone muito longo.") String telefone,
        @Size(max = 255, message = "Endereco muito longo.") String endereco,
        @Size(max = 100, message = "Bairro muito longo.") String bairro,
        @Size(max = 100, message = "Cidade muito longa.") String cidade,
        @Size(max = 2, message = "UF deve ter 2 letras.") String uf,
        @Size(max = 9, message = "CEP invalido.") String cep,
        @Size(max = 100, message = "Complemento muito longo.") String complemento
) {
}
