package com.ditec.assistencia.controller;

import com.ditec.assistencia.dto.agendamento.AgendamentoCreateRequest;
import com.ditec.assistencia.dto.agendamento.AgendamentoResponse;
import com.ditec.assistencia.dto.agendamento.AgendamentoUpdateRequest;
import com.ditec.assistencia.dto.agendamento.DisponibilidadeResponse;
import com.ditec.assistencia.service.AgendamentoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/agendamentos")
@RequiredArgsConstructor
public class AgendamentoController {

    private final AgendamentoService agendamentoService;

    @GetMapping("/disponibilidade")
    public DisponibilidadeResponse disponibilidade(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return agendamentoService.disponibilidade(data);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AgendamentoResponse criar(@Valid @RequestBody AgendamentoCreateRequest request, Authentication auth) {
        return agendamentoService.criar(request, auth);
    }

    @GetMapping("/{id}")
    public AgendamentoResponse buscar(@PathVariable Long id, Authentication auth) {
        return agendamentoService.buscarPorId(id, auth);
    }

    @PutMapping("/{id}")
    public AgendamentoResponse atualizar(@PathVariable Long id, @Valid @RequestBody AgendamentoUpdateRequest request,
                                          Authentication auth) {
        return agendamentoService.atualizar(id, request, auth);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelar(@PathVariable Long id, Authentication auth) {
        agendamentoService.cancelar(id, auth);
    }
}
