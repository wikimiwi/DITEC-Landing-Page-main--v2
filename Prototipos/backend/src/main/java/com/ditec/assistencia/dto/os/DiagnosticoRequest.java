package com.ditec.assistencia.dto.os;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DiagnosticoRequest(
        @NotBlank(message = "Descreva o diagnostico.")
        @Size(max = 4000, message = "Diagnostico muito longo.") String diagnostico,
        @DecimalMin(value = "0.0", message = "O orcamento nao pode ser negativo.")
        @DecimalMax(value = "999999.99", message = "Orcamento invalido.") BigDecimal valorOrcamento,
        LocalDate previsaoConclusao
) {
}
