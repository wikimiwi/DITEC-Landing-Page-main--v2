package com.ditec.assistencia.dto.os;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AvaliacaoRequest(
        @NotNull(message = "Informe uma nota.") @Min(value = 1, message = "A nota minima e' 1.") @Max(value = 5, message = "A nota maxima e' 5.") Integer nota,
        @Size(max = 500, message = "Comentario muito longo.") String comentario
) {
}
