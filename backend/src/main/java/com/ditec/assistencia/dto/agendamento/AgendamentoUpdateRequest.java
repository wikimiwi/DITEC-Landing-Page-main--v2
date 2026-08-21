package com.ditec.assistencia.dto.agendamento;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

/** Alteracao de um agendamento ainda elegivel (PENDENTE ou AGENDADO). */
public record AgendamentoUpdateRequest(
        @NotBlank(message = "Informe o tipo do aparelho.") String tipoAparelho,
        String descricaoProblema,
        @NotBlank(message = "Informe o endereco.") String endereco,
        @NotBlank(message = "Informe o bairro.") String bairro,
        @Future(message = "Selecione uma data futura.") LocalDateTime dataHora
) {
}
