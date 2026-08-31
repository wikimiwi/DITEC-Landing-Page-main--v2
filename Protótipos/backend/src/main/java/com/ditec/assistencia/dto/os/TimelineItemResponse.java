package com.ditec.assistencia.dto.os;

import java.time.LocalDateTime;

public record TimelineItemResponse(String statusAnterior, String statusNovo, String observacao,
                                    String alteradoPor, LocalDateTime dataHora) {
}
