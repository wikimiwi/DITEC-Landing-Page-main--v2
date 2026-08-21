package com.ditec.assistencia.repository;

import com.ditec.assistencia.entity.Usuario;
import com.ditec.assistencia.enums.TipoUsuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByTipo(TipoUsuario tipo);
}
