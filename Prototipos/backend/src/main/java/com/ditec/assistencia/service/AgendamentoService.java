package com.ditec.assistencia.service;

import com.ditec.assistencia.dto.agendamento.AgendamentoCreateRequest;
import com.ditec.assistencia.dto.agendamento.AgendamentoResponse;
import com.ditec.assistencia.dto.agendamento.AgendamentoUpdateRequest;
import com.ditec.assistencia.dto.agendamento.DisponibilidadeResponse;
import com.ditec.assistencia.entity.*;
import com.ditec.assistencia.enums.StatusAgendamento;
import com.ditec.assistencia.enums.StatusOS;
import com.ditec.assistencia.exception.AcessoNegadoException;
import com.ditec.assistencia.exception.ConflitoException;
import com.ditec.assistencia.exception.RecursoNaoEncontradoException;
import com.ditec.assistencia.exception.RegraDeNegocioException;
import com.ditec.assistencia.repository.*;
import com.ditec.assistencia.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgendamentoService {

    private final AgendamentoRepository agendamentoRepository;
    private final OrdemServicoRepository ordemServicoRepository;
    private final ClienteRepository clienteRepository;
    private final ServicoRepository servicoRepository;
    private final TecnicoRepository tecnicoRepository;
    private final RegrasNegocioService regras;
    private final ProtocoloService protocoloService;
    private final OrdemServicoService ordemServicoService;

    private static final DateTimeFormatter HORA_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATA_HORA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy 'as' HH:mm");

    // ---------------------------------------------------------------
    // Disponibilidade (usada pelo calendario antes de agendar)
    // ---------------------------------------------------------------
    public DisponibilidadeResponse disponibilidade(LocalDate data) {
        boolean diaAtendido = regras.isDiaAtendido(data) && !data.isBefore(LocalDate.now());
        if (!diaAtendido) {
            return new DisponibilidadeResponse(data, false, List.of(), List.of());
        }

        LocalDateTime inicio = data.atStartOfDay();
        LocalDateTime fim = data.atTime(LocalTime.MAX);
        Set<LocalTime> ocupados = agendamentoRepository.findHorariosOcupadosEntre(inicio, fim)
                .stream().map(LocalDateTime::toLocalTime).collect(Collectors.toSet());

        List<String> disponiveis = new ArrayList<>();
        List<String> ocupadosStr = new ArrayList<>();
        for (LocalTime h : RegrasNegocioService.HORARIOS_PERMITIDOS) {
            boolean passado = data.isEqual(LocalDate.now()) && h.isBefore(LocalTime.now());
            if (ocupados.contains(h) || passado) ocupadosStr.add(HORA_FMT.format(h));
            else disponiveis.add(HORA_FMT.format(h));
        }
        return new DisponibilidadeResponse(data, true, disponiveis, ocupadosStr);
    }

    // ---------------------------------------------------------------
    // Criacao (aceita visitante OU cliente autenticado)
    // ---------------------------------------------------------------
    @Transactional
    public AgendamentoResponse criar(AgendamentoCreateRequest req, Authentication auth) {
        if (!regras.isBairroAtendido(req.bairro())) {
            throw new RegraDeNegocioException("Ainda nao atendemos esse bairro. Fale com a gente pelo WhatsApp.");
        }
        LocalDate dia = req.dataHora().toLocalDate();
        LocalTime hora = req.dataHora().toLocalTime();
        if (!regras.isDiaAtendido(dia)) {
            throw new RegraDeNegocioException("Nao atendemos aos domingos. Escolha outro dia.");
        }
        if (!regras.isHorarioPermitido(hora)) {
            throw new RegraDeNegocioException("Horario invalido. Escolha um dos horarios disponiveis no calendario.");
        }
        if (!agendamentoRepository.findAtivosNoHorario(req.dataHora()).isEmpty()) {
            throw new ConflitoException("Horario indisponivel.");
        }

        Agendamento.AgendamentoBuilder builder = Agendamento.builder()
                .tipoAparelho(req.tipoAparelho().trim())
                .descricaoProblema(req.descricaoProblema())
                .endereco(req.endereco().trim())
                .bairro(req.bairro().trim())
                .dataHora(req.dataHora())
                .status(StatusAgendamento.PENDENTE)
                .prioridadeGas(regras.isAparelhoGas(req.tipoAparelho()));

        Cliente cliente = clienteAutenticadoOuNull(auth);
        if (cliente != null) {
            builder.cliente(cliente);
        } else {
            builder.nomeContato(req.nome().trim()).telefoneContato(req.telefone().trim());
        }

        if (req.servicoId() != null) {
            servicoRepository.findById(req.servicoId()).ifPresent(builder::servico);
        }

        Agendamento agendamento = agendamentoRepository.save(builder.build());
        agendamento.setProtocolo(protocoloService.gerar(agendamento.getId()));
        agendamento = agendamentoRepository.save(agendamento);

        criarOrdemServicoInicial(agendamento, cliente);

        return AgendamentoMapper.toResponse(agendamento);
    }

    private void criarOrdemServicoInicial(Agendamento agendamento, Cliente cliente) {
        OrdemServico os = ordemServicoRepository.save(OrdemServico.builder()
                .agendamento(agendamento)
                .cliente(cliente)
                .protocolo(agendamento.getProtocolo())
                .descricaoProblema(agendamento.getDescricaoProblema())
                .status(StatusOS.AGENDADO)
                .garantiaDias(90)
                .build());
        ordemServicoService.registrarAbertura(os, agendamento.isPrioridadeGas()
                ? "Agendamento recebido pelo site. Aparelho a gas: atendimento prioritario."
                : "Agendamento recebido pelo site. Aguardando avaliacao tecnica.");
    }

    // ---------------------------------------------------------------
    // Consulta / alteracao / cancelamento (dono do agendamento ou admin)
    // ---------------------------------------------------------------
    public AgendamentoResponse buscarPorId(Long id, Authentication auth) {
        Agendamento a = agendamentoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Agendamento nao encontrado."));
        garantirDonoOuAdmin(a, auth);
        return AgendamentoMapper.toResponse(a);
    }

    @Transactional
    public AgendamentoResponse atualizar(Long id, AgendamentoUpdateRequest req, Authentication auth) {
        Agendamento a = agendamentoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Agendamento nao encontrado."));
        garantirDonoOuAdmin(a, auth);

        if (!a.getStatus().isElegivelParaAlteracaoOuCancelamento()) {
            throw new RegraDeNegocioException("Este agendamento nao pode mais ser alterado (ja esta " +
                    a.getStatus().name().toLowerCase() + ").");
        }
        if (!regras.isBairroAtendido(req.bairro())) {
            throw new RegraDeNegocioException("Ainda nao atendemos esse bairro.");
        }

        if (req.dataHora() != null && !req.dataHora().equals(a.getDataHora())) {
            LocalDate dia = req.dataHora().toLocalDate();
            LocalTime hora = req.dataHora().toLocalTime();
            if (!regras.isDiaAtendido(dia)) throw new RegraDeNegocioException("Nao atendemos aos domingos.");
            if (!regras.isHorarioPermitido(hora)) throw new RegraDeNegocioException("Horario invalido.");
            boolean conflito = agendamentoRepository.findAtivosNoHorario(req.dataHora()).stream()
                    .anyMatch(outro -> !outro.getId().equals(a.getId()));
            if (conflito) throw new ConflitoException("Horario indisponivel.");
            a.setDataHora(req.dataHora());
        }

        a.setTipoAparelho(req.tipoAparelho().trim());
        a.setDescricaoProblema(req.descricaoProblema());
        a.setEndereco(req.endereco().trim());
        a.setBairro(req.bairro().trim());
        a.setPrioridadeGas(regras.isAparelhoGas(req.tipoAparelho()));

        agendamentoRepository.save(a);

        ordemServicoRepository.findByAgendamento_Id(a.getId()).ifPresent(os -> {
            os.setDescricaoProblema(a.getDescricaoProblema());
            ordemServicoRepository.save(os);
        });

        return AgendamentoMapper.toResponse(a);
    }

    @Transactional
    public void cancelar(Long id, Authentication auth) {
        Agendamento a = agendamentoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Agendamento nao encontrado."));
        garantirDonoOuAdmin(a, auth);

        if (!a.getStatus().isElegivelParaAlteracaoOuCancelamento()) {
            throw new RegraDeNegocioException("Este agendamento nao pode mais ser cancelado (ja esta " +
                    a.getStatus().name().toLowerCase() + ").");
        }
        OrdemServico os = ordemServicoRepository.findByAgendamento_Id(a.getId()).orElse(null);
        if (os != null) {
            // muda OS + agendamento juntos e grava o historico
            ordemServicoService.cancelarPorAgendamento(os, "Cancelado a pedido do cliente.", (CustomUserDetails) auth.getPrincipal());
        } else {
            a.setStatus(StatusAgendamento.CANCELADO);
            agendamentoRepository.save(a);
        }
    }

    // ---------------------------------------------------------------
    // Admin
    // ---------------------------------------------------------------
    /** Lista do admin: aparelhos a gas primeiro (prioridade), depois por data. */
    public Page<AgendamentoResponse> listarTodos(Pageable pageable) {
        return agendamentoRepository.findAllByOrderByPrioridadeGasDescDataHoraAsc(pageable).map(AgendamentoMapper::toResponse);
    }

    @Transactional
    public AgendamentoResponse atribuirTecnico(Long id, Long tecnicoId, Authentication auth) {
        Agendamento a = agendamentoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Agendamento nao encontrado."));
        if (a.getStatus() == StatusAgendamento.CANCELADO || a.getStatus() == StatusAgendamento.CONCLUIDO) {
            throw new RegraDeNegocioException("Nao e' possivel atribuir tecnico a um agendamento cancelado ou concluido.");
        }
        OrdemServico os = ordemServicoRepository.findByAgendamento_Id(a.getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ordem de Servico do agendamento nao encontrada."));
        ordemServicoService.aplicarTecnico(os, tecnicoId, (CustomUserDetails) auth.getPrincipal());
        return AgendamentoMapper.toResponse(a);
    }

    @Transactional
    public AgendamentoResponse confirmar(Long id, Authentication auth) {
        Agendamento a = agendamentoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Agendamento nao encontrado."));
        if (a.getStatus() != StatusAgendamento.PENDENTE) {
            throw new RegraDeNegocioException("Somente agendamentos pendentes podem ser confirmados.");
        }
        a.setStatus(StatusAgendamento.AGENDADO);
        agendamentoRepository.save(a);
        ordemServicoRepository.findByAgendamento_Id(a.getId()).ifPresent(os ->
                ordemServicoService.registrarConfirmacaoVisita(os,
                        "Visita tecnica confirmada para " + DATA_HORA_BR.format(a.getDataHora()) + ".",
                        (CustomUserDetails) auth.getPrincipal()));
        return AgendamentoMapper.toResponse(a);
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------
    private Cliente clienteAutenticadoOuNull(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof CustomUserDetails cud)) {
            return null;
        }
        if (cud.getUsuario().getTipo() != com.ditec.assistencia.enums.TipoUsuario.CLIENTE) return null;
        return clienteRepository.findByUsuario_Id(cud.getId()).orElse(null);
    }

    private void garantirDonoOuAdmin(Agendamento a, Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails cud)) {
            throw new AcessoNegadoException("Autenticacao necessaria.");
        }
        boolean isAdmin = cud.getUsuario().getTipo() == com.ditec.assistencia.enums.TipoUsuario.ADMINISTRADOR;
        boolean isDono = a.getCliente() != null && a.getCliente().getUsuario().getId().equals(cud.getId());
        if (!isAdmin && !isDono) {
            throw new AcessoNegadoException("Este agendamento nao pertence a voce.");
        }
    }

    private String nomeDoAutenticado(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails cud) return cud.getUsuario().getNome();
        return "cliente";
    }
}
