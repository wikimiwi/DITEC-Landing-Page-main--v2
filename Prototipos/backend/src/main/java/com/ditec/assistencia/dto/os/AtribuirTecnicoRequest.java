package com.ditec.assistencia.dto.os;

import jakarta.validation.constraints.NotNull;

public record AtribuirTecnicoRequest(@NotNull(message = "Informe o tecnico.") Long tecnicoId) {
}
