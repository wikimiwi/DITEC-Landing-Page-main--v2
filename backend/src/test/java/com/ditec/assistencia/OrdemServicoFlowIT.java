package com.ditec.assistencia;

import com.ditec.assistencia.dto.auth.LoginRequest;
import com.ditec.assistencia.dto.os.FinalizarOSRequest;
import com.ditec.assistencia.dto.os.StatusUpdateRequest;
import com.ditec.assistencia.entity.Agendamento;
import com.ditec.assistencia.entity.OrdemServico;
import com.ditec.assistencia.entity.Tecnico;
import com.ditec.assistencia.entity.Usuario;
import com.ditec.assistencia.enums.StatusAgendamento;
import com.ditec.assistencia.enums.StatusConta;
import com.ditec.assistencia.enums.StatusOS;
import com.ditec.assistencia.enums.TipoUsuario;
import com.ditec.assistencia.repository.AgendamentoRepository;
import com.ditec.assistencia.repository.OrdemServicoRepository;
import com.ditec.assistencia.repository.TecnicoRepository;
import com.ditec.assistencia.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Year;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cobre "34. TESTES / OS" do prompt mestre:
 *  - tecnico assume uma OS sem tecnico atribuido e avanca o status
 *  - progressao invalida (pular etapa ou regredir) e' recusada (422)
 *  - finalizar aplica desconto de 5% para pagamento a vista e ativa garantia
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrdemServicoFlowIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private TecnicoRepository tecnicoRepository;
    @Autowired private AgendamentoRepository agendamentoRepository;
    @Autowired private OrdemServicoRepository ordemServicoRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private OrdemServico osTeste;

    @BeforeEach
    void montarCenario() {
        Usuario usuarioTecnico = usuarioRepository.save(Usuario.builder()
                .nome("Tecnico de Teste").email("tecnico.it@ditec.com.br")
                .senhaHash(passwordEncoder.encode("senha123")).telefone("(11) 90000-9999")
                .tipo(TipoUsuario.TECNICO).status(StatusConta.ATIVO).build());
        tecnicoRepository.save(Tecnico.builder().usuario(usuarioTecnico).nome(usuarioTecnico.getNome())
                .email(usuarioTecnico.getEmail()).telefone(usuarioTecnico.getTelefone())
                .status(StatusConta.ATIVO).build());

        Agendamento agendamento = agendamentoRepository.save(Agendamento.builder()
                .nomeContato("Cliente Sem Conta").telefoneContato("(11) 95555-0000")
                .tipoAparelho("Fogao a gas").descricaoProblema("Nao acende o forno")
                .endereco("Rua X, 1").bairro("Moema")
                .dataHora(LocalDateTime.now().plusDays(3))
                .status(StatusAgendamento.PENDENTE).prioridadeGas(true).build());
        agendamento.setProtocolo("DITEC-" + Year.now() + "-" + String.format("%06d", agendamento.getId()));
        agendamento = agendamentoRepository.save(agendamento);

        osTeste = ordemServicoRepository.save(OrdemServico.builder()
                .agendamento(agendamento).protocolo(agendamento.getProtocolo())
                .descricaoProblema(agendamento.getDescricaoProblema())
                .status(StatusOS.AGENDADO).garantiaDias(90).build());
    }

    private String tokenTecnico() throws Exception {
        LoginRequest login = new LoginRequest("tecnico.it@ditec.com.br", "senha123");
        String resposta = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(resposta);
        return node.get("token").asText();
    }

    @Test
    void tecnicoAssumeEProgrideStatusAteEmAtendimento() throws Exception {
        String token = tokenTecnico();
        StatusUpdateRequest para = new StatusUpdateRequest("EM_ATENDIMENTO", "Tecnico a caminho.");

        mockMvc.perform(put("/api/ordens-servico/{id}/status", osTeste.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(para)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_ATENDIMENTO"))
                .andExpect(jsonPath("$.timeline.length()").value(1));
    }

    @Test
    void naoPermitePularEtapaDeAgendadoParaConcluido() throws Exception {
        String token = tokenTecnico();
        StatusUpdateRequest para = new StatusUpdateRequest("CONCLUIDO", "Tentando pular etapa.");

        mockMvc.perform(put("/api/ordens-servico/{id}/status", osTeste.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(para)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void finalizarAplicaDescontoDeCincoPorCentoNoPix() throws Exception {
        String token = tokenTecnico();

        mockMvc.perform(put("/api/ordens-servico/{id}/status", osTeste.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StatusUpdateRequest("EM_ATENDIMENTO", null))))
                .andExpect(status().isOk());

        FinalizarOSRequest finalizar = new FinalizarOSRequest(new BigDecimal("200.00"), "pix",
                "Valvula nova", "Servico concluido sem intercorrencias.", "NF-000999");

        mockMvc.perform(post("/api/ordens-servico/{id}/finalizar", osTeste.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(finalizar)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDO"))
                .andExpect(jsonPath("$.valor").value(190.00))
                .andExpect(jsonPath("$.garantiaDias").value(90));
    }
}
