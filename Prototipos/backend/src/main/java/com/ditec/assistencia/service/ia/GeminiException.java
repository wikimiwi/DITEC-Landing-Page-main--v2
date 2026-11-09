package com.ditec.assistencia.service.ia;

import lombok.Getter;

/** Falha classificada na chamada a Gemini API. A mensagem nunca contem a chave. */
@Getter
public class GeminiException extends RuntimeException {

    public enum Tipo {
        NAO_CONFIGURADA,      // GEMINI_API_KEY ausente
        CHAVE_INVALIDA,       // 401 ou 400 API_KEY_INVALID
        SEM_CREDITO,          // 402
        PERMISSAO_NEGADA,     // 403 (projeto sem acesso, verificacao/billing pendente, chave bloqueada)
        MODELO_INDISPONIVEL,  // 404 modelo inexistente ou nao liberado para a conta
        LIMITE_REQUISICOES,   // 429 (por minuto ou cota diaria do plano gratuito)
        INSTABILIDADE,        // 500/503
        TIMEOUT,              // 504 ou sem resposta no tempo limite
        CONTEUDO_BLOQUEADO,   // pergunta/resposta bloqueada pelos filtros de seguranca
        RESPOSTA_INVALIDA,    // JSON inesperado / sem texto
        REQUISICAO_RECUSADA   // outros 4xx
    }

    private final Tipo tipo;
    private final int httpStatus;
    /** Espera sugerida pelo Google (RetryInfo.retryDelay) em ms; 0 = nao informada. */
    private final long esperaSugeridaMs;

    public GeminiException(Tipo tipo, int httpStatus, String mensagem) {
        this(tipo, httpStatus, mensagem, 0L);
    }

    public GeminiException(Tipo tipo, int httpStatus, String mensagem, long esperaSugeridaMs) {
        super(mensagem);
        this.tipo = tipo;
        this.httpStatus = httpStatus;
        this.esperaSugeridaMs = esperaSugeridaMs;
    }
}
