package com.ditec.assistencia.exception;

/** 422 — violacao de uma regra do DRS (ex: cancelar OS ja concluida, bairro fora da area). */
public class RegraDeNegocioException extends RuntimeException {
    public RegraDeNegocioException(String mensagem) {
        super(mensagem);
    }
}
