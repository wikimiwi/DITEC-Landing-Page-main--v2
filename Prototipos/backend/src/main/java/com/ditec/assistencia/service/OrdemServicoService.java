package com.ditec.assistencia.service;

import com.ditec.assistencia.dto.os.*;
import com.ditec.assistencia.entity.*;
import com.ditec.assistencia.enums.StatusAgendamento;
import com.ditec.assistencia.enums.StatusConta;
import com.ditec.assistencia.enums.StatusOS;
import com.ditec.assistencia.enums.TipoEventoOS;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/**
 * Regras da Ordem de Servico: rastreamento, ciclo de vida (ver StatusOS),
 * diagnostico, orcamento, previsao, observacoes, finalizacao e historico.
 *
 * Toda alteracao relevante gera um registro em timeline_os — o historico
 * nunca e' apagado nem sobrescrito.
 *
 * Leitura em transacao somente-leitura: com open-in-view desligado, sem a
 * transacao os relacionamentos LAZY estouravam LazyInitializationException
 * (era a causa do HTTP 500 no rastreamento).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrdemServicoService {

    private static final BigDecimal DESCONTO_PAGAMENTO_VISTA = new BigDecimal("0.05");
    private static final Set<String> FORMAS_PAGAMENTO_VISTA = Set.of("dinheiro", "pix");
    private static final Set<String> FORMAS_PAGAMENTO_VALIDAS =
            Set.of("dinheiro", "pix", "cartao", "cartao_credito", "cartao_debito", "boleto");
    private static final int GARANTIA_DIAS = 90;
    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final OrdemServicoRepository ordemServicoRepository;
    private final TimelineOSRepository timelineOSRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final AvaliacaoRepository avaliacaoRepository;
    private final ClienteRepository clienteRepository;
    private final TecnicoRepository tecnicoRepository;
    private final UsuarioRepository usuarioRepository;

    // ---------------------------------------------------------------
    // Rastreamento publico (protocolo + telefone do agendamento)
    // ---------------------------------------------------------------
    public OrdemServicoRastreioResponse rastrear(RastreioRequest req) {
        String protocolo = req.protocolo().trim().toUpperCase();
        // Mesma resposta para "nao existe" e "telefone nao confere": nao revela
        // a terceiros quais protocolos existem.
        RecursoNaoEncontradoException naoEncontrada = new RecursoNaoEncontradoException(
                "Nao encontramos uma OS com esse protocolo e telefone. Confira os dados e tente novamente.");
        OrdemServico os = ordemServicoRepository.findByProtocolo(protocolo).orElseThrow(() -> naoEncontrada);
        if (!telefoneConfere(os.getAgendamento().getTelefoneExibicao(), req.telefone())) {
            throw naoEncontrada;
        }
        return OrdemServicoMapper.toRastreioResponse(os, historico(os));
    }

    /**
     * Compara pelos ultimos 8 digitos (ignora mascara, DDI 55 e DDD), exigindo
     * ao menos 8 digitos informados.
     */
    static boolean telefoneConfere(String telefoneCadastrado, String telefoneInformado) {
        if (telefoneCadastrado == null || telefoneInformado == null) return false;
        String cad = telefoneCadastrado.replaceAll("\\D", "");
        String inf = telefoneInformado.replaceAll("\\D", "");
        if (cad.length() < 8 || inf.length() < 8) return false;
        return cad.substring(cad.length() - 8).equals(inf.substring(inf.length() - 8));
    }

    // ---------------------------------------------------------------
    // Consultas autenticadas
    // ---------------------------------------------------------------
    public OrdemServicoResponse buscarPorId(Long id, Authentication auth) {
        OrdemServico os = getOuFalhar(id);
        CustomUserDetails cud = garantirDonoTecnicoOuAdmin(os, auth);
        boolean equipe = cud.getUsuario().getTipo() != TipoUsuario.CLIENTE;
        return OrdemServicoMapper.toResponse(os, historico(os), equipe, equipe);
    }

    public List<OrdemServicoResponse> listarMinhas(Authentication auth) {
        CustomUserDetails cud = exigirAutenticado(auth);
        if (cud.getUsuario().getTipo() == TipoUsuario.TECNICO) {
            // cud.getId() e' o id do USUARIO; a FK tecnico_id aponta para a entidade Tecnico.
            Tecnico tecnico = tecnicoRepository.findByUsuario_Id(cud.getId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Cadastro de tecnico nao encontrado."));
            return ordemServicoRepository.findByTecnico_IdOrderByCriadoEmDesc(tecnico.getId()).stream()
                    .map(os -> OrdemServicoMapper.toResponse(os, null, true, true)).toList();
        }
        Cliente cliente = clienteRepository.findByUsuario_Id(cud.getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cadastro de cliente nao encontrado."));
        return ordemServicoRepository.findByCliente_IdOrderByCriadoEmDesc(cliente.getId()).stream()
                .map(os -> OrdemServicoMapper.toResponse(os, historico(os), false, false)).toList();
    }

    /** Listagem do administrador com filtros opcionais (status, tecnico, protocolo, nome do cliente). */
    public List<OrdemServicoResponse> listarAdmin(String status, Long tecnicoId, String protocolo, String cliente) {
        StatusOS filtroStatus = (status == null || status.isBlank()) ? null : parseStatus(status);
        return ordemServicoRepository.buscarAdmin(filtroStatus, tecnicoId, vazioParaNull(protocolo), vazioParaNull(cliente))
                .stream().map(os -> OrdemServicoMapper.toResponse(os, null, true, true)).toList();
    }

    // ---------------------------------------------------------------
    // Mudanca de status (tecnico/admin)
    // ---------------------------------------------------------------
    @Transactional
    public OrdemServicoResponse atualizarStatus(Long id, StatusUpdateRequest req, Authentication auth) {
        OrdemServico os = getOuFalhar(id);
        CustomUserDetails cud = garantirTecnicoDaOSOuAdmin(os, auth);
        StatusOS atual = os.getStatus();
        StatusOS novo = parseStatus(req.status());
        String observacao = textoOuNull(req.observacao());

        if (novo == StatusOS.CONCLUIDO) {
            throw new RegraDeNegocioException("Para concluir a OS use a finalizacao (valor, pagamento e descricao do servico).");
        }
        if (!atual.podeTransicionarPara(novo)) {
            throw new RegraDeNegocioException("Nao e' possivel mudar de \"" + atual.getDescricao()
                    + "\" para \"" + novo.getDescricao() + "\".");
        }
        if (novo == StatusOS.CANCELADO) {
            if (cud.getUsuario().getTipo() != TipoUsuario.ADMINISTRADOR) {
                throw new AcessoNegadoException("Somente o administrador pode cancelar uma OS.");
            }
            if (observacao == null) throw new RegraDeNegocioException("Informe o motivo do cancelamento.");
        }
        if (atual == StatusOS.AGENDADO && os.getAgendamento().getStatus() == StatusAgendamento.PENDENTE
                && novo != StatusOS.CANCELADO) {
            throw new RegraDeNegocioException("O agendamento ainda esta pendente. Confirme-o antes de iniciar o atendimento.");
        }
        if (atual == StatusOS.EM_DIAGNOSTICO && novo != StatusOS.CANCELADO && textoOuNull(os.getDiagnostico()) == null) {
            throw new RegraDeNegocioException("Registre o diagnostico antes de avancar a OS.");
        }
        if (novo == StatusOS.AGUARDANDO_APROVACAO) {
            if (os.getValorOrcamento() == null) {
                throw new RegraDeNegocioException("Informe o valor do orcamento (no diagnostico) antes de envia-lo ao cliente.");
            }
            os.setOrcamentoAprovado(null);
        }
        if (atual == StatusOS.AGUARDANDO_APROVACAO && novo != StatusOS.CANCELADO) {
            // Equipe registrando uma aprovacao recebida por outro canal (telefone/WhatsApp).
            os.setOrcamentoAprovado(true);
        }

        assumirSeAindaSemTecnico(os, cud);
        if (novo == StatusOS.EM_DIAGNOSTICO && os.getDataInicio() == null) os.setDataInicio(LocalDateTime.now());
        if (novo == StatusOS.ENTREGUE) os.setDataEntrega(LocalDateTime.now());

        mudarStatus(os, novo, observacao != null ? observacao : "Status alterado para " + novo.getDescricao() + ".", cud);
        return detalheEquipe(os);
    }

    // ---------------------------------------------------------------
    // Diagnostico / previsao / observacoes (tecnico/admin)
    // ---------------------------------------------------------------
    @Transactional
    public OrdemServicoResponse registrarDiagnostico(Long id, DiagnosticoRequest req, Authentication auth) {
        OrdemServico os = getOuFalhar(id);
        CustomUserDetails cud = garantirTecnicoDaOSOuAdmin(os, auth);
        if (os.getStatus() == StatusOS.AGENDADO || os.getStatus().isServicoConcluido() || os.getStatus().isTerminal()) {
            throw new RegraDeNegocioException("O diagnostico so pode ser registrado com a OS em andamento (a partir de \"Em diagnostico\").");
        }
        if (os.getStatus() == StatusOS.AGUARDANDO_APROVACAO && req.valorOrcamento() != null
                && os.getValorOrcamento() != null && req.valorOrcamento().compareTo(os.getValorOrcamento()) != 0) {
            throw new RegraDeNegocioException("O orcamento ja foi enviado ao cliente e nao pode ser alterado enquanto aguarda aprovacao.");
        }
        validarPrevisao(req.previsaoConclusao());
        assumirSeAindaSemTecnico(os, cud);

        os.setDiagnostico(req.diagnostico().trim());
        StringBuilder texto = new StringBuilder("Diagnostico: ").append(req.diagnostico().trim());
        if (req.valorOrcamento() != null) {
            os.setValorOrcamento(req.valorOrcamento().setScale(2, RoundingMode.HALF_UP));
            texto.append(" | Orcamento: R$ ").append(os.getValorOrcamento().toPlainString().replace('.', ','));
        }
        if (req.previsaoConclusao() != null) {
            os.setPrevisaoConclusao(req.previsaoConclusao());
            texto.append(" | Previsao: ").append(DATA_BR.format(req.previsaoConclusao()));
        }
        ordemServicoRepository.save(os);
        registrarEvento(os, TipoEventoOS.DIAGNOSTICO, os.getStatus(), os.getStatus(), limitar(texto.toString()), true, cud);
        return detalheEquipe(os);
    }

    @Transactional
    public OrdemServicoResponse atualizarPrevisao(Long id, PrevisaoRequest req, Authentication auth) {
        OrdemServico os = getOuFalhar(id);
        CustomUserDetails cud = garantirTecnicoDaOSOuAdmin(os, auth);
        if (os.getStatus().isServicoConcluido() || os.getStatus().isTerminal()) {
            throw new RegraDeNegocioException("A previsao so pode ser alterada enquanto o servico nao foi concluido.");
        }
        if (req.previsaoConclusao() == null) throw new RegraDeNegocioException("Informe a data prevista.");
        validarPrevisao(req.previsaoConclusao());
        os.setPrevisaoConclusao(req.previsaoConclusao());
        ordemServicoRepository.save(os);
        String obs = "Previsao de conclusao: " + DATA_BR.format(req.previsaoConclusao())
                + (textoOuNull(req.observacao()) != null ? " — " + req.observacao().trim() : "");
        registrarEvento(os, TipoEventoOS.PREVISAO, os.getStatus(), os.getStatus(), limitar(obs), true, cud);
        return detalheEquipe(os);
    }

    @Transactional
    public OrdemServicoResponse adicionarObservacao(Long id, ObservacaoRequest req, Authentication auth) {
        OrdemServico os = getOuFalhar(id);
        CustomUserDetails cud = garantirTecnicoDaOSOuAdmin(os, auth);
        boolean visivel = req.visivelCliente() == null || req.visivelCliente();
        registrarEvento(os, TipoEventoOS.OBSERVACAO, os.getStatus(), os.getStatus(), req.texto().trim(), visivel, cud);
        os.setAtualizadoEm(LocalDateTime.now()); // "ultima atualizacao" reflete a nova observacao
        ordemServicoRepository.save(os);
        return detalheEquipe(os);
    }

    // ---------------------------------------------------------------
    // Orcamento (resposta do cliente dono da OS)
    // ---------------------------------------------------------------
    @Transactional
    public OrdemServicoResponse responderOrcamento(Long id, OrcamentoRespostaRequest req, Authentication auth) {
        OrdemServico os = getOuFalhar(id);
        CustomUserDetails cud = exigirAutenticado(auth);
        if (!ehDono(os, cud)) throw new AcessoNegadoException("Esta Ordem de Servico nao pertence a voce.");
        if (os.getStatus() != StatusOS.AGUARDANDO_APROVACAO) {
            throw new RegraDeNegocioException("Esta OS nao esta aguardando aprovacao de orcamento.");
        }
        String extra = textoOuNull(req.observacao()) != null ? " Observacao: " + req.observacao().trim() : "";
        if (Boolean.TRUE.equals(req.aprovado())) {
            os.setOrcamentoAprovado(true);
            mudarStatus(os, StatusOS.EM_MANUTENCAO, limitar("Orcamento aprovado pelo cliente." + extra), cud);
        } else {
            os.setOrcamentoAprovado(false);
            mudarStatus(os, StatusOS.CANCELADO,
                    limitar("Orcamento recusado pelo cliente (diagnostico gratuito, sem custo)." + extra), cud);
        }
        return OrdemServicoMapper.toResponse(os, historico(os), false, false);
    }

    // ---------------------------------------------------------------
    // Finalizacao (tecnico/admin) — valor, pecas, garantia, desconto a vista
    // ---------------------------------------------------------------
    @Transactional
    public OrdemServicoResponse finalizar(Long id, FinalizarOSRequest req, Authentication auth) {
        OrdemServico os = getOuFalhar(id);
        CustomUserDetails cud = garantirTecnicoDaOSOuAdmin(os, auth);

        if (os.getStatus() != StatusOS.EM_TESTES) {
            throw new RegraDeNegocioException("So e' possivel finalizar uma OS que esteja \"Em testes\".");
        }
        String forma = RegrasNegocioService.normalizar(req.formaPagamento()).replace(' ', '_').replace('-', '_');
        if (!FORMAS_PAGAMENTO_VALIDAS.contains(forma)) {
            throw new RegraDeNegocioException("Forma de pagamento invalida. Use pix, dinheiro, cartao ou boleto.");
        }

        BigDecimal desconto = BigDecimal.ZERO;
        BigDecimal valorFinal = req.valor().setScale(2, RoundingMode.HALF_UP);
        if (FORMAS_PAGAMENTO_VISTA.contains(forma)) {
            desconto = DESCONTO_PAGAMENTO_VISTA;
            valorFinal = req.valor().multiply(BigDecimal.ONE.subtract(desconto)).setScale(2, RoundingMode.HALF_UP);
        }

        os.setValor(valorFinal);
        os.setFormaPagamento(forma);
        os.setDesconto(desconto);
        os.setPecasUtilizadas(textoOuNull(req.pecasUtilizadas()));
        os.setDescricaoFinal(req.descricaoFinal().trim());
        os.setNotaFiscalNumero(textoOuNull(req.notaFiscalNumero()));
        os.setDataConclusao(LocalDateTime.now());
        os.setGarantiaDias(GARANTIA_DIAS);

        mudarStatus(os, StatusOS.CONCLUIDO,
                limitar("Servico concluido: " + req.descricaoFinal().trim() + " Garantia de " + GARANTIA_DIAS + " dias ativada."),
                cud);
        return detalheEquipe(os);
    }

    // ---------------------------------------------------------------
    // Atribuicao de tecnico (admin)
    // ---------------------------------------------------------------
    @Transactional
    public OrdemServicoResponse atribuirTecnico(Long osId, Long tecnicoId, Authentication auth) {
        OrdemServico os = getOuFalhar(osId);
        CustomUserDetails cud = exigirAutenticado(auth);
        aplicarTecnico(os, tecnicoId, cud);
        return detalheEquipe(os);
    }

    /** Usado tambem pela atribuicao via agendamento (AgendamentoService). */
    @Transactional
    public void aplicarTecnico(OrdemServico os, Long tecnicoId, CustomUserDetails cud) {
        if (tecnicoId == null) throw new RegraDeNegocioException("Informe o tecnico.");
        if (os.getStatus().isServicoConcluido() || os.getStatus().isTerminal()) {
            throw new RegraDeNegocioException("Nao e' possivel trocar o tecnico de uma OS concluida, entregue ou cancelada.");
        }
        Tecnico tecnico = tecnicoRepository.findById(tecnicoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tecnico nao encontrado."));
        if (tecnico.getStatus() != StatusConta.ATIVO) {
            throw new RegraDeNegocioException("Este tecnico esta inativo e nao pode receber novas OS.");
        }
        if (os.getTecnico() != null && os.getTecnico().getId().equals(tecnico.getId())) return;
        os.setTecnico(tecnico);
        os.getAgendamento().setTecnico(tecnico);
        agendamentoRepository.save(os.getAgendamento());
        ordemServicoRepository.save(os);
        registrarEvento(os, TipoEventoOS.ATRIBUICAO, os.getStatus(), os.getStatus(),
                "Tecnico responsavel: " + tecnico.getNome() + ".", true, cud);
    }

    // ---------------------------------------------------------------
    // Avaliacao (cliente, servico concluido)
    // ---------------------------------------------------------------
    @Transactional
    public void avaliar(Long id, AvaliacaoRequest req, Authentication auth) {
        OrdemServico os = getOuFalhar(id);
        CustomUserDetails cud = exigirAutenticado(auth);
        Cliente cliente = clienteRepository.findByUsuario_Id(cud.getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cadastro de cliente nao encontrado."));

        if (!ehDono(os, cud)) throw new AcessoNegadoException("Esta Ordem de Servico nao pertence a voce.");
        if (!os.getStatus().isServicoConcluido()) {
            throw new RegraDeNegocioException("So e' possivel avaliar um atendimento ja concluido.");
        }
        if (avaliacaoRepository.findByOrdemServico_Id(os.getId()).isPresent()) {
            throw new ConflitoException("Este atendimento ja foi avaliado.");
        }
        avaliacaoRepository.save(Avaliacao.builder()
                .ordemServico(os).cliente(cliente).nota(req.nota()).comentario(textoOuNull(req.comentario())).build());
    }

    // ---------------------------------------------------------------
    // Uso interno (AgendamentoService)
    // ---------------------------------------------------------------
    /** Cria o primeiro registro do historico quando a OS e' aberta. */
    @Transactional
    public void registrarAbertura(OrdemServico os, String observacao) {
        registrarEvento(os, TipoEventoOS.ABERTURA, null, os.getStatus(), observacao, true, null);
    }

    /** Visita confirmada pelo admin: registro informativo no historico (status da OS nao muda). */
    @Transactional
    public void registrarConfirmacaoVisita(OrdemServico os, String observacao, CustomUserDetails cud) {
        registrarEvento(os, TipoEventoOS.OBSERVACAO, os.getStatus(), os.getStatus(), observacao, true, cud);
    }

    /** Cancelamento solicitado pelo cliente via agendamento (so' antes de iniciar o atendimento). */
    @Transactional
    public void cancelarPorAgendamento(OrdemServico os, String observacao, CustomUserDetails cud) {
        if (!os.getStatus().podeTransicionarPara(StatusOS.CANCELADO)) {
            throw new RegraDeNegocioException("Esta OS nao pode mais ser cancelada (" + os.getStatus().getDescricao() + ").");
        }
        mudarStatus(os, StatusOS.CANCELADO, observacao, cud);
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------
    private void mudarStatus(OrdemServico os, StatusOS novo, String observacao, CustomUserDetails cud) {
        StatusOS anterior = os.getStatus();
        os.setStatus(novo);
        ordemServicoRepository.save(os);

        Agendamento ag = os.getAgendamento();
        ag.setStatus(novo.paraStatusAgendamento());
        agendamentoRepository.save(ag);

        registrarEvento(os, TipoEventoOS.STATUS, anterior, novo, observacao, true, cud);
    }

    private void registrarEvento(OrdemServico os, TipoEventoOS tipo, StatusOS anterior, StatusOS novo,
                                 String observacao, boolean visivelCliente, CustomUserDetails cud) {
        timelineOSRepository.save(TimelineOS.builder()
                .ordemServico(os)
                .tipo(tipo)
                .statusAnterior(anterior != null ? anterior.name() : null)
                .statusNovo(novo.name())
                .observacao(observacao)
                .alteradoPor(cud != null ? cud.getUsuario().getNome() : "sistema")
                .usuario(cud != null ? usuarioRepository.getReferenceById(cud.getId()) : null)
                .visivelCliente(visivelCliente)
                .dataHora(LocalDateTime.now())
                .build());
    }

    private List<TimelineOS> historico(OrdemServico os) {
        return timelineOSRepository.findByOrdemServico_IdOrderByDataHoraAscIdAsc(os.getId());
    }

    private OrdemServicoResponse detalheEquipe(OrdemServico os) {
        return OrdemServicoMapper.toResponse(os, historico(os), true, true);
    }

    private StatusOS parseStatus(String status) {
        try {
            return StatusOS.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new RegraDeNegocioException("Status invalido.");
        }
    }

    private void validarPrevisao(LocalDate previsao) {
        if (previsao != null && previsao.isBefore(LocalDate.now())) {
            throw new RegraDeNegocioException("A previsao de conclusao nao pode estar no passado.");
        }
    }

    private OrdemServico getOuFalhar(Long id) {
        return ordemServicoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ordem de Servico nao encontrada."));
    }

    private CustomUserDetails exigirAutenticado(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails cud)) {
            throw new AcessoNegadoException("Autenticacao necessaria.");
        }
        return cud;
    }

    private boolean ehDono(OrdemServico os, CustomUserDetails cud) {
        return cud.getUsuario().getTipo() == TipoUsuario.CLIENTE && os.getCliente() != null
                && os.getCliente().getUsuario().getId().equals(cud.getId());
    }

    private boolean ehTecnicoDaOS(OrdemServico os, CustomUserDetails cud) {
        return cud.getUsuario().getTipo() == TipoUsuario.TECNICO && os.getTecnico() != null
                && os.getTecnico().getUsuario().getId().equals(cud.getId());
    }

    private CustomUserDetails garantirDonoTecnicoOuAdmin(OrdemServico os, Authentication auth) {
        CustomUserDetails cud = exigirAutenticado(auth);
        if (cud.getUsuario().getTipo() == TipoUsuario.ADMINISTRADOR || ehTecnicoDaOS(os, cud) || ehDono(os, cud)) {
            return cud;
        }
        throw new AcessoNegadoException("Esta Ordem de Servico nao pertence a voce.");
    }

    /**
     * Tecnico so' altera OS atribuida a ele, ou uma OS ainda sem tecnico (nesse
     * caso ele a assume — ver assumirSeAindaSemTecnico). Admin altera qualquer uma.
     */
    private CustomUserDetails garantirTecnicoDaOSOuAdmin(OrdemServico os, Authentication auth) {
        CustomUserDetails cud = exigirAutenticado(auth);
        TipoUsuario tipo = cud.getUsuario().getTipo();
        if (tipo == TipoUsuario.ADMINISTRADOR) return cud;
        if (tipo == TipoUsuario.TECNICO && (os.getTecnico() == null || ehTecnicoDaOS(os, cud))) return cud;
        throw new AcessoNegadoException("Esta Ordem de Servico esta atribuida a outro tecnico.");
    }

    private void assumirSeAindaSemTecnico(OrdemServico os, CustomUserDetails cud) {
        if (os.getTecnico() != null || cud.getUsuario().getTipo() != TipoUsuario.TECNICO) return;
        tecnicoRepository.findByUsuario_Id(cud.getId()).ifPresent(tecnico -> {
            os.setTecnico(tecnico);
            os.getAgendamento().setTecnico(tecnico);
            agendamentoRepository.save(os.getAgendamento());
            registrarEvento(os, TipoEventoOS.ATRIBUICAO, os.getStatus(), os.getStatus(),
                    "Tecnico responsavel: " + tecnico.getNome() + ".", true, cud);
        });
    }

    private static String textoOuNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String vazioParaNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String limitar(String s) {
        return s.length() <= 500 ? s : s.substring(0, 497) + "...";
    }
}
