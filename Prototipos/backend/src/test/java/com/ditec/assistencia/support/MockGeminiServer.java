package com.ditec.assistencia.support;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Servidor HTTP local que imita a Gemini API nos testes (nenhuma chamada real ao Google,
 * nenhuma cota consumida). Respostas sao enfileiradas; cada requisicao recebida e' registrada
 * (caminho, query, headers e corpo) para as verificacoes.
 */
public class MockGeminiServer implements AutoCloseable {

    public record Resposta(int status, String corpo, long atrasoMs) {
    }

    public record Requisicao(String caminho, String query, Map<String, List<String>> headers, String corpo) {
        public String header(String nome) {
            return headers.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(nome))
                    .findFirst().map(e -> e.getValue().get(0)).orElse(null);
        }
    }

    private final HttpServer server;
    private final ConcurrentLinkedQueue<Resposta> fila = new ConcurrentLinkedQueue<>();
    private final List<Requisicao> recebidas = new CopyOnWriteArrayList<>();

    public MockGeminiServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", troca -> {
            String corpo = new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            recebidas.add(new Requisicao(troca.getRequestURI().getPath(), troca.getRequestURI().getQuery(),
                    troca.getRequestHeaders(), corpo));
            Resposta r = fila.poll();
            if (r == null) r = new Resposta(500, "{\"error\":{\"code\":500,\"status\":\"INTERNAL\"}}", 0);
            if (r.atrasoMs() > 0) {
                try {
                    Thread.sleep(r.atrasoMs());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] bytes = r.corpo().getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "application/json");
            try {
                troca.sendResponseHeaders(r.status(), bytes.length);
                try (OutputStream os = troca.getResponseBody()) {
                    os.write(bytes);
                }
            } catch (IOException ignorado) {
                // cliente desistiu (teste de timeout)
            }
        });
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta";
    }

    public MockGeminiServer responder(int status, String corpo) {
        fila.add(new Resposta(status, corpo, 0));
        return this;
    }

    public MockGeminiServer responderComAtraso(int status, String corpo, long atrasoMs) {
        fila.add(new Resposta(status, corpo, atrasoMs));
        return this;
    }

    public MockGeminiServer sucesso(String texto) {
        return responder(200, """
                {"candidates":[{"content":{"role":"model","parts":[{"text":%s}]},"finishReason":"STOP"}],
                 "usageMetadata":{"promptTokenCount":10,"candidatesTokenCount":5,"totalTokenCount":15},
                 "modelVersion":"gemini-2.5-flash"}
                """.formatted(com.fasterxml.jackson.databind.node.TextNode.valueOf(texto).toString()));
    }

    public static String erro(int codigo, String status, String razao) {
        String detalhes = razao == null ? "" : ",\"details\":[{\"@type\":\"type.googleapis.com/google.rpc.ErrorInfo\",\"reason\":\"" + razao + "\"}]";
        return "{\"error\":{\"code\":" + codigo + ",\"message\":\"erro simulado\",\"status\":\"" + status + "\"" + detalhes + "}}";
    }

    public List<Requisicao> recebidas() {
        return recebidas;
    }

    public void limpar() {
        fila.clear();
        recebidas.clear();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
