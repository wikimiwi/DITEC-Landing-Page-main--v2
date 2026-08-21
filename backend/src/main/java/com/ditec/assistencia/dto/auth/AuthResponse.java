package com.ditec.assistencia.dto.auth;

public record AuthResponse(String token, String tipo, String nome, String email, long expiraEmMinutos) {
}
