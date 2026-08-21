package com.ditec.assistencia.repository;

import com.ditec.assistencia.entity.OrdemServico;
import com.ditec.assistencia.enums.StatusOS;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrdemServicoRepository extends JpaRepository<OrdemServico, Long> {
    Optional<OrdemServico> findByProtocolo(String protocolo);
    Optional<OrdemServico> findByAgendamento_Id(Long agendamentoId);
    List<OrdemServico> findByCliente_IdOrderByCriadoEmDesc(Long clienteId);
    List<OrdemServico> findByTecnico_IdOrderByCriadoEmDesc(Long tecnicoId);
    long countByStatus(StatusOS status);
    long countByStatusIn(List<StatusOS> status);
}
