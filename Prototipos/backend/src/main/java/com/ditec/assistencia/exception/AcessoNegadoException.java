package com.ditec.assistencia.exception;

/** 403 — usuario autenticado tentando acessar um recurso que nao e' seu. */
public class AcessoNegadoException extends RuntimeException {
    public AcessoNegadoException(String mensagem) {
        super(mensagem);
    }
}
