package com.ditec.assistencia.exception;

/** 401 — e-mail/senha incorretos no login. */
public class CredenciaisInvalidasException extends RuntimeException {
    public CredenciaisInvalidasException(String mensagem) {
        super(mensagem);
    }
}
