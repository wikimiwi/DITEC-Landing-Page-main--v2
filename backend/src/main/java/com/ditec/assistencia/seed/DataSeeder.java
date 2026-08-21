package com.ditec.assistencia.seed;

import com.ditec.assistencia.entity.*;
import com.ditec.assistencia.enums.StatusAgendamento;
import com.ditec.assistencia.enums.StatusConta;
import com.ditec.assistencia.enums.StatusOS;
import com.ditec.assistencia.enums.TipoUsuario;
import com.ditec.assistencia.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    @Value("${ditec.seed.enabled:true}")
    private boolean seedEnabled;

    private final UsuarioRepository usuarioRepository;
    private final ClienteRepository clienteRepository;
    private final TecnicoRepository tecnicoRepository;
    private final ServicoRepository servicoRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final OrdemServicoRepository ordemServicoRepository;
    private final TimelineOSRepository timelineOSRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
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

        // ---- Agendamento + OS de exemplo (concluida, com timeline e nota fiscal) ----
        Agendamento agendamento = agendamentoRepository.save(Agendamento.builder()
                .cliente(cliente)
                .tecnico(tecnico)
                .tipoAparelho("Geladeira Brastemp")
                .descricaoProblema("Nao gela e faz ruido no compressor.")
                .endereco("Rua das Acacias, 123")
                .bairro("Moema")
                .dataHora(LocalDateTime.now().minusDays(5).withHour(10).withMinute(0))
                .status(StatusAgendamento.CONCLUIDO)
                .prioridadeGas(false)
                .build());
        agendamento.setProtocolo("DITEC-" + java.time.Year.now() + "-" + String.format("%06d", agendamento.getId()));
        agendamento = agendamentoRepository.save(agendamento);

        OrdemServico os = ordemServicoRepository.save(OrdemServico.builder()
                .agendamento(agendamento)
                .cliente(cliente)
                .tecnico(tecnico)
                .protocolo(agendamento.getProtocolo())
                .descricaoProblema(agendamento.getDescricaoProblema())
                .status(StatusOS.CONCLUIDO)
                .valor(new BigDecimal("171.00"))
                .formaPagamento("pix")
                .desconto(new BigDecimal("0.05"))
                .pecasUtilizadas("Compressor novo")
                .descricaoFinal("Compressor substituido. Testado e funcionando normalmente.")
                .dataInicio(LocalDateTime.now().minusDays(5).withHour(10).withMinute(30))
                .dataConclusao(LocalDateTime.now().minusDays(5).withHour(13).withMinute(0))
                .garantiaDias(90)
                .notaFiscalNumero("NF-000123")
                .build());

        timelineOSRepository.save(TimelineOS.builder().ordemServico(os).statusAnterior(null).statusNovo("AGENDADO").observacao("Agendamento recebido pelo site.").alteradoPor("sistema").dataHora(agendamento.getDataHora().minusHours(2)).build());
        timelineOSRepository.save(TimelineOS.builder().ordemServico(os).statusAnterior("AGENDADO").statusNovo("EM_ATENDIMENTO").observacao("Tecnico a caminho.").alteradoPor(tecnico.getNome()).dataHora(os.getDataInicio()).build());
        timelineOSRepository.save(TimelineOS.builder().ordemServico(os).statusAnterior("EM_ATENDIMENTO").statusNovo("CONCLUIDO").observacao("Atendimento finalizado. Garantia de 90 dias ativada.").alteradoPor(tecnico.getNome()).dataHora(os.getDataConclusao()).build());

        log.warn(" Protocolo de teste para rastreamento: {}", agendamento.getProtocolo());
        log.warn(" Login admin:    admin@ditec.com.br    / admin123");
        log.warn(" Login tecnico:  tecnico@ditec.com.br   / tecnico123");
        log.warn(" Login cliente:  cliente@ditec.com.br   / cliente123");
        log.warn("==========================================================");
    }
}
