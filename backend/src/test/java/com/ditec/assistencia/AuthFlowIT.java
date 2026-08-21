package com.ditec.assistencia;

import com.ditec.assistencia.dto.auth.LoginRequest;
import com.ditec.assistencia.dto.auth.RegisterRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Cobre "34. TESTES / Cadastro" e "Login" do prompt mestre:
 *  - cliente consegue criar conta
 *  - e-mail duplicado e' bloqueado
 *  - login com senha errada e' recusado
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registrarNovoClienteRetornaToken() throws Exception {
        RegisterRequest req = new RegisterRequest("Joana Silva", "joana@example.com", "(11) 91234-5678", "senha123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tipo").value("CLIENTE"));
    }

    @Test
    void registrarComEmailJaExistenteRetorna409() throws Exception {
        RegisterRequest req = new RegisterRequest("Pedro Lima", "pedro.duplicado@example.com", "(11) 90000-0000", "senha123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    void loginComSenhaErradaRetorna401() throws Exception {
        RegisterRequest cadastro = new RegisterRequest("Ana Souza", "ana.login@example.com", "(11) 98765-4321", "senhaCerta");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cadastro)))
                .andExpect(status().isCreated());

        LoginRequest loginErrado = new LoginRequest("ana.login@example.com", "senhaErrada");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginErrado)))
                .andExpect(status().isUnauthorized());

        LoginRequest loginCerto = new LoginRequest("ana.login@example.com", "senhaCerta");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginCerto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }
}
