package com.ditec.assistencia;

import com.ditec.assistencia.dto.auth.LoginRequest;
import com.ditec.assistencia.entity.*;
import com.ditec.assistencia.enums.StatusAgendamento;
import com.ditec.assistencia.enums.StatusConta;
import com.ditec.assistencia.enums.StatusOS;
import com.ditec.assistencia.enums.TipoUsuario;
import com.ditec.assistencia.repository.*;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ciclo de vida completo da Ordem de Servico (dados ficticios, banco H2 do perfil "test"):
 * transicoes validas e invalidas, historico persistido, diagnostico/orcamento,
 * finalizacao com desconto, rastreamento e permissoes por perfil.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrdemServicoFlowIT {

    private static final String TEL_CLIENTE = "(11) 95555-0000";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private TecnicoRepository tecnicoRepository;
    @Autowired private AgendamentoRepository agendamentoRepository;
    @Autowired private OrdemServicoRepository ordemServicoRepository;
    @Autowired private TimelineOSRepository timelineOSRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private OrdemServico os;
    private Agendamento agendamento;
    private Tecnico outroTecnico;

    @BeforeEach
    void montarCenario() {
        Usuario uTec = usuario("Tecnico de Teste", "tecnico.it@ditec.com.br", TipoUsuario.TECNICO);
        tecnicoRepository.save(Tecnico.builder().usuario(uTec).nome(uTec.getNome()).email(uTec.getEmail())
                .status(StatusConta.ATIVO).build());
        Usuario uTec2 = usuario("Outro Tecnico", "tecnico2.it@ditec.com.br", TipoUsuario.TECNICO);
        outroTecnico = tecnicoRepository.save(Tecnico.builder().usuario(uTec2).nome(uTec2.getNome()).email(uTec2.getEmail())
                .status(StatusConta.ATIVO).build());
        usuario("Admin de Teste", "admin.it@ditec.com.br", TipoUsuario.ADMINISTRADOR);
        Usuario uCli = usuario("Cliente Dono Teste", "cliente.it@ditec.com.br", TipoUsuario.CLIENTE);
        uCli.setTelefone(TEL_CLIENTE);
        Cliente cliente = clienteRepository.save(Cliente.builder().usuario(uCli).build());
        Usuario uCli2 = usuario("Outro Cliente", "cliente2.it@ditec.com.br", TipoUsuario.CLIENTE);
        clienteRepository.save(Cliente.builder().usuario(uCli2).build());

        agendamento = agendamentoRepository.save(Agendamento.builder()
                .cliente(cliente).tipoAparelho("Fogao a gas").descricaoProblema("Nao acende o forno")
                .endereco("Rua X, 1").bairro("Moema").dataHora(LocalDateTime.now().plusDays(3))
                .status(StatusAgendamento.PENDENTE).prioridadeGas(true).build());
        agendamento.setProtocolo("DITEC-" + Year.now() + "-" + String.format("%06d", agendamento.getId()));
        agendamento = agendamentoRepository.save(agendamento);

        os = ordemServicoRepository.save(OrdemServico.builder()
                .agendamento(agendamento).cliente(cliente).protocolo(agendamento.getProtocolo())
                .descricaoProblema(agendamento.getDescricaoProblema())
                .status(StatusOS.AGENDADO).garantiaDias(90).build());
    }

    private Usuario usuario(String nome, String email, TipoUsuario tipo) {
        return usuarioRepository.save(Usuario.builder().nome(nome).email(email)
                .senhaHash(passwordEncoder.encode("senha12345")).telefone("(11) 90000-0000")
                .tipo(tipo).status(StatusConta.ATIVO).build());
    }

    private String token(String email) throws Exception {
        String resp = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "senha12345"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).get("token").asText();
    }

    private ResultActions put(String token, String url, Object body) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(url, os.getId())
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions postJson(String token, String url, Object body) throws Exception {
        var req = post(url, os.getId()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
        if (token != null) req.header("Authorization", "Bearer " + token);
        return mockMvc.perform(req);
    }

    private ResultActions mudar(String token, String novo, String obs) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("status", novo);
        body.put("observacao", obs);
        return put(token, "/api/ordens-servico/{id}/status", body);
    }

    private void confirmarAgendamento() {
        agendamento.setStatus(StatusAgendamento.AGENDADO);
        agendamentoRepository.save(agendamento);
    }

    // ------------------------------------------------------------------

    @Test
    void naoIniciaAtendimentoComAgendamentoPendente() throws Exception {
        mudar(token("tecnico.it@ditec.com.br"), "EM_DIAGNOSTICO", null).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void cicloCompletoAteEntregaComHistoricoEDesconto() throws Exception {
        confirmarAgendamento();
        String tec = token("tecnico.it@ditec.com.br");

        mudar(tec, "EM_DIAGNOSTICO", "Tecnico no local.").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_DIAGNOSTICO"))
                .andExpect(jsonPath("$.tecnicoNome").value("Tecnico de Teste")); // assumiu a OS sem tecnico

        // nao avanca sem diagnostico
        mudar(tec, "EM_MANUTENCAO", null).andExpect(status().isUnprocessableEntity());

        put(tec, "/api/ordens-servico/{id}/diagnostico", Map.of("diagnostico", "Termopar com defeito.",
                "valorOrcamento", 200, "previsaoConclusao", LocalDate.now().plusDays(2).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diagnostico").value("Termopar com defeito."))
                .andExpect(jsonPath("$.valorOrcamento").value(200.0));

        mudar(tec, "AGUARDANDO_APROVACAO", null).andExpect(status().isOk());
        postJson(token("cliente.it@ditec.com.br"), "/api/ordens-servico/{id}/orcamento/resposta", Map.of("aprovado", true))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("EM_MANUTENCAO"))
                .andExpect(jsonPath("$.orcamentoAprovado").value(true));

        mudar(tec, "EM_TESTES", "Peca trocada.").andExpect(status().isOk());
        // CONCLUIDO so' via /finalizar
        mudar(tec, "CONCLUIDO", null).andExpect(status().isUnprocessableEntity());

        postJson(tec, "/api/ordens-servico/{id}/finalizar", Map.of("valor", 200, "formaPagamento", "pix",
                "descricaoFinal", "Termopar substituido e testado."))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDO"))
                .andExpect(jsonPath("$.valor").value(190.00))
                .andExpect(jsonPath("$.garantiaDias").value(90))
                .andExpect(jsonPath("$.garantiaAte").isNotEmpty());

        mudar(tec, "ENTREGUE", "Entregue ao cliente.").andExpect(status().isOk())
                .andExpect(jsonPath("$.dataEntrega").isNotEmpty());

        // estado terminal: nada mais muda
        mudar(token("admin.it@ditec.com.br"), "EM_MANUTENCAO", null).andExpect(status().isUnprocessableEntity());

        // historico persistido e nunca sobrescrito
        List<TimelineOS> historico = timelineOSRepository.findByOrdemServico_IdOrderByDataHoraAscIdAsc(os.getId());
        assertThat(historico).extracting(TimelineOS::getStatusNovo)
                .contains("EM_DIAGNOSTICO", "AGUARDANDO_APROVACAO", "EM_MANUTENCAO", "EM_TESTES", "CONCLUIDO", "ENTREGUE");
        assertThat(historico).allSatisfy(t -> {
            assertThat(t.getDataHora()).isNotNull();
            assertThat(t.getAlteradoPor()).isNotBlank();
        });
        assertThat(historico).filteredOn(t -> "ENTREGUE".equals(t.getStatusNovo()))
                .singleElement().satisfies(t -> {
                    assertThat(t.getStatusAnterior()).isEqualTo("CONCLUIDO");
                    assertThat(t.getUsuario()).isNotNull();
                });

        // rastreamento publico reflete o banco
        postJson(null, "/api/ordens-servico/rastreio", Map.of("protocolo", os.getProtocolo(), "telefone", TEL_CLIENTE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENTREGUE"))
                .andExpect(jsonPath("$.etapa").value(5))
                .andExpect(jsonPath("$.diagnostico").value("Termopar com defeito."))
                .andExpect(jsonPath("$.timeline.length()").value(historico.size()));
    }

    @Test
    void naoPermitePularEtapa() throws Exception {
        confirmarAgendamento();
        mudar(token("tecnico.it@ditec.com.br"), "EM_TESTES", null).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void somenteAdminCancelaEExigeMotivo() throws Exception {
        String tec = token("tecnico.it@ditec.com.br");
        String adm = token("admin.it@ditec.com.br");
        mudar(tec, "CANCELADO", "x").andExpect(status().isForbidden());
        mudar(adm, "CANCELADO", null).andExpect(status().isUnprocessableEntity());
        mudar(adm, "CANCELADO", "Cliente desistiu.").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADO"));
        assertThat(agendamentoRepository.findById(agendamento.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusAgendamento.CANCELADO);
        // cancelada nao avanca
        mudar(adm, "EM_DIAGNOSTICO", null).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void clienteRecusaOrcamentoECancelaSemCusto() throws Exception {
        confirmarAgendamento();
        String tec = token("tecnico.it@ditec.com.br");
        mudar(tec, "EM_DIAGNOSTICO", null).andExpect(status().isOk());
        put(tec, "/api/ordens-servico/{id}/diagnostico", Map.of("diagnostico", "Placa queimada.", "valorOrcamento", 500))
                .andExpect(status().isOk());
        mudar(tec, "AGUARDANDO_APROVACAO", null).andExpect(status().isOk());

        // outro cliente nao pode responder
        postJson(token("cliente2.it@ditec.com.br"), "/api/ordens-servico/{id}/orcamento/resposta", Map.of("aprovado", true))
                .andExpect(status().isForbidden());

        postJson(token("cliente.it@ditec.com.br"), "/api/ordens-servico/{id}/orcamento/resposta", Map.of("aprovado", false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADO"))
                .andExpect(jsonPath("$.orcamentoAprovado").value(false));
    }

    @Test
    void aguardandoAprovacaoExigeOrcamento() throws Exception {
        confirmarAgendamento();
        String tec = token("tecnico.it@ditec.com.br");
        mudar(tec, "EM_DIAGNOSTICO", null).andExpect(status().isOk());
        put(tec, "/api/ordens-servico/{id}/diagnostico", Map.of("diagnostico", "Sem orcamento ainda."))
                .andExpect(status().isOk());
        mudar(tec, "AGUARDANDO_APROVACAO", null).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void observacaoInternaNaoApareceNoRastreioMasApareceParaEquipe() throws Exception {
        String tec = token("tecnico.it@ditec.com.br");
        postJson(tec, "/api/ordens-servico/{id}/observacoes", Map.of("texto", "Nota interna secreta", "visivelCliente", false))
                .andExpect(status().isCreated());
        postJson(tec, "/api/ordens-servico/{id}/observacoes", Map.of("texto", "Cliente avisado por WhatsApp"))
                .andExpect(status().isCreated());

        String publico = postJson(null, "/api/ordens-servico/rastreio",
                Map.of("protocolo", os.getProtocolo(), "telefone", "11955550000"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(publico).doesNotContain("Nota interna secreta").contains("Cliente avisado por WhatsApp");

        String equipe = mockMvc.perform(get("/api/ordens-servico/{id}", os.getId())
                        .header("Authorization", "Bearer " + token("admin.it@ditec.com.br")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(equipe).contains("Nota interna secreta");

        String dono = mockMvc.perform(get("/api/ordens-servico/{id}", os.getId())
                        .header("Authorization", "Bearer " + token("cliente.it@ditec.com.br")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(dono).doesNotContain("Nota interna secreta");
    }

    @Test
    void permissoesDeAcessoAOS() throws Exception {
        // outro cliente nao ve a OS
        mockMvc.perform(get("/api/ordens-servico/{id}", os.getId())
                        .header("Authorization", "Bearer " + token("cliente2.it@ditec.com.br")))
                .andExpect(status().isForbidden());
        // cliente nao altera status
        mudar(token("cliente.it@ditec.com.br"), "EM_DIAGNOSTICO", null).andExpect(status().isForbidden());
        // sem login: 401
        mockMvc.perform(get("/api/ordens-servico/{id}", os.getId())).andExpect(status().isUnauthorized());

        // OS atribuida a outro tecnico: tecnico de teste nao mexe
        os.setTecnico(outroTecnico);
        ordemServicoRepository.save(os);
        confirmarAgendamento();
        mudar(token("tecnico.it@ditec.com.br"), "EM_DIAGNOSTICO", null).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/ordens-servico").header("Authorization", "Bearer " + token("tecnico.it@ditec.com.br")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminFiltraEAtribuiTecnicoAtivo() throws Exception {
        String adm = token("admin.it@ditec.com.br");
        mockMvc.perform(get("/api/admin/ordens-servico").param("protocolo", os.getProtocolo())
                        .header("Authorization", "Bearer " + adm))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].prioridadeGas").value(true));
        mockMvc.perform(get("/api/admin/ordens-servico").param("status", "ENTREGUE")
                        .header("Authorization", "Bearer " + adm))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/admin/ordens-servico").param("cliente", "dono teste")
                        .header("Authorization", "Bearer " + adm))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));

        put(adm, "/api/admin/ordens-servico/{id}/tecnico", Map.of("tecnicoId", outroTecnico.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tecnicoNome").value("Outro Tecnico"));

        outroTecnico.setStatus(StatusConta.INATIVO);
        tecnicoRepository.save(outroTecnico);
        Tecnico inativo = outroTecnico;
        Tecnico ativo = tecnicoRepository.findByUsuario_Id(
                usuarioRepository.findByEmailIgnoreCase("tecnico.it@ditec.com.br").orElseThrow().getId()).orElseThrow();
        put(adm, "/api/admin/ordens-servico/{id}/tecnico", Map.of("tecnicoId", ativo.getId())).andExpect(status().isOk());
        put(adm, "/api/admin/ordens-servico/{id}/tecnico", Map.of("tecnicoId", inativo.getId()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void previsaoNoPassadoERecusada() throws Exception {
        put(token("tecnico.it@ditec.com.br"), "/api/ordens-servico/{id}/previsao",
                Map.of("previsaoConclusao", LocalDate.now().minusDays(1).toString()))
                .andExpect(status().isUnprocessableEntity());
        put(token("tecnico.it@ditec.com.br"), "/api/ordens-servico/{id}/previsao",
                Map.of("previsaoConclusao", LocalDate.now().plusDays(5).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previsaoConclusao").value(LocalDate.now().plusDays(5).toString()));
    }
}
