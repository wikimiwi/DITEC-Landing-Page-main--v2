package com.ditec.assistencia.exception;

/** 502 — falha ao chamar um servico externo (ex: Hugging Face fora do ar / sem HF_API_KEY). */
public class IntegracaoExternaException extends RuntimeException {
    public IntegracaoExternaException(String mensagem) {
        super(mensagem);
    }
}
