package com.ditec.assistencia.repository;

import com.ditec.assistencia.entity.TimelineOS;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TimelineOSRepository extends JpaRepository<TimelineOS, Long> {
    /** Historico em ordem cronologica (id desempata eventos gravados no mesmo instante). */
    List<TimelineOS> findByOrdemServico_IdOrderByDataHoraAscIdAsc(Long ordemServicoId);
}
