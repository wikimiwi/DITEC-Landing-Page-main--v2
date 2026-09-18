package com.ditec.assistencia.dto.admin;

public record ClienteAdminResponse(Long id, String nome, String email, String telefone,
                                    String bairro, String cidade, String clienteDesde) {
}
