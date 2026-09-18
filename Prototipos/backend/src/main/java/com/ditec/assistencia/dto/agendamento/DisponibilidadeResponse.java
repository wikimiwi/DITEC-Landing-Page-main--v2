package com.ditec.assistencia.dto.agendamento;

import java.time.LocalDate;
import java.util.List;

public record DisponibilidadeResponse(
        LocalDate data,
        boolean diaAtendido,
        List<String> horariosDisponiveis,
        List<String> horariosOcupados
) {
}
