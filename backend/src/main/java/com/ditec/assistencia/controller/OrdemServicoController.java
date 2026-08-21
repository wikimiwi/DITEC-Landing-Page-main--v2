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

    /** Rastreamento publico — igual ao comportamento original da landing page. */
    @GetMapping("/protocolo/{protocolo}")
    public OrdemServicoResponse buscarPorProtocolo(@PathVariable String protocolo) {
        return ordemServicoService.buscarPorProtocolo(protocolo);
    }

    @GetMapping("/{id}")
    public OrdemServicoResponse buscarPorId(@PathVariable Long id, Authentication auth) {
        return ordemServicoService.buscarPorId(id, auth);
    }

    /** Minhas OS — cliente ve as proprias, tecnico ve as que estao com ele. */
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

    @PostMapping("/{id}/finalizar")
    @PreAuthorize("hasAnyRole('TECNICO','ADMINISTRADOR')")
    public OrdemServicoResponse finalizar(@PathVariable Long id, @Valid @RequestBody FinalizarOSRequest request,
                                           Authentication auth) {
        return ordemServicoService.finalizar(id, request, auth);
    }

    @PostMapping("/{id}/avaliacao")
    @PreAuthorize("hasRole('CLIENTE')")
    @ResponseStatus(HttpStatus.CREATED)
    public void avaliar(@PathVariable Long id, @Valid @RequestBody AvaliacaoRequest request, Authentication auth) {
        ordemServicoService.avaliar(id, request, auth);
    }
}
