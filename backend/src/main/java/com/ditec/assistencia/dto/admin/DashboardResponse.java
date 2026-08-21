package com.ditec.assistencia.dto.admin;

public record DashboardResponse(long totalClientes, long totalAgendamentos, long osAbertas,
                                 long osConcluidas, long atendimentosNoMes) {
}
