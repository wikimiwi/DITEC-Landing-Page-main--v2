package com.ditec.assistencia.controller;

import com.ditec.assistencia.dto.admin.*;
import com.ditec.assistencia.dto.agendamento.AgendamentoResponse;
import com.ditec.assistencia.dto.os.OrdemServicoResponse;
import com.ditec.assistencia.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Painel administrativo — todos os endpoints exigem ROLE_ADMINISTRADOR (secao 14 do prompt mestre). */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class AdminController {

    private final AdminService adminService;
    private final AgendamentoService agendamentoService;
    private final OrdemServicoService ordemServicoService;
    private final TecnicoService tecnicoService;
    private final ServicoService servicoService;
    private final RelatorioService relatorioService;

    // ---------------- Dashboard ----------------
    @GetMapping("/dashboard")
    public DashboardResponse dashboard() {
        return adminService.dashboard();
    }

    // ---------------- Clientes ----------------
    @GetMapping("/clientes")
    public List<ClienteAdminResponse> clientes() {
        return adminService.listarClientes();
    }

    // ---------------- Agendamentos ----------------
    @GetMapping("/agendamentos")
    public Page<AgendamentoResponse> agendamentos(@PageableDefault(size = 50) Pageable pageable) {
        return agendamentoService.listarTodos(pageable);
    }

    @PutMapping("/agendamentos/{id}/confirmar")
    public AgendamentoResponse confirmarAgendamento(@PathVariable Long id) {
        return agendamentoService.confirmar(id);
    }

    @PutMapping("/agendamentos/{id}/tecnico")
    public AgendamentoResponse atribuirTecnico(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        return agendamentoService.atribuirTecnico(id, body.get("tecnicoId"));
    }

    // ---------------- Ordens de Servico ----------------
    @GetMapping("/ordens-servico")
    public List<OrdemServicoResponse> ordensServico() {
        return ordemServicoService.listarTodasAdmin();
    }

    // ---------------- Tecnicos ----------------
    @GetMapping("/tecnicos")
    public List<TecnicoResponse> tecnicos() {
        return tecnicoService.listar();
    }

    @PostMapping("/tecnicos")
    @ResponseStatus(HttpStatus.CREATED)
    public TecnicoCriadoResponse criarTecnico(@Valid @RequestBody TecnicoRequest request) {
        return tecnicoService.criar(request);
    }

    @PutMapping("/tecnicos/{id}")
    public TecnicoResponse atualizarTecnico(@PathVariable Long id, @Valid @RequestBody TecnicoRequest request) {
        return tecnicoService.atualizar(id, request);
    }

    @PutMapping("/tecnicos/{id}/status")
    public TecnicoResponse statusTecnico(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        return tecnicoService.alterarStatus(id, Boolean.TRUE.equals(body.get("ativo")));
    }

    // ---------------- Servicos ----------------
    @GetMapping("/servicos")
    public List<ServicoResponse> servicos() {
        return servicoService.listarTodos();
    }

    @PostMapping("/servicos")
    @ResponseStatus(HttpStatus.CREATED)
    public ServicoResponse criarServico(@Valid @RequestBody ServicoRequest request) {
        return servicoService.criar(request);
    }

    @PutMapping("/servicos/{id}")
    public ServicoResponse atualizarServico(@PathVariable Long id, @Valid @RequestBody ServicoRequest request) {
        return servicoService.atualizar(id, request);
    }

    @PutMapping("/servicos/{id}/status")
    public ServicoResponse statusServico(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        return servicoService.alterarStatus(id, Boolean.TRUE.equals(body.get("ativo")));
    }

    // ---------------- Relatorios (CSV) ----------------
    @GetMapping("/relatorios/agendamentos")
    public ResponseEntity<byte[]> relatorioAgendamentos() {
        return csv(relatorioService.agendamentosCsv(), "relatorio-agendamentos.csv");
    }

    @GetMapping("/relatorios/os")
    public ResponseEntity<byte[]> relatorioOS() {
        return csv(relatorioService.ordensServicoCsv(), "relatorio-ordens-servico.csv");
    }

    @GetMapping("/relatorios/avaliacoes")
    public ResponseEntity<byte[]> relatorioAvaliacoes() {
        return csv(relatorioService.avaliacoesCsv(), "relatorio-avaliacoes.csv");
    }

    @GetMapping("/relatorios/chatbot")
    public ResponseEntity<byte[]> relatorioChatbot() {
        return csv(relatorioService.chatbotCsv(), "relatorio-chatbot.csv");
    }

    private ResponseEntity<byte[]> csv(byte[] conteudo, String nomeArquivo) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nomeArquivo + "\"")
                .body(conteudo);
    }
}
