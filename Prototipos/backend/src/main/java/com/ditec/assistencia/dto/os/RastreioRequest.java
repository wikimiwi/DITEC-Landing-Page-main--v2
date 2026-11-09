package com.ditec.assistencia.dto.os;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Consulta publica de OS. Alem do protocolo (sequencial, portanto adivinhavel),
 * exige o telefone informado no agendamento — impede que terceiros consultem
 * ordens alheias testando protocolos em sequencia.
 */
public record RastreioRequest(
        @NotBlank(message = "Informe o numero da OS (protocolo).")
        @Size(max = 20, message = "Protocolo invalido.")
        @Pattern(regexp = "(?i)^\\s*DITEC-\\d{4}-\\d{6}\\s*$", message = "Use o formato DITEC-AAAA-NNNNNN.")
        String protocolo,
        @NotBlank(message = "Informe o telefone usado no agendamento.")
        @Size(max = 20, message = "Telefone invalido.")
        String telefone
) {
}
