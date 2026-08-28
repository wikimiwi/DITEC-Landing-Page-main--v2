package com.ditec.assistencia.dto.os;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record FinalizarOSRequest(
        @NotNull(message = "Informe o valor cobrado.")
        @DecimalMin(value = "0.0", message = "O valor nao pode ser negativo.")
        @DecimalMax(value = "999999.99", message = "Valor invalido.") BigDecimal valor,
        @NotBlank(message = "Informe a forma de pagamento.") @Size(max = 30, message = "Forma de pagamento invalida.") String formaPagamento,
        @Size(max = 2000, message = "Descricao das pecas muito longa.") String pecasUtilizadas,
        @Size(max = 2000, message = "Descricao final muito longa.") String descricaoFinal,
        @Size(max = 50, message = "Numero da nota fiscal muito longo.") String notaFiscalNumero
) {
}
