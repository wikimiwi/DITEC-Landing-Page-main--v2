package com.ditec.assistencia.controller;

import com.ditec.assistencia.dto.admin.ServicoResponse;
import com.ditec.assistencia.service.ServicoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Lista publica de servicos ativos (usada por qualquer picker no site no futuro). */
@RestController
@RequestMapping("/api/servicos")
@RequiredArgsConstructor
public class ServicoController {

    private final ServicoService servicoService;

    @GetMapping
    public List<ServicoResponse> listar() {
        return servicoService.listarAtivos();
    }
}
