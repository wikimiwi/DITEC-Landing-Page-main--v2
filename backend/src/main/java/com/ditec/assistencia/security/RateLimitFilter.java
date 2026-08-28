package com.ditec.assistencia.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ditec.assistencia.dto.common.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Rate limiting em memoria (por IP) para endpoints sensiveis a abuso —
 * secao 9 da auditoria: login, cadastro, chatbot, agendamento e avaliacao.
 *
 * DECISAO TECNICA: implementado como contador de janela fixa, sem
 * dependencia externa (Bucket4j etc.) — suficiente para uma instancia
 * unica. Em producao com multiplas instancias, isso precisaria migrar
 * para um armazenamento compartilhado (ex: Redis), pois cada instancia
 * teria seu proprio contador. Documentado no SECURITY.md.
 *
 * Desligavel via ditec.ratelimit.enabled — usado pelo perfil "test" para
 * nao interferir nos testes automatizados (que fazem varias chamadas de
 * login em sequencia e nao devem ser tratadas como forca bruta).
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    @Value("${ditec.ratelimit.enabled:true}")
    private boolean habilitado;

    private record Regra(Pattern caminho, String metodo, int maxRequisicoes, long janelaMillis) {
    }

    private record Contador(AtomicInteger quantidade, long inicioJanela) {
    }

    private final ObjectMapper objectMapper;

    // Regras avaliadas em ordem — a primeira que casar (metodo + caminho) e' aplicada.
    private final List<Regra> regras = List.of(
            new Regra(Pattern.compile("^/api/auth/login$"), "POST", 10, 5 * 60_000L),
            new Regra(Pattern.compile("^/api/auth/register$"), "POST", 5, 60 * 60_000L),
            new Regra(Pattern.compile("^/api/chat$"), "POST", 20, 5 * 60_000L),
            new Regra(Pattern.compile("^/api/agendamentos$"), "POST", 10, 60 * 60_000L),
            new Regra(Pattern.compile("^/api/ordens-servico/protocolo/.+$"), "GET", 30, 5 * 60_000L),
            new Regra(Pattern.compile("^/api/ordens-servico/\\d+/avaliacao$"), "POST", 10, 60 * 60_000L)
    );

    private final Map<String, Contador> contadores = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {

        if (!habilitado) {
            filterChain.doFilter(request, response);
            return;
        }

        String caminho = request.getRequestURI();
        String metodo = request.getMethod();

        Regra regra = regras.stream()
                .filter(r -> r.metodo().equals(metodo) && r.caminho().matcher(caminho).matches())
                .findFirst().orElse(null);

        if (regra != null) {
            String chave = clienteId(request) + "|" + regra.caminho().pattern();
            if (excedeuLimite(chave, regra)) {
                responderBloqueado(response, request);
                return;
            }
        }

        limparEntradasAntigasOcasionalmente();
        filterChain.doFilter(request, response);
    }

    private boolean excedeuLimite(String chave, Regra regra) {
        long agora = Instant.now().toEpochMilli();
        Contador atualizado = contadores.compute(chave, (k, existente) -> {
            if (existente == null || (agora - existente.inicioJanela()) > regra.janelaMillis()) {
                return new Contador(new AtomicInteger(1), agora);
            }
            existente.quantidade().incrementAndGet();
            return existente;
        });
        return atualizado.quantidade().get() > regra.maxRequisicoes();
    }

    /** Confia em X-Forwarded-For apenas se vier de um proxy reverso configurado (ver README/producao); caso contrario usa o IP direto da conexao. */
    private String clienteId(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void responderBloqueado(HttpServletResponse response, HttpServletRequest request) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = ErrorResponse.of(429, "Too Many Requests",
                "Muitas tentativas em pouco tempo. Aguarde alguns minutos e tente novamente.",
                request.getRequestURI());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    /** Evita crescimento ilimitado do mapa em memoria ao longo do tempo — limpeza oportunista, sem agendador dedicado. */
    private void limparEntradasAntigasOcasionalmente() {
        if (contadores.size() < 5000) return;
        long agora = Instant.now().toEpochMilli();
        contadores.entrySet().removeIf(e -> (agora - e.getValue().inicioJanela()) > 60 * 60_000L);
    }
}
