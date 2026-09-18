package com.ditec.assistencia.dto.agendamento;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** Alteracao de um agendamento ainda elegivel (PENDENTE ou AGENDADO). */
public record AgendamentoUpdateRequest(
        @NotBlank(message = "Informe o tipo do aparelho.") @Size(max = 100, message = "Descricao do aparelho muito longa.") String tipoAparelho,
        @Size(max = 500, message = "Descricao do problema muito longa.") String descricaoProblema,
        @NotBlank(message = "Informe o endereco.") @Size(max = 255, message = "Endereco muito longo.") String endereco,
        @NotBlank(message = "Informe o bairro.") @Size(max = 100, message = "Bairro muito longo.") String bairro,
        @Future(message = "Selecione uma data futura.") LocalDateTime dataHora
) {
}
