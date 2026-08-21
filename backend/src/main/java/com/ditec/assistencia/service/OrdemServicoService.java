package com.ditec.assistencia.service;

import com.ditec.assistencia.dto.os.*;
import com.ditec.assistencia.entity.*;
import com.ditec.assistencia.enums.StatusAgendamento;
import com.ditec.assistencia.enums.StatusOS;
import com.ditec.assistencia.enums.TipoUsuario;
import com.ditec.assistencia.exception.AcessoNegadoException;
import com.ditec.assistencia.exception.ConflitoException;
import com.ditec.assistencia.exception.RecursoNaoEncontradoException;
import com.ditec.assistencia.exception.RegraDeNegocioException;
import com.ditec.assistencia.repository.*;
import com.ditec.assistencia.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrdemServicoService {

    private static final BigDecimal DESCONTO_PAGAMENTO_VISTA = new BigDecimal("0.05");
    private static final List<String> FORMAS_PAGAMENTO_VISTA = List.of("dinheiro", "pix");

    private final OrdemServicoRepository ordemServicoRepository;
    private final TimelineOSRepository timelineOSRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final AvaliacaoRepository avaliacaoRepository;
    private final ClienteRepository clienteRepository;
    private final TecnicoRepository tecnicoRepository;

    // ---------------------------------------------------------------
    // Rastreamento publico (por protocolo — nao exige login)
    // ---------------------------------------------------------------
    public OrdemServicoResponse buscarPorProtocolo(String protocolo) {
        OrdemServico os = ordemServicoRepository.findByProtocolo(protocolo.trim().toUpperCase())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Protocolo nao encontrado."));
        List<TimelineOS> timeline = timelineOSRepository.findByOrdemServico_IdOrderByDataHoraAsc(os.getId());
        return OrdemServicoMapper.toResponse(os, timeline);
    }

    public OrdemServicoResponse buscarPorId(Long id, Authentication auth) {
        OrdemServico os = getOuFalhar(id);
        garantirDonoTecnicoOuAdmin(os, auth);
        List<TimelineOS> timeline = timelineOSRepository.findByOrdemServico_IdOrderByDataHoraAsc(os.getId());
        return OrdemServicoMapper.toResponse(os, timeline);
    }

    public List<OrdemServicoResponse> listarMinhas(Authentication auth) {
        CustomUserDetails cud = exigirAutenticado(auth);
        if (cud.getUsuario().getTipo() == TipoUsuario.TECNICO) {
            return ordemServicoRepository.findByTecnico_IdOrderByCriadoEmDesc(cud.getId()).stream()
                    .map(this::comTimeline).toList();
        }
        var cliente = clienteRepository.findByUsuario_Id(cud.getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cadastro de cliente nao encontrado."));
        return ordemServicoRepository.findByCliente_IdOrderByCriadoEmDesc(cliente.getId()).stream()
                .map(this::comTimeline).toList();
    }

    public List<OrdemServicoResponse> listarTodasAdmin() {
        return ordemServicoRepository.findAll().stream().map(this::comTimeline).toList();
    }

    // ---------------------------------------------------------------
    // Progressao de status (tecnico/admin) — sem regressao (DRS secao 23)
    // ---------------------------------------------------------------
    @Transactional
    public OrdemServicoResponse atualizarStatus(Long id, StatusUpdateRequest req, Authentication auth) {
        OrdemServico os = getOuFalhar(id);
        garantirTecnicoDaOSOuAdmin(os, auth);
        assumirSeAindaSemTecnico(os, auth);

        StatusOS novo;
        try {
            novo = StatusOS.valueOf(req.status().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new RegraDeNegocioException("Status invalido. Use AGENDADO, EM_ATENDIMENTO, CONCLUIDO ou CANCELADO.");
        }

        if (!os.getStatus().podeTransicionarPara(novo)) {
            throw new RegraDeNegocioException("Nao e' possivel mudar de " + os.getStatus() + " para " + novo + ".");
        }

        StatusOS anterior = os.getStatus();
        os.setStatus(novo);
        if (novo == StatusOS.EM_ATENDIMENTO && os.getDataInicio() == null) os.setDataInicio(LocalDateTime.now());
        ordemServicoRepository.save(os);

        sincronizarAgendamento(os, novo);

        timelineOSRepository.save(TimelineOS.builder()
                .ordemServico(os).statusAnterior(anterior.name()).statusNovo(novo.name())
                .observacao(req.observacao()).alteradoPor(nomeDoAutenticado(auth)).build());

        return comTimeline(os);
    }

    // ---------------------------------------------------------------
    // Finalizacao (tecnico/admin) — valor, pecas, garantia, desconto a vista
    // ---------------------------------------------------------------
    @Transactional
    public OrdemServicoResponse finalizar(Long id, FinalizarOSRequest req, Authentication auth) {
        OrdemServico os = getOuFalhar(id);
        garantirTecnicoDaOSOuAdmin(os, auth);
        assumirSeAindaSemTecnico(os, auth);

        if (os.getStatus() != StatusOS.EM_ATENDIMENTO) {
            throw new RegraDeNegocioException("Só e' possivel finalizar uma OS que esteja EM_ATENDIMENTO.");
        }

        BigDecimal desconto = BigDecimal.ZERO;
        BigDecimal valorFinal = req.valor();
        if (FORMAS_PAGAMENTO_VISTA.contains(RegrasNegocioService.normalizar(req.formaPagamento()))) {
            desconto = DESCONTO_PAGAMENTO_VISTA;
            valorFinal = req.valor().multiply(BigDecimal.ONE.subtract(desconto)).setScale(2, RoundingMode.HALF_UP);
        }

        os.setValor(valorFinal);
        os.setFormaPagamento(req.formaPagamento());
        os.setDesconto(desconto);
        os.setPecasUtilizadas(req.pecasUtilizadas());
        os.setDescricaoFinal(req.descricaoFinal());
        os.setNotaFiscalNumero(req.notaFiscalNumero());
        os.setDataConclusao(LocalDateTime.now());
        os.setGarantiaDias(90);
        StatusOS anterior = os.getStatus();
        os.setStatus(StatusOS.CONCLUIDO);
        ordemServicoRepository.save(os);

        sincronizarAgendamento(os, StatusOS.CONCLUIDO);

        timelineOSRepository.save(TimelineOS.builder()
                .ordemServico(os).statusAnterior(anterior.name()).statusNovo(StatusOS.CONCLUIDO.name())
                .observacao("Atendimento finalizado. Garantia de 90 dias ativada.")
                .alteradoPor(nomeDoAutenticado(auth)).build());

        return comTimeline(os);
    }

    // ---------------------------------------------------------------
    // Avaliacao (cliente, OS concluida)
    // ---------------------------------------------------------------
    @Transactional
    public void avaliar(Long id, AvaliacaoRequest req, Authentication auth) {
        OrdemServico os = getOuFalhar(id);
        CustomUserDetails cud = exigirAutenticado(auth);
        var cliente = clienteRepository.findByUsuario_Id(cud.getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cadastro de cliente nao encontrado."));

        boolean dono = os.getCliente() != null && os.getCliente().getId().equals(cliente.getId());
        if (!dono) throw new AcessoNegadoException("Esta Ordem de Servico nao pertence a voce.");
        if (os.getStatus() != StatusOS.CONCLUIDO) {
            throw new RegraDeNegocioException("So e' possivel avaliar um atendimento ja concluido.");
        }
        if (avaliacaoRepository.findByOrdemServico_Id(os.getId()).isPresent()) {
            throw new ConflitoException("Este atendimento ja foi avaliado.");
        }

        avaliacaoRepository.save(Avaliacao.builder()
                .ordemServico(os).cliente(cliente).nota(req.nota()).comentario(req.comentario()).build());
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------
    private void sincronizarAgendamento(OrdemServico os, StatusOS novoStatusOS) {
        Agendamento ag = os.getAgendamento();
        StatusAgendamento novoStatusAg = switch (novoStatusOS) {
            case EM_ATENDIMENTO -> StatusAgendamento.EM_ATENDIMENTO;
            case CONCLUIDO -> StatusAgendamento.CONCLUIDO;
            case CANCELADO -> StatusAgendamento.CANCELADO;
            case AGENDADO -> StatusAgendamento.AGENDADO;
        };
        ag.setStatus(novoStatusAg);
        agendamentoRepository.save(ag);
    }

    private OrdemServico getOuFalhar(Long id) {
        return ordemServicoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ordem de Servico nao encontrada."));
    }

    private OrdemServicoResponse comTimeline(OrdemServico os) {
        List<TimelineOS> timeline = timelineOSRepository.findByOrdemServico_IdOrderByDataHoraAsc(os.getId());
        return OrdemServicoMapper.toResponse(os, timeline);
    }

    private CustomUserDetails exigirAutenticado(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails cud)) {
            throw new AcessoNegadoException("Autenticacao necessaria.");
        }
        return cud;
    }

    private void garantirDonoTecnicoOuAdmin(OrdemServico os, Authentication auth) {
        CustomUserDetails cud = exigirAutenticado(auth);
        TipoUsuario tipo = cud.getUsuario().getTipo();
        if (tipo == TipoUsuario.ADMINISTRADOR) return;
        if (tipo == TipoUsuario.TECNICO && os.getTecnico() != null
                && os.getTecnico().getUsuario().getId().equals(cud.getId())) return;
        if (tipo == TipoUsuario.CLIENTE && os.getCliente() != null
                && os.getCliente().getUsuario().getId().equals(cud.getId())) return;
        throw new AcessoNegadoException("Esta Ordem de Servico nao pertence a voce.");
    }

    /**
     * DECISAO TECNICA: um tecnico so pode alterar uma OS que ja e' dele, OU
     * uma OS que ainda nao tem tecnico atribuido (nesse caso ele "assume" o
     * atendimento — ver assumirSeAindaSemTecnico). Isso satisfaz a regra do
     * DRS de que o tecnico trabalha sobre OS atribuidas, sem exigir uma tela
     * extra de distribuicao manual (o admin ainda pode atribuir previamente
     * via PUT /api/admin/agendamentos/{id}/tecnico, se preferir).
     */
    private void garantirTecnicoDaOSOuAdmin(OrdemServico os, Authentication auth) {
        CustomUserDetails cud = exigirAutenticado(auth);
        TipoUsuario tipo = cud.getUsuario().getTipo();
        if (tipo == TipoUsuario.ADMINISTRADOR) return;
        if (tipo == TipoUsuario.TECNICO) {
            boolean semTecnico = os.getTecnico() == null;
            boolean ehODono = os.getTecnico() != null && os.getTecnico().getUsuario().getId().equals(cud.getId());
            if (semTecnico || ehODono) return;
        }
        throw new AcessoNegadoException("Esta Ordem de Servico ja esta atribuida a outro tecnico.");
    }

    private void assumirSeAindaSemTecnico(OrdemServico os, Authentication auth) {
        if (os.getTecnico() != null) return;
        CustomUserDetails cud = exigirAutenticado(auth);
        if (cud.getUsuario().getTipo() != TipoUsuario.TECNICO) return;
        tecnicoRepository.findByUsuario_Id(cud.getId()).ifPresent(tecnico -> {
            os.setTecnico(tecnico);
            os.getAgendamento().setTecnico(tecnico);
            agendamentoRepository.save(os.getAgendamento());
        });
    }

    private String nomeDoAutenticado(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails cud) return cud.getUsuario().getNome();
        return "sistema";
    }
}
