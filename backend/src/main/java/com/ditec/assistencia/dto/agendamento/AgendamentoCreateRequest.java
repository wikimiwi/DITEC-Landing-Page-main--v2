package com.ditec.assistencia.dto.agendamento;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * Criacao de agendamento. nome/telefone sao obrigatorios mesmo para quem
 * esta logado (evita uma segunda consulta so pra exibir no formulario) —
 * quando ha' um cliente autenticado, o backend ignora-os e usa os dados
 * da conta; quando e' visitante, viram nome_contato/telefone_contato.
 */
public record AgendamentoCreateRequest(
        @NotBlank(message = "Informe o nome.") String nome,
        @NotBlank(message = "Informe o telefone.") String telefone,
        @NotBlank(message = "Informe o tipo do aparelho.") String tipoAparelho,
        String descricaoProblema,
        @NotBlank(message = "Informe o endereco.") String endereco,
        @NotBlank(message = "Informe o bairro.") String bairro,
        @NotNull(message = "Selecione data e horario.") @Future(message = "Selecione uma data futura.") LocalDateTime dataHora,
        Long servicoId
) {
}
