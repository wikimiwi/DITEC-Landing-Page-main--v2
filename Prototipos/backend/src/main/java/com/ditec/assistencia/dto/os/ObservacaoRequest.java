package com.ditec.assistencia.dto.os;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** visivelCliente=false grava uma anotacao interna (nao aparece no rastreamento). */
public record ObservacaoRequest(
        @NotBlank(message = "Escreva a observacao.") @Size(max = 500, message = "Observacao muito longa.") String texto,
        Boolean visivelCliente
) {
}
