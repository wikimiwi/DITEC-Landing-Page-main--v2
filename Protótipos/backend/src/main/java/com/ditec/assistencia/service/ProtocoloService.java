package com.ditec.assistencia.service;

import org.springframework.stereotype.Component;

import java.time.Year;

/**
 * Gera protocolos no formato DITEC-{ano}-{sequencial de 6 digitos}
 * (secao 12 do prompt mestre). Usamos o proprio ID auto-increment do
 * agendamento como base do sequencial: e' unico, monotonico e nao exige
 * nenhuma tabela/sequence extra — simples e suficiente para o escopo.
 * O mesmo protocolo e' reaproveitado na Ordem de Servico correspondente.
 */
@Component
public class ProtocoloService {

    public String gerar(Long agendamentoId) {
        return "DITEC-" + Year.now() + "-" + String.format("%06d", agendamentoId);
    }
}
