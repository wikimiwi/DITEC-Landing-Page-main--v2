package com.ditec.assistencia.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Geracao e validacao de tokens JWT (HS256).
 *
 * O segredo (JWT_SECRET) e' obrigatorio via variavel de ambiente — a
 * aplicacao falha ao subir se ele nao existir ou for curto demais, em vez
 * de silenciosamente usar uma chave fraca/previsivel.
 */
@Component
@RequiredArgsConstructor
public class JwtUtil {

    private static final int MIN_SECRET_BYTES = 32; // HS256 exige >= 256 bits

    private final JwtProperties jwtProperties;
    private SecretKey signingKey;

    @PostConstruct
    void init() {
        String secret = jwtProperties.secret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                "JWT_SECRET nao configurado. Defina a variavel de ambiente JWT_SECRET " +
                "(minimo 32 caracteres) antes de iniciar a aplicacao. Veja .env.example.");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                "JWT_SECRET muito curto (" + bytes.length + " bytes). Use pelo menos " +
                MIN_SECRET_BYTES + " bytes/caracteres. Gere um com: openssl rand -base64 48");
        }
        this.signingKey = Keys.hmacShaKeyFor(bytes);
    }

    public String gerarToken(String email, String tipo, Long usuarioId) {
        Instant agora = Instant.now();
        Instant expiraEm = agora.plusSeconds(jwtProperties.expirationMinutes() * 60);
        return Jwts.builder()
                .subject(email)
                .claim("tipo", tipo)
                .claim("uid", usuarioId)
                .issuedAt(Date.from(agora))
                .expiration(Date.from(expiraEm))
                .signWith(signingKey)
                .compact();
    }

    public Claims validarEExtrairClaims(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extrairEmail(String token) {
        return validarEExtrairClaims(token).getSubject();
    }
}
