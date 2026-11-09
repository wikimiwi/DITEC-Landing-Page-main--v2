package com.ditec.assistencia.config;

import com.ditec.assistencia.security.JwtAuthenticationFilter;
import com.ditec.assistencia.security.RateLimitFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Regras de autenticacao/autorizacao da API.
 *
 * Estrategia: stateless (sem sessao de servidor) + JWT no header
 * Authorization: Bearer <token>. Regras finas por perfil (CLIENTE,
 * ADMINISTRADOR, TECNICO) ficam nos controllers via @PreAuthorize —
 * aqui definimos apenas o que e' publico vs. o que exige login.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;
    private final CorsProperties corsProperties;
    private final org.springframework.core.env.Environment environment;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            // DECISAO DE SEGURANCA (CSRF): a API e' 100% stateless — autenticacao via
            // JWT no header "Authorization: Bearer", nunca por cookie. CSRF explora
            // credenciais ambientes automaticas (cookies) enviadas pelo navegador sem
            // o usuario perceber; como nao usamos cookie de sessao/autenticacao em
            // nenhum momento, nao ha' credencial ambiente para o ataque explorar, e
            // desabilitar CSRF aqui nao introduz uma vulnerabilidade nova. Se um dia
            // a autenticacao migrar para cookies (ex: por causa de XSS/localStorage),
            // esta configuracao PRECISA ser revisada e a protecao CSRF reativada.
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Sem token (ou token invalido/expirado) => 401, e nao 403. O front-end usa o
            // 401 para encerrar a sessao local expirada e pedir login novamente.
            .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
                response.setStatus(401);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\","
                        + "\"message\":\"Sessao expirada ou login necessario.\",\"path\":\""
                        + request.getRequestURI().replace("\"", "") + "\"}");
            }))
            .headers(headers -> headers
                // API pura (JSON) — nao ha' motivo legitimo pra carregar qualquer
                // subrecurso a partir das respostas do backend.
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'none'; frame-ancestors 'none'; base-uri 'none'"))
                .frameOptions(frame -> frame.deny())
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                // Permissions-Policy via header estatico: no Spring Security 6.3,
                // .permissionsPolicy(...) devolve PermissionsPolicyConfig e quebra o
                // encadeamento (era o motivo de o projeto nao compilar).
                .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy",
                        "geolocation=(), microphone=(), camera=(), payment=(), usb=()"))
                // So' tem efeito quando servido via HTTPS de verdade (o navegador
                // ignora esse header em HTTP puro) — necessario configurar em producao.
                .httpStrictTransportSecurity(hsts -> hsts
                        .includeSubDomains(true)
                        .maxAgeInSeconds(31536000))
                // Cache-Control: no-store ja' vem por padrao do Spring Security em
                // todas as respostas (nao precisa configurar explicitamente).
            )
            .authorizeHttpRequests(auth -> auth
                // --- publico ---
                .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/agendamentos/disponibilidade").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/agendamentos").permitAll() // suporta agendamento sem login
                .requestMatchers(HttpMethod.POST, "/api/ordens-servico/rastreio").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/chat").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/servicos").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                // --- tudo mais exige token valido; papel especifico e' checado com @PreAuthorize ---
                .anyRequest().authenticated()
            )
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Somente as origens configuradas (DITEC_ALLOWED_ORIGINS / FRONTEND_URL). Sem
        // configuracao, nenhuma origem externa e' liberada (antes caia em localhost).
        List<String> origens = corsProperties.allowedOrigins() == null ? List.of()
                : corsProperties.allowedOrigins().stream().map(String::trim).filter(o -> !o.isEmpty() && !"*".equals(o) && !o.startsWith("${")).toList();
        boolean producao = java.util.Arrays.stream(environment.getActiveProfiles()).anyMatch(p -> p.toLowerCase().contains("prod"));
        if (producao && origens.isEmpty()) {
            // falha rapida: em producao o front-end precisa estar explicitamente autorizado
            throw new IllegalStateException("Perfil prod sem origem de CORS: defina FRONTEND_URL (ou DITEC_ALLOWED_ORIGINS).");
        }
        configuration.setAllowedOrigins(origens);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        // A API nao usa cookies (JWT no header Authorization) — credenciais CORS desnecessarias.
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
