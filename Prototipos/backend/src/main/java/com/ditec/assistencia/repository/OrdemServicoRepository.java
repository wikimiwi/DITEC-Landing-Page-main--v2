package com.ditec.assistencia.repository;

import com.ditec.assistencia.entity.OrdemServico;
import com.ditec.assistencia.enums.StatusOS;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrdemServicoRepository extends JpaRepository<OrdemServico, Long> {
    Optional<OrdemServico> findByProtocolo(String protocolo);
    Optional<OrdemServico> findByAgendamento_Id(Long agendamentoId);
    List<OrdemServico> findByCliente_IdOrderByCriadoEmDesc(Long clienteId);
    List<OrdemServico> findByTecnico_IdOrderByCriadoEmDesc(Long tecnicoId);
    long countByStatus(StatusOS status);
    long countByStatusIn(Collection<StatusOS> status);

    /**
     * Listagem administrativa com filtros opcionais (null = sem filtro).
     * Os "join fetch" evitam o problema N+1 ao montar a resposta.
     * protocolo e cliente sao comparados por "contem", sem diferenciar maiusculas.
     */
    @Query("""
           select o from OrdemServico o
             join fetch o.agendamento a
             left join fetch o.tecnico t
             left join fetch o.cliente c
             left join fetch c.usuario u
           where (:status is null or o.status = :status)
             and (:tecnicoId is null or t.id = :tecnicoId)
             and (:protocolo is null or upper(o.protocolo) like concat('%', upper(:protocolo), '%'))
             and (:cliente is null
                  or lower(u.nome) like concat('%', lower(:cliente), '%')
                  or lower(a.nomeContato) like concat('%', lower(:cliente), '%'))
           order by a.prioridadeGas desc, o.criadoEm desc
           """)
    List<OrdemServico> buscarAdmin(@Param("status") StatusOS status,
                                   @Param("tecnicoId") Long tecnicoId,
                                   @Param("protocolo") String protocolo,
                                   @Param("cliente") String cliente);
}
