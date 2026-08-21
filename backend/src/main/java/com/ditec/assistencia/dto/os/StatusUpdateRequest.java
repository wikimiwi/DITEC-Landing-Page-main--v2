package com.ditec.assistencia.dto.os;

import jakarta.validation.constraints.NotBlank;

public record StatusUpdateRequest(
        @NotBlank(message = "Informe o novo status.") String status,
        String observacao
) {
}
