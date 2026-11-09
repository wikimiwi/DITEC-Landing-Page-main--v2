package com.ditec.assistencia.service.ia;

import com.ditec.assistencia.config.GeminiProperties;
import com.ditec.assistencia.dto.chat.ChatMessageDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Cliente da Google Gemini API (REST v1beta, metodo generateContent).
 *
 * <ul>
 *   <li>Autenticacao pelo header {@code x-goog-api-key} — a chave nunca vai na URL
 *       (URLs aparecem em logs de proxies/servidores) e nunca e' logada.</li>
 *   <li>Conversa: {@code systemInstruction} (regras da DITEC, definidas so' no servidor) +
 *       {@code contents} com papeis "user"/"model" (o "assistant" do front vira "model").</li>
 *   <li>Raciocinio ("thinking") ajustado por geracao: Gemini 2.5 usa
 *       {@code thinkingBudget} (0 = desligado, menor latencia); Gemini 3+ usa
 *       {@code thinkingLevel} e ignora temperature.</li>
 *   <li>Tentativas LIMITADAS (no maximo 3) com espera progressiva + jitter apenas para
 *       429 e 5xx, como recomenda a documentacao; 400/401/402/403 nunca sao repetidos.</li>
 *   <li>Se o modelo principal nao existir/estiver indisponivel para a conta, tenta UMA vez
 *       o modelo alternativo (GEMINI_FALLBACK_MODEL).</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GeminiClient {

    private final RestClient geminiRestClient;
    private final GeminiProperties props;
    private final ObjectMapper objectMapper;

    /** Resposta valida da IA. */
    public record Resposta(String texto, String modelo, long duracaoMs, String finishReason) {
    }

    public Resposta gerar(String instrucaoSistema, List<ChatMessageDto> historico) {
        if (!props.configurada()) {
            throw new GeminiException(GeminiException.Tipo.NAO_CONFIGURADA, 0, "GEMINI_API_KEY nao configurada.");
        }
        String alternativo = props.fallbackModel();
        boolean temAlternativo = alternativo != null && !alternativo.isBlank() && !alternativo.equals(props.model());
        // Principal ja' sabidamente indisponivel: vai direto ao alternativo (evita uma ida e volta
        // inutil a cada mensagem); o principal volta a ser testado depois de REVALIDAR_PRINCIPAL_MS.
        if (temAlternativo && System.currentTimeMillis() < usarAlternativoAte) {
            return gerarComModelo(alternativo, instrucaoSistema, historico);
        }
        try {
            return gerarComModelo(props.model(), instrucaoSistema, historico);
        } catch (GeminiException ex) {
            if (!temAlternativo || ex.getTipo() != GeminiException.Tipo.MODELO_INDISPONIVEL) throw ex;
            log.warn("Modelo Gemini '{}' indisponivel para esta conta ({}); usando o alternativo '{}' pelas proximas {} h.",
                    props.model(), ex.getMessage(), alternativo, REVALIDAR_PRINCIPAL_MS / 3_600_000);
            Resposta r = gerarComModelo(alternativo, instrucaoSistema, historico);
            usarAlternativoAte = System.currentTimeMillis() + REVALIDAR_PRINCIPAL_MS;
            return r;
        }
    }

    static final long ESPERA_MAXIMA_PARA_REPETIR_MS = 2_000L;

    /** Ate quando usar direto o modelo alternativo (principal indisponivel para a conta). */
    private volatile long usarAlternativoAte = 0L;
    static final long REVALIDAR_PRINCIPAL_MS = 6 * 3_600_000L;

    private Resposta gerarComModelo(String modelo, String instrucaoSistema, List<ChatMessageDto> historico) {
        Map<String, Object> corpo = montarCorpo(modelo, instrucaoSistema, historico);
        String url = props.apiUrl().replaceAll("/+$", "") + "/models/" + modelo + ":generateContent";
        int tentativasExtras = props.maxRetriesSeguro();
        long espera = props.retryBackoffMs() != null ? props.retryBackoffMs() : 500L;

        for (int tentativa = 0; ; tentativa++) {
            long inicio = System.nanoTime();
            try {
                String json = geminiRestClient.post()
                        .uri(url)
                        .header("x-goog-api-key", props.apiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(corpo)
                        .retrieve()
                        .body(String.class);
                long ms = (System.nanoTime() - inicio) / 1_000_000;
                return interpretar(json, modelo, ms);
            } catch (HttpStatusCodeException ex) {
                GeminiException erro = classificar(ex);
                // Se o Google pede para esperar mais que alguns segundos (limite por minuto/dia do
                // plano gratuito), repetir agora so' atrasaria o usuario: desiste e deixa o
                // ChatService pausar pelo tempo indicado.
                boolean esperaLonga = erro.getEsperaSugeridaMs() > ESPERA_MAXIMA_PARA_REPETIR_MS;
                boolean repetivel = !esperaLonga && (erro.getTipo() == GeminiException.Tipo.LIMITE_REQUISICOES
                        || erro.getTipo() == GeminiException.Tipo.INSTABILIDADE);
                if (repetivel && tentativa < tentativasExtras) {
                    long aguardar = espera * (1L << tentativa) + ThreadLocalRandom.current().nextLong(0, Math.max(1, espera / 2));
                    log.warn("Gemini HTTP {} ({}); nova tentativa {}/{} em {} ms.", erro.getHttpStatus(), erro.getTipo(),
                            tentativa + 1, tentativasExtras, aguardar);
                    dormir(aguardar);
                    continue;
                }
                throw erro;
            } catch (ResourceAccessException ex) {
                // timeout ou falha de rede: nao repete (o usuario ja esperou o tempo maximo)
                throw new GeminiException(GeminiException.Tipo.TIMEOUT, 0,
                        "Sem resposta da Gemini API apos " + (System.nanoTime() - inicio) / 1_000_000 + " ms.");
            } catch (RestClientException ex) {
                throw new GeminiException(GeminiException.Tipo.RESPOSTA_INVALIDA, 0,
                        "Falha de comunicacao com a Gemini API: " + ex.getClass().getSimpleName());
            }
        }
    }

    Map<String, Object> montarCorpo(String modelo, String instrucaoSistema, List<ChatMessageDto> historico) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("systemInstruction", Map.of("parts", List.of(Map.of("text", instrucaoSistema))));
        corpo.put("contents", converterHistorico(historico));

        Map<String, Object> geracao = new LinkedHashMap<>();
        geracao.put("maxOutputTokens", props.maxOutputTokens() != null ? props.maxOutputTokens() : 400);
        boolean gemini3OuSuperior = modelo.matches("gemini-([3-9]|\\d{2,}).*");
        if (gemini3OuSuperior) {
            String nivel = props.thinkingLevel() == null || props.thinkingLevel().isBlank() ? "LOW" : props.thinkingLevel();
            geracao.put("thinkingConfig", Map.of("thinkingLevel", nivel.toUpperCase()));
        } else {
            geracao.put("temperature", props.temperature() != null ? props.temperature() : 0.6);
            if (modelo.startsWith("gemini-2.5")) {
                // 2.5 Flash/Flash-Lite: raciocinio desligado = respostas mais rapidas para um chat de atendimento
                geracao.put("thinkingConfig", Map.of("thinkingBudget", 0));
            }
        }
        corpo.put("generationConfig", geracao);
        return corpo;
    }

    /**
     * Converte o historico do front (user/assistant) para o formato do Gemini (user/model):
     * descarta papeis desconhecidos e mensagens vazias, junta mensagens seguidas do mesmo papel
     * e garante que a conversa comece pelo usuario.
     */
    static List<Map<String, Object>> converterHistorico(List<ChatMessageDto> historico) {
        List<Map<String, Object>> contents = new ArrayList<>();
        String papelAnterior = null;
        StringBuilder acumulado = new StringBuilder();
        for (ChatMessageDto m : historico) {
            if (m == null || m.content() == null || m.content().isBlank()) continue;
            String papel = "assistant".equalsIgnoreCase(m.role()) ? "model"
                    : "user".equalsIgnoreCase(m.role()) ? "user" : null;
            if (papel == null) continue;
            if (papelAnterior == null && papel.equals("model")) continue; // precisa comecar pelo usuario
            if (papel.equals(papelAnterior)) {
                acumulado.append("\n\n").append(m.content().trim());
                continue;
            }
            if (papelAnterior != null) contents.add(conteudo(papelAnterior, acumulado.toString()));
            papelAnterior = papel;
            acumulado = new StringBuilder(m.content().trim());
        }
        if (papelAnterior != null) contents.add(conteudo(papelAnterior, acumulado.toString()));
        return contents;
    }

    private static Map<String, Object> conteudo(String papel, String texto) {
        return Map.of("role", papel, "parts", List.of(Map.of("text", texto)));
    }

    private Resposta interpretar(String json, String modelo, long ms) {
        JsonNode raiz;
        try {
            raiz = objectMapper.readTree(json == null ? "" : json);
        } catch (Exception e) {
            throw new GeminiException(GeminiException.Tipo.RESPOSTA_INVALIDA, 200, "Resposta da Gemini API nao e' JSON valido.");
        }
        String bloqueio = raiz.path("promptFeedback").path("blockReason").asText("");
        if (!bloqueio.isEmpty()) {
            throw new GeminiException(GeminiException.Tipo.CONTEUDO_BLOQUEADO, 200, "Pergunta bloqueada pela Gemini API: " + bloqueio);
        }
        JsonNode candidato = raiz.path("candidates").path(0);
        String finish = candidato.path("finishReason").asText("");
        StringBuilder texto = new StringBuilder();
        for (JsonNode parte : candidato.path("content").path("parts")) {
            if (parte.path("thought").asBoolean(false)) continue; // nunca mostra o "raciocinio" interno
            texto.append(parte.path("text").asText(""));
        }
        if (texto.toString().isBlank()) {
            GeminiException.Tipo tipo = finish.equals("SAFETY") || finish.equals("PROHIBITED_CONTENT") || finish.equals("BLOCKLIST")
                    || finish.equals("SPII") ? GeminiException.Tipo.CONTEUDO_BLOQUEADO : GeminiException.Tipo.RESPOSTA_INVALIDA;
            throw new GeminiException(tipo, 200, "Gemini nao devolveu texto (finishReason=" + (finish.isEmpty() ? "-" : finish) + ").");
        }
        return new Resposta(texto.toString().trim(), raiz.path("modelVersion").asText(modelo), ms, finish);
    }

    /** Traduz o erro HTTP do Gemini em um tipo de falha (sem nunca guardar headers da requisicao). */
    GeminiException classificar(HttpStatusCodeException ex) {
        int st = ex.getStatusCode().value();
        String status = "";
        String razao = "";
        long esperaMs = 0L;
        try {
            JsonNode erro = objectMapper.readTree(ex.getResponseBodyAsString()).path("error");
            status = erro.path("status").asText(erro.path("code").isTextual() ? erro.path("code").asText() : "");
            for (JsonNode d : erro.path("details")) {
                if (d.hasNonNull("reason")) razao = d.get("reason").asText();
                if (d.hasNonNull("retryDelay")) esperaMs = duracaoEmMs(d.get("retryDelay").asText());
            }
        } catch (Exception ignorado) {
            // corpo nao-JSON: classifica so' pelo status HTTP
        }
        String detalhe = "HTTP " + st + (status.isEmpty() ? "" : " " + status) + (razao.isEmpty() ? "" : " " + razao);
        GeminiException.Tipo tipo;
        if (st == 401 || razao.contains("API_KEY_INVALID") || razao.contains("API_KEY")) tipo = GeminiException.Tipo.CHAVE_INVALIDA;
        else if (st == 402) tipo = GeminiException.Tipo.SEM_CREDITO;
        else if (st == 403) tipo = GeminiException.Tipo.PERMISSAO_NEGADA;
        else if (st == 404) tipo = GeminiException.Tipo.MODELO_INDISPONIVEL;
        else if (st == 429) tipo = GeminiException.Tipo.LIMITE_REQUISICOES;
        else if (st == 504) tipo = GeminiException.Tipo.TIMEOUT;
        else if (st >= 500) tipo = GeminiException.Tipo.INSTABILIDADE;
        else tipo = GeminiException.Tipo.REQUISICAO_RECUSADA;
        if (esperaMs > 0) detalhe += " (aguardar " + esperaMs / 1000 + " s)";
        return new GeminiException(tipo, st, detalhe, esperaMs);
    }

    /** "40s" / "1.5s" / "250ms" -> milissegundos (formato google.protobuf.Duration). */
    static long duracaoEmMs(String duracao) {
        try {
            String d = duracao.trim();
            if (d.endsWith("ms")) return Math.round(Double.parseDouble(d.substring(0, d.length() - 2)));
            if (d.endsWith("s")) return Math.round(Double.parseDouble(d.substring(0, d.length() - 1)) * 1000);
        } catch (NumberFormatException ignorado) {
            // formato inesperado: ignora a sugestao
        }
        return 0L;
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
