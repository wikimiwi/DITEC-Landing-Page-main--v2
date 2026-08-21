package com.ditec.assistencia.service;

import com.ditec.assistencia.dto.admin.ClienteAdminResponse;
import com.ditec.assistencia.dto.admin.DashboardResponse;
import com.ditec.assistencia.entity.Cliente;
import com.ditec.assistencia.enums.StatusAgendamento;
import com.ditec.assistencia.enums.StatusOS;
import com.ditec.assistencia.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final ClienteRepository clienteRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final OrdemServicoRepository ordemServicoRepository;

    private static final DateTimeFormatter DESDE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy", new Locale("pt", "BR"));

    public DashboardResponse dashboard() {
        long totalClientes = clienteRepository.count();
        long totalAgendamentos = agendamentoRepository.count();
        long osAbertas = ordemServicoRepository.countByStatusIn(List.of(StatusOS.AGENDADO, StatusOS.EM_ATENDIMENTO));
        long osConcluidas = ordemServicoRepository.countByStatus(StatusOS.CONCLUIDO);

        YearMonth mesAtual = YearMonth.now();
        LocalDateTime inicioMes = mesAtual.atDay(1).atStartOfDay();
        LocalDateTime fimMes = mesAtual.atEndOfMonth().atTime(23, 59, 59);
        long atendimentosNoMes = agendamentoRepository.findAll().stream()
                .filter(a -> !a.getDataHora().isBefore(inicioMes) && !a.getDataHora().isAfter(fimMes))
                .count();

        return new DashboardResponse(totalClientes, totalAgendamentos, osAbertas, osConcluidas, atendimentosNoMes);
    }

    public List<ClienteAdminResponse> listarClientes() {
        return clienteRepository.findAll().stream().map(this::toResponse).toList();
    }

    private ClienteAdminResponse toResponse(Cliente c) {
        return new ClienteAdminResponse(
                c.getId(), c.getUsuario().getNome(), c.getUsuario().getEmail(), c.getUsuario().getTelefone(),
                c.getBairro(), c.getCidade(),
                c.getUsuario().getDataCadastro() != null ? DESDE_FMT.format(c.getUsuario().getDataCadastro()) : "-"
        );
    }
}
