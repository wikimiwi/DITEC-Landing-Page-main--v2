package com.ditec.assistencia.entity;

import com.ditec.assistencia.enums.StatusConta;
import com.ditec.assistencia.enums.TipoUsuario;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Tabela de autenticacao/identidade. Todo login (cliente, administrador ou
 * tecnico) passa por aqui — o tipo define o perfil e as permissoes.
 */
@Entity
@Table(name = "usuario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "senha_hash", nullable = false, length = 255)
    private String senhaHash;

    @Column(length = 20)
    private String telefone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoUsuario tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatusConta status = StatusConta.ATIVO;

    @Column(name = "data_cadastro", nullable = false, updatable = false)
    private LocalDateTime dataCadastro;

    @Column(name = "ultimo_acesso")
    private LocalDateTime ultimoAcesso;

    @PrePersist
    void prePersist() {
        if (dataCadastro == null) dataCadastro = LocalDateTime.now();
        if (status == null) status = StatusConta.ATIVO;
    }
}
