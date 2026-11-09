package com.ditec.assistencia.exception;

/** 502 — falha ao chamar um servico externo (ex: IA externa fora do ar). */
public class IntegracaoExternaException extends RuntimeException {
    public IntegracaoExternaException(String mensagem) {
        super(mensagem);
    }
}
