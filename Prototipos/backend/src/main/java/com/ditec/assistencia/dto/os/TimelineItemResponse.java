package com.ditec.assistencia.dto.os;

import java.time.LocalDateTime;

/** Um registro do historico da OS. statusAnterior == statusNovo em eventos que nao mudam o status. */
public record TimelineItemResponse(
        String tipo,
        String statusAnterior,
        String statusNovo,
        String statusNovoDescricao,
        String observacao,
        String alteradoPor,
        boolean visivelCliente,
        LocalDateTime dataHora
) {
}
