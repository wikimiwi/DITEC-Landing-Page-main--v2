package com.ditec.assistencia.repository;

import com.ditec.assistencia.entity.Avaliacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AvaliacaoRepository extends JpaRepository<Avaliacao, Long> {
    Optional<Avaliacao> findByOrdemServico_Id(Long ordemServicoId);
    List<Avaliacao> findAllByOrderByCriadoEmDesc();
}
