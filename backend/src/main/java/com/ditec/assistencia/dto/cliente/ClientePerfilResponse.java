package com.ditec.assistencia.dto.cliente;

public record ClientePerfilResponse(
        Long id,
        String nome,
        String email,
        String telefone,
        String endereco,
        String bairro,
        String cidade,
        String uf,
        String cep,
        String complemento,
        String clienteDesde
) {
}
