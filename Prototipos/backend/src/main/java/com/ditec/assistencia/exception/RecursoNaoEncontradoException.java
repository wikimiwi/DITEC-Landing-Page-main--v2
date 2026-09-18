package com.ditec.assistencia.exception;

/** 404 — protocolo inexistente, agendamento inexistente, etc. */
public class RecursoNaoEncontradoException extends RuntimeException {
    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
