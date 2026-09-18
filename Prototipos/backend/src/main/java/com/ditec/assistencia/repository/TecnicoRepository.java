package com.ditec.assistencia.repository;

import com.ditec.assistencia.entity.Tecnico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TecnicoRepository extends JpaRepository<Tecnico, Long> {
    Optional<Tecnico> findByUsuario_Id(Long usuarioId);
    Optional<Tecnico> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
}
