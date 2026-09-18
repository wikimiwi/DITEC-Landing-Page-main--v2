package com.ditec.assistencia.dto.admin;

public record TecnicoResponse(Long id, String nome, String email, String telefone,
                               String especialidade, String certificacao, String status) {
}
