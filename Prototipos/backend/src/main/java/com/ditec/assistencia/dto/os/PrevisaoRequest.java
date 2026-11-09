package com.ditec.assistencia.dto.os;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record PrevisaoRequest(
        @NotNull(message = "Informe a data prevista.") LocalDate previsaoConclusao,
        @Size(max = 500, message = "Observacao muito longa.") String observacao
) {
}
