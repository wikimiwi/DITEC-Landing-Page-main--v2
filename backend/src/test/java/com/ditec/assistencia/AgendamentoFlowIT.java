package com.ditec.assistencia;

import com.ditec.assistencia.dto.agendamento.AgendamentoCreateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Cobre "34. TESTES / Agendamento" e "OS" do prompt mestre:
 *  - cliente (visitante) cria agendamento -> protocolo e' gerado
 *  - horario ja ocupado e' bloqueado (409)
 *  - bairro fora da area e' bloqueado (422)
 *  - rastreamento publico por protocolo funciona
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AgendamentoFlowIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /** Proximo dia util (nao-domingo) a partir de uma semana no futuro, sempre as 10:00 — um dos horarios fixos permitidos. */
    private LocalDateTime proximoHorarioValido() {
        LocalDate dia = LocalDate.now().plusDays(9);
        while (dia.getDayOfWeek() == DayOfWeek.SUNDAY) dia = dia.plusDays(1);
        return dia.atTime(LocalTime.of(10, 0));
    }

    @Test
    void criarAgendamentoGeraProtocoloERastreiaPorProtocolo() throws Exception {
        AgendamentoCreateRequest req = new AgendamentoCreateRequest(
                "Visitante Teste", "(11) 90000-1111", "Geladeira Consul",
                "Nao gela", "Rua Teste, 100", "Moema", proximoHorarioValido(), null);

        String protocolo = mockMvc.perform(post("/api/agendamentos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.protocolo").isNotEmpty())
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andReturn().getResponse().getContentAsString();

        String protocoloExtraido = objectMapper.readTree(protocolo).get("protocolo").asText();

        mockMvc.perform(get("/api/ordens-servico/protocolo/{protocolo}", protocoloExtraido))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AGENDADO"))
                .andExpect(jsonPath("$.timeline").isArray())
                .andExpect(jsonPath("$.timeline[0].statusNovo").value("AGENDADO"));
    }

    @Test
    void criarDoisAgendamentosNoMesmoHorarioRetorna409() throws Exception {
        LocalDateTime horario = proximoHorarioValido().plusDays(1); // slot exclusivo deste teste
        AgendamentoCreateRequest req1 = new AgendamentoCreateRequest(
                "Cliente 1", "(11) 90000-2222", "Maquina de lavar LG",
                "Nao centrifuga", "Rua A, 1", "Pinheiros", horario, null);
        AgendamentoCreateRequest req2 = new AgendamentoCreateRequest(
                "Cliente 2", "(11) 90000-3333", "Micro-ondas",
                "Nao liga", "Rua B, 2", "Vila Mariana", horario, null);

        mockMvc.perform(post("/api/agendamentos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/agendamentos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isConflict());
    }

    @Test
    void criarAgendamentoEmBairroNaoAtendidoRetorna422() throws Exception {
        AgendamentoCreateRequest req = new AgendamentoCreateRequest(
                "Cliente Fora De Area", "(11) 90000-4444", "Fogao",
                "Nao acende", "Rua Distante, 1", "Bairro Que Nao Existe Na Lista", proximoHorarioValido().plusDays(2), null);

        mockMvc.perform(post("/api/agendamentos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void protocoloInexistenteRetorna404() throws Exception {
        mockMvc.perform(get("/api/ordens-servico/protocolo/{protocolo}", "DITEC-2000-999999"))
                .andExpect(status().isNotFound());
    }
}
