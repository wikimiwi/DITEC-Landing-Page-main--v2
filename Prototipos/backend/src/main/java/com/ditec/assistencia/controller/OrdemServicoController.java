package com.ditec.assistencia.controller;

import com.ditec.assistencia.dto.os.*;
import com.ditec.assistencia.service.OrdemServicoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ordens-servico")
@RequiredArgsConstructor
public class OrdemServicoController {

    private final OrdemServicoService ordemServicoService;

    /**
     * Rastreamento publico: protocolo + telefone do agendamento.
     * POST (e nao GET) para que o telefone nao fique em URL, logs de acesso ou historico do navegador.
     * O antigo GET /protocolo/{protocolo} (so' com o protocolo, enumeravel) foi removido.
     */
    @PostMapping("/rastreio")
    public OrdemServicoRastreioResponse rastrear(@Valid @RequestBody RastreioRequest request) {
        return ordemServicoService.rastrear(request);
    }

    @GetMapping("/{id}")
    public OrdemServicoResponse buscarPorId(@PathVariable Long id, Authentication auth) {
        return ordemServicoService.buscarPorId(id, auth);
    }

    /** Minhas OS — cliente ve as proprias, tecnico ve as atribuidas a ele. */
    @GetMapping
    @PreAuthorize("hasAnyRole('CLIENTE','TECNICO')")
    public List<OrdemServicoResponse> minhas(Authentication auth) {
        return ordemServicoService.listarMinhas(auth);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('TECNICO','ADMINISTRADOR')")
    public OrdemServicoResponse atualizarStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request,
                                                Authentication auth) {
        return ordemServicoService.atualizarStatus(id, request, auth);
    }

    @PutMapping("/{id}/diagnostico")
    @PreAuthorize("hasAnyRole('TECNICO','ADMINISTRADOR')")
    public OrdemServicoResponse diagnostico(@PathVariable Long id, @Valid @RequestBody DiagnosticoRequest request,
                                            Authentication auth) {
        return ordemServicoService.registrarDiagnostico(id, request, auth);
    }

    @PutMapping("/{id}/previsao")
    @PreAuthorize("hasAnyRole('TECNICO','ADMINISTRADOR')")
    public OrdemServicoResponse previsao(@PathVariable Long id, @Valid @RequestBody PrevisaoRequest request,
                                         Authentication auth) {
        return ordemServicoService.atualizarPrevisao(id, request, auth);
    }

    @PostMapping("/{id}/observacoes")
    @PreAuthorize("hasAnyRole('TECNICO','ADMINISTRADOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public OrdemServicoResponse observacao(@PathVariable Long id, @Valid @RequestBody ObservacaoRequest request,
                                           Authentication auth) {
        return ordemServicoService.adicionarObservacao(id, request, auth);
    }

    @PostMapping("/{id}/finalizar")
    @PreAuthorize("hasAnyRole('TECNICO','ADMINISTRADOR')")
    public OrdemServicoResponse finalizar(@PathVariable Long id, @Valid @RequestBody FinalizarOSRequest request,
                                          Authentication auth) {
        return ordemServicoService.finalizar(id, request, auth);
    }

    /** Cliente dono aprova ou recusa o orcamento. */
    @PostMapping("/{id}/orcamento/resposta")
    @PreAuthorize("hasRole('CLIENTE')")
    public OrdemServicoResponse responderOrcamento(@PathVariable Long id,
                                                   @Valid @RequestBody OrcamentoRespostaRequest request,
                                                   Authentication auth) {
        return ordemServicoService.responderOrcamento(id, request, auth);
    }

    @PostMapping("/{id}/avaliacao")
    @PreAuthorize("hasRole('CLIENTE')")
    @ResponseStatus(HttpStatus.CREATED)
    public void avaliar(@PathVariable Long id, @Valid @RequestBody AvaliacaoRequest request, Authentication auth) {
        ordemServicoService.avaliar(id, request, auth);
    }
}
