package com.ditec.assistencia;

import com.ditec.assistencia.dto.auth.LoginRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cobre "9. Brute force e rate limiting" e "32. Testes de seguranca / API" da
 * auditoria. Roda com um contexto Spring proprio (ditec.ratelimit.enabled=true
 * via @TestPropertySource) para nao afetar os demais testes, que dependem do
 * rate limit desligado (application-test.yml) para poder logar varias vezes
 * em sequencia sem ser tratados como forca bruta.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "ditec.ratelimit.enabled=true")
class RateLimitIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void bloqueiaAposMuitasTentativasDeLoginEmSequencia() throws Exception {
        LoginRequest tentativa = new LoginRequest("naoexiste@ditec.com.br", "senhaqualquer");
        String corpo = objectMapper.writeValueAsString(tentativa);

        // as 10 primeiras tentativas passam pelo filtro normalmente (401, credenciais invalidas)
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo))
                    .andExpect(status().isUnauthorized());
        }

        // a 11a tentativa, na mesma janela, e' bloqueada pelo rate limit (429)
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isTooManyRequests());
    }
}
