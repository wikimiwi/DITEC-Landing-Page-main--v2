package com.ditec.assistencia.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Registro de cada interacao do chatbot (UC010/UC011/UC013 do DRS). */
@Entity
@Table(name = "chat_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @Column(name = "sessao_id", nullable = false, length = 64)
    private String sessaoId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String pergunta;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String resposta;

    @Column(name = "data_hora", nullable = false)
    private LocalDateTime dataHora;

    @PrePersist
    void prePersist() {
        if (dataHora == null) dataHora = LocalDateTime.now();
    }
}
