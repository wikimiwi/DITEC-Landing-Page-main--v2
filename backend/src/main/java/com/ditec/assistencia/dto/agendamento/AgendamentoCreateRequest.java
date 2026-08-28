package com.ditec.assistencia.dto.agendamento;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Criacao de agendamento. nome/telefone sao obrigatorios mesmo para quem
 * esta logado (evita uma segunda consulta so pra exibir no formulario) —
 * quando ha' um cliente autenticado, o backend ignora-os e usa os dados
 * da conta; quando e' visitante, viram nome_contato/telefone_contato.
 */
public record AgendamentoCreateRequest(
        @NotBlank(message = "Informe o nome.") @Size(max = 150, message = "Nome muito longo.") String nome,
        @NotBlank(message = "Informe o telefone.") @Size(max = 20, message = "Telefone muito longo.") String telefone,
        @NotBlank(message = "Informe o tipo do aparelho.") @Size(max = 100, message = "Descricao do aparelho muito longa.") String tipoAparelho,
        @Size(max = 500, message = "Descricao do problema muito longa.") String descricaoProblema,
        @NotBlank(message = "Informe o endereco.") @Size(max = 255, message = "Endereco muito longo.") String endereco,
        @NotBlank(message = "Informe o bairro.") @Size(max = 100, message = "Bairro muito longo.") String bairro,
        @NotNull(message = "Selecione data e horario.") @Future(message = "Selecione uma data futura.") LocalDateTime dataHora,
        Long servicoId
) {
}
