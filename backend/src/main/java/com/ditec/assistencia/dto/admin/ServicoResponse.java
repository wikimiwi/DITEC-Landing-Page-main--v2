package com.ditec.assistencia.dto.admin;

import java.math.BigDecimal;

public record ServicoResponse(Long id, String nome, String descricao, BigDecimal valorBase, String status) {
}
