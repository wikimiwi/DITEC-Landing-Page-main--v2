package com.ditec.assistencia.exception;

/** 409 — e-mail ja cadastrado, horario ja ocupado, etc. */
public class ConflitoException extends RuntimeException {
    public ConflitoException(String mensagem) {
        super(mensagem);
    }
}
