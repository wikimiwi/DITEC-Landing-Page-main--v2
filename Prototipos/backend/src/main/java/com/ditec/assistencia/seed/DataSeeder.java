package com.ditec.assistencia.seed;

import com.ditec.assistencia.entity.*;
import com.ditec.assistencia.enums.StatusAgendamento;
import com.ditec.assistencia.enums.StatusConta;
import com.ditec.assistencia.enums.StatusOS;
import com.ditec.assistencia.enums.TipoEventoOS;
import com.ditec.assistencia.enums.TipoUsuario;
import com.ditec.assistencia.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * Popula dados de desenvolvimento/demonstracao no primeiro start (secao 31
 * do prompt mestre: "criar dados de teste... deixar claro que nao sao dados
 * reais, nunca inserir senhas reais").
 *
 * DECISAO TECNICA: em vez de um V2__seed.sql com hashes bcrypt fixos no
 * script, o seed roda como codigo Java usando o MESMO PasswordEncoder bean
 * que valida o login — assim a senha de teste sempre bate com o hash
 * gerado, sem risco de um hash bcrypt digitado a mao ficar incompativel.
 *
 * So roda se DITEC_SEED_ENABLED=true (padrao) E ainda nao existir nenhum
 * ADMINISTRADOR no banco — ou seja, e' seguro reiniciar a aplicacao varias
 * vezes: o seed nunca duplica dados.
 *
 * TRAVA DE SEGURANCA (auditoria): alem da flag, o seed se RECUSA a rodar
 * se o profile ativo contiver "prod" — mesmo que alguem esqueca
 * DITEC_SEED_ENABLED=true configurado em producao, a conta
 * admin@ditec.com.br/admin123 nunca sera criada la'.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    @Value("${ditec.seed.enabled:true}")
    private boolean seedEnabled;

    private final Environment environment;
    private final UsuarioRepository usuarioRepository;
    private final ClienteRepository clienteRepository;
    private final TecnicoRepository tecnicoRepository;
    private final ServicoRepository servicoRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final OrdemServicoRepository ordemServicoRepository;
    private final TimelineOSRepository timelineOSRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.ditec.assistencia.service.ProtocoloService protocoloService;


    @Override
    @Transactional
    public void run(String... args) {
        boolean profileDeProducao = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> p.toLowerCase().contains("prod"));
        if (profileDeProducao) {
            log.warn("DITEC seed: profile de producao detectado ({}) — seed NUNCA roda aqui, " +
                            "independente de DITEC_SEED_ENABLED.",
                    Arrays.toString(environment.getActiveProfiles()));
            return;
        }
        if (!seedEnabled) {
            log.info("DITEC seed desabilitado (DITEC_SEED_ENABLED=false).");
            return;
        }
        if (usuarioRepository.existsByTipo(TipoUsuario.ADMINISTRADOR)) {
            log.info("DITEC seed: ja existe um administrador cadastrado, pulando.");
            return;
        }

        log.warn("==========================================================");
        log.warn(" DITEC — populando dados de TESTE (ambiente de desenvolvimento).");
        log.warn(" NENHUMA dessas contas/senhas deve ser usada em producao.");
        log.warn("==========================================================");

        // ---- Administrador ----
        Usuario admin = usuarioRepository.save(Usuario.builder()
                .nome("Administrador DITEC")
                .email("admin@ditec.com.br")
                .senhaHash(passwordEncoder.encode("admin123"))
                .telefone("(11) 99999-0000")
                .tipo(TipoUsuario.ADMINISTRADOR)
                .status(StatusConta.ATIVO)
                .build());

        // ---- Tecnico ----
        Usuario usuarioTecnico = usuarioRepository.save(Usuario.builder()
                .nome("Carlos Andrade")
                .email("tecnico@ditec.com.br")
                .senhaHash(passwordEncoder.encode("tecnico123"))
                .telefone("(11) 98888-1111")
                .tipo(TipoUsuario.TECNICO)
                .status(StatusConta.ATIVO)
                .build());
        Tecnico tecnico = tecnicoRepository.save(Tecnico.builder()
                .usuario(usuarioTecnico)
                .nome(usuarioTecnico.getNome())
                .email(usuarioTecnico.getEmail())
                .telefone(usuarioTecnico.getTelefone())
                .especialidade("Refrigeracao e lavanderia")
                .certificacao("SENAI — Tecnico em Refrigeracao")
                .status(StatusConta.ATIVO)
                .build());

        // ---- Cliente ----
        Usuario usuarioCliente = usuarioRepository.save(Usuario.builder()
                .nome("Maria Fernanda Souza")
                .email("cliente@ditec.com.br")
                .senhaHash(passwordEncoder.encode("cliente123"))
                .telefone("(11) 97777-2222")
                .tipo(TipoUsuario.CLIENTE)
                .status(StatusConta.ATIVO)
                .build());
        Cliente cliente = clienteRepository.save(Cliente.builder()
                .usuario(usuarioCliente)
                .endereco("Rua das Acacias, 123")
                .bairro("Moema")
                .cidade("Sao Paulo")
                .uf("SP")
                .cep("04077-000")
                .build());

        // ---- Servicos ----
        servicoRepository.save(Servico.builder().nome("Conserto de geladeira").descricao("Diagnostico e reparo de geladeiras e freezers.").valorBase(new BigDecimal("180.00")).status(StatusConta.ATIVO).build());
        servicoRepository.save(Servico.builder().nome("Conserto de maquina de lavar").descricao("Diagnostico e reparo de maquinas de lavar e secadoras.").valorBase(new BigDecimal("160.00")).status(StatusConta.ATIVO).build());
        servicoRepository.save(Servico.builder().nome("Manutencao de ar-condicionado").descricao("Limpeza, recarga de gas e reparo de ar-condicionado.").valorBase(new BigDecimal("220.00")).status(StatusConta.ATIVO).build());

        // ---- Agendamentos + OS de DEMONSTRACAO (dados ficticios) ----
        // Tres OS em momentos diferentes do ciclo, para testar o rastreamento:
        //  1) ENTREGUE (historico completo)   2) AGUARDANDO_APROVACAO (orcamento)
        //  3) CANCELADA (visitante sem conta)
        LocalDateTime base = LocalDateTime.now().minusDays(6).withHour(10).withMinute(0).withSecond(0).withNano(0);

        OrdemServico os1 = criarOS(cliente, null, null, tecnico, "Geladeira Brastemp", "Nao gela e faz ruido no compressor.",
                "Rua das Acacias, 123", "Moema", base, StatusOS.ENTREGUE);
        os1.setDiagnostico("Compressor com falha de partida; rele queimado.");
        os1.setValorOrcamento(new BigDecimal("180.00"));
        os1.setOrcamentoAprovado(true);
        os1.setValor(new BigDecimal("171.00"));
        os1.setFormaPagamento("pix");
        os1.setDesconto(new BigDecimal("0.05"));
        os1.setPecasUtilizadas("Rele de partida do compressor");
        os1.setDescricaoFinal("Rele substituido. Testado por 2h e funcionando normalmente.");
        os1.setDataInicio(base.plusMinutes(30));
        os1.setDataConclusao(base.plusHours(3));
        os1.setDataEntrega(base.plusHours(3).plusMinutes(10));
        os1.setGarantiaDias(90);
        os1.setNotaFiscalNumero("REC-000123 (exemplo)");
        ordemServicoRepository.save(os1);
        evento(os1, TipoEventoOS.ABERTURA, null, "AGENDADO", "Agendamento recebido pelo site. Aguardando avaliacao tecnica.", null, base.minusDays(1));
        evento(os1, TipoEventoOS.OBSERVACAO, "AGENDADO", "AGENDADO", "Visita tecnica confirmada.", admin, base.minusDays(1).plusHours(1));
        evento(os1, TipoEventoOS.STATUS, "AGENDADO", "EM_DIAGNOSTICO", "Tecnico iniciou a avaliacao no local.", usuarioTecnico, base.plusMinutes(30));
        evento(os1, TipoEventoOS.DIAGNOSTICO, "EM_DIAGNOSTICO", "EM_DIAGNOSTICO", "Diagnostico: compressor com falha de partida; rele queimado. | Orcamento: R$ 180,00", usuarioTecnico, base.plusMinutes(50));
        evento(os1, TipoEventoOS.STATUS, "EM_DIAGNOSTICO", "AGUARDANDO_APROVACAO", "Orcamento enviado ao cliente.", usuarioTecnico, base.plusMinutes(55));
        evento(os1, TipoEventoOS.STATUS, "AGUARDANDO_APROVACAO", "EM_MANUTENCAO", "Orcamento aprovado pelo cliente.", usuarioCliente, base.plusHours(1));
        evento(os1, TipoEventoOS.STATUS, "EM_MANUTENCAO", "EM_TESTES", "Rele substituido; aparelho em teste.", usuarioTecnico, base.plusHours(1).plusMinutes(30));
        evento(os1, TipoEventoOS.STATUS, "EM_TESTES", "CONCLUIDO", "Servico concluido. Garantia de 90 dias ativada.", usuarioTecnico, base.plusHours(3));
        evento(os1, TipoEventoOS.STATUS, "CONCLUIDO", "ENTREGUE", "Aparelho entregue funcionando ao cliente.", usuarioTecnico, base.plusHours(3).plusMinutes(10));

        LocalDateTime base2 = LocalDateTime.now().minusDays(1).withHour(14).withMinute(30).withSecond(0).withNano(0);
        OrdemServico os2 = criarOS(cliente, null, null, tecnico, "Maquina de lavar Electrolux", "Nao centrifuga e faz barulho.",
                "Rua das Acacias, 123", "Moema", base2, StatusOS.AGUARDANDO_APROVACAO);
        os2.setDiagnostico("Rolamento do cesto desgastado; necessaria troca do kit de rolamentos.");
        os2.setValorOrcamento(new BigDecimal("260.00"));
        os2.setPrevisaoConclusao(java.time.LocalDate.now().plusDays(3));
        os2.setDataInicio(base2.plusMinutes(15));
        ordemServicoRepository.save(os2);
        evento(os2, TipoEventoOS.ABERTURA, null, "AGENDADO", "Agendamento recebido pelo site. Aguardando avaliacao tecnica.", null, base2.minusDays(1));
        evento(os2, TipoEventoOS.STATUS, "AGENDADO", "EM_DIAGNOSTICO", "Tecnico iniciou a avaliacao no local.", usuarioTecnico, base2.plusMinutes(15));
        evento(os2, TipoEventoOS.DIAGNOSTICO, "EM_DIAGNOSTICO", "EM_DIAGNOSTICO", "Diagnostico: rolamento do cesto desgastado. | Orcamento: R$ 260,00", usuarioTecnico, base2.plusMinutes(45));
        evento(os2, TipoEventoOS.STATUS, "EM_DIAGNOSTICO", "AGUARDANDO_APROVACAO", "Orcamento enviado ao cliente.", usuarioTecnico, base2.plusMinutes(50));

        LocalDateTime base3 = LocalDateTime.now().minusDays(2).withHour(8).withMinute(30).withSecond(0).withNano(0);
        OrdemServico os3 = criarOS(null, "Joao Pereira Lima", "(11) 96666-3333", null, "Micro-ondas Panasonic", "Nao esquenta.",
                "Av. Paulista, 1000", "Bela Vista", base3, StatusOS.CANCELADO);
        evento(os3, TipoEventoOS.ABERTURA, null, "AGENDADO", "Agendamento recebido pelo site. Aguardando avaliacao tecnica.", null, base3.minusDays(1));
        evento(os3, TipoEventoOS.STATUS, "AGENDADO", "CANCELADO", "Cancelado a pedido do cliente.", admin, base3.minusHours(5));

        log.warn(" OS de demonstracao (rastreio = protocolo + telefone):");
        log.warn("   {} (entregue)            tel. (11) 97777-2222", os1.getProtocolo());
        log.warn("   {} (aguardando aprovacao) tel. (11) 97777-2222", os2.getProtocolo());
        log.warn("   {} (cancelada)           tel. (11) 96666-3333", os3.getProtocolo());
        log.warn(" Login admin:    admin@ditec.com.br    / admin123");
        log.warn(" Login tecnico:  tecnico@ditec.com.br   / tecnico123");
        log.warn(" Login cliente:  cliente@ditec.com.br   / cliente123");
        log.warn("==========================================================");
    }

    private OrdemServico criarOS(Cliente cliente, String nomeContato, String telefoneContato, Tecnico tecnico,
                                 String aparelho, String problema, String endereco, String bairro,
                                 LocalDateTime dataHora, StatusOS status) {
        Agendamento ag = agendamentoRepository.save(Agendamento.builder()
                .cliente(cliente).nomeContato(nomeContato).telefoneContato(telefoneContato).tecnico(tecnico)
                .tipoAparelho(aparelho).descricaoProblema(problema).endereco(endereco).bairro(bairro)
                .dataHora(dataHora).status(status.paraStatusAgendamento()).prioridadeGas(false)
                .build());
        ag.setProtocolo(protocoloService.gerar(ag.getId()));
        ag = agendamentoRepository.save(ag);
        return ordemServicoRepository.save(OrdemServico.builder()
                .agendamento(ag).cliente(cliente).tecnico(tecnico).protocolo(ag.getProtocolo())
                .descricaoProblema(problema).status(status).garantiaDias(90)
                .criadoEm(dataHora.minusDays(1)).build());
    }

    private void evento(OrdemServico os, TipoEventoOS tipo, String anterior, String novo, String obs,
                        Usuario usuario, LocalDateTime quando) {
        timelineOSRepository.save(TimelineOS.builder().ordemServico(os).tipo(tipo).statusAnterior(anterior)
                .statusNovo(novo).observacao(obs).alteradoPor(usuario != null ? usuario.getNome() : "sistema")
                .usuario(usuario).visivelCliente(true).dataHora(quando).build());
    }
}
