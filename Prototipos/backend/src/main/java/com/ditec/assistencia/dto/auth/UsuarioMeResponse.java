package com.ditec.assistencia.dto.auth;

public record UsuarioMeResponse(Long id, String nome, String email, String telefone, String tipo, String desde) {
}
