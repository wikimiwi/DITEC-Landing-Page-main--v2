package com.ditec.assistencia.repository;

import com.ditec.assistencia.entity.Agendamento;
import com.ditec.assistencia.enums.StatusAgendamento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AgendamentoRepository extends JpaRepository<Agendamento, Long> {

    Optional<Agendamento> findByProtocolo(String protocolo);

    List<Agendamento> findByCliente_IdOrderByDataHoraDesc(Long clienteId);

    /** Usado para checar conflito de horario (mesmo slot, agendamento ainda ativo). */
    @Query("""
           select a from Agendamento a
           where a.dataHora = :dataHora
             and a.status in (com.ditec.assistencia.enums.StatusAgendamento.PENDENTE,
                               com.ditec.assistencia.enums.StatusAgendamento.AGENDADO,
                               com.ditec.assistencia.enums.StatusAgendamento.EM_ATENDIMENTO)
           """)
    List<Agendamento> findAtivosNoHorario(@Param("dataHora") LocalDateTime dataHora);

    @Query("""
           select a.dataHora from Agendamento a
           where a.dataHora between :inicio and :fim
             and a.status in (com.ditec.assistencia.enums.StatusAgendamento.PENDENTE,
                               com.ditec.assistencia.enums.StatusAgendamento.AGENDADO,
                               com.ditec.assistencia.enums.StatusAgendamento.EM_ATENDIMENTO)
           """)
    List<LocalDateTime> findHorariosOcupadosEntre(@Param("inicio") LocalDateTime inicio,
                                                    @Param("fim") LocalDateTime fim);

    /** Vincula o agendamento mais recente ainda sem conta a um cliente recem-cadastrado,
     *  usando o telefone informado — mesma logica que existia no localStorage. */
    Optional<Agendamento> findFirstByClienteIsNullAndTelefoneContatoOrderByCriadoEmDesc(String telefoneContato);

    long countByStatusNot(StatusAgendamento status);

    Page<Agendamento> findAll(Pageable pageable);
}
