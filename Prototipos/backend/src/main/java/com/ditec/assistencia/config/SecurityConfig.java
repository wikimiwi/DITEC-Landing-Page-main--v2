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
            .headers(headers -> headers
                // API pura (JSON) — nao ha' motivo legitimo pra carregar qualquer
                // subrecurso a partir das respostas do backend.
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'none'; frame-ancestors 'none'; base-uri 'none'"))
                .frameOptions(frame -> frame.deny())
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
.permissionsPolicy(permissions -> permissions.policy(
        "geolocation=(), microphone=(), camera=(), payment=(), usb=()"))
.and()
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
                .requestMatchers(HttpMethod.GET, "/api/ordens-servico/protocolo/**").permitAll()
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
        configuration.setAllowedOrigins(
                corsProperties.allowedOrigins() != null && !corsProperties.allowedOrigins().isEmpty()
                        ? corsProperties.allowedOrigins()
                        : List.of("http://localhost:5500"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
