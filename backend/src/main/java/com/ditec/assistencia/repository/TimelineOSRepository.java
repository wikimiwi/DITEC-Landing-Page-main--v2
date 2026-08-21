package com.ditec.assistencia.repository;

import com.ditec.assistencia.entity.TimelineOS;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TimelineOSRepository extends JpaRepository<TimelineOS, Long> {
    List<TimelineOS> findByOrdemServico_IdOrderByDataHoraAsc(Long ordemServicoId);
}
