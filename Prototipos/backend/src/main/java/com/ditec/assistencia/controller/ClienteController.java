package com.ditec.assistencia.controller;

import com.ditec.assistencia.dto.agendamento.AgendamentoResponse;
import com.ditec.assistencia.dto.cliente.ClientePerfilResponse;
import com.ditec.assistencia.dto.cliente.ClienteUpdateRequest;
import com.ditec.assistencia.security.CustomUserDetails;
import com.ditec.assistencia.service.ClienteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clientes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CLIENTE')")
public class ClienteController {

    private final ClienteService clienteService;

    @GetMapping("/me")
    public ClientePerfilResponse me(@AuthenticationPrincipal CustomUserDetails principal) {
        return clienteService.perfil(principal.getUsuario());
    }

    @PutMapping("/me")
    public ClientePerfilResponse atualizar(@AuthenticationPrincipal CustomUserDetails principal,
                                            @Valid @RequestBody ClienteUpdateRequest request) {
        return clienteService.atualizarPerfil(principal.getUsuario(), request);
    }

    @GetMapping("/me/agendamentos")
    public List<AgendamentoResponse> meusAgendamentos(@AuthenticationPrincipal CustomUserDetails principal) {
        return clienteService.meusAgendamentos(principal.getUsuario());
    }
}
