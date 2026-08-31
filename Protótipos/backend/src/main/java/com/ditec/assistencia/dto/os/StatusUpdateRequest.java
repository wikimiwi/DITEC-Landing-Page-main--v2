package com.ditec.assistencia.dto.os;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StatusUpdateRequest(
        @NotBlank(message = "Informe o novo status.") @Size(max = 30, message = "Status invalido.") String status,
        @Size(max = 500, message = "Observacao muito longa.") String observacao
) {
}
