package com.ditec.assistencia.repository;

import com.ditec.assistencia.entity.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    Optional<Cliente> findByUsuario_Id(Long usuarioId);
    Optional<Cliente> findByUsuario_EmailIgnoreCase(String email);
}
