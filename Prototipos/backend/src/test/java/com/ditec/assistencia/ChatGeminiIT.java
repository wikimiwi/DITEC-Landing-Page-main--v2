package com.ditec.assistencia;

import com.ditec.assistencia.repository.ChatLogRepository;
import com.ditec.assistencia.support.MockGeminiServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/chat com a Gemini API SIMULADA (servidor local): o endpoint mantem o mesmo
 * contrato do front-end, repassa o contexto, trata falhas com resposta automatica e
 * nunca registra a chave nos logs. Nenhuma chamada real ao Google.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD) // estado de pausa e' por instancia
class ChatGeminiIT {

    private static final String CHAVE = "chave-falsa-para-testes-XYZ987";
    private static final MockGeminiServer GEMINI;

    static {
        try {
            GEMINI = new MockGeminiServer();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void gemini(DynamicPropertyRegistry r) {
        r.add("ditec.gemini.api-key", () -> CHAVE);
        r.add("ditec.gemini.api-url", GEMINI::baseUrl);
        r.add("ditec.gemini.model", () -> "gemini-2.5-flash");
        r.add("ditec.gemini.fallback-model", () -> "");
        r.add("ditec.gemini.read-timeout-ms", () -> "1500");
        // banco H2 proprio: o @DirtiesContext fecha o contexto e o create-drop apagaria
        // as tabelas do banco compartilhado pelos outros testes
        r.add("spring.datasource.url", () -> "jdbc:h2:mem:ditec_chat_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
    }

    @AfterAll
    static void parar() {
        GEMINI.close();
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ChatLogRepository chatLogRepository;

    @BeforeEach
    void limpar() {
        GEMINI.limpar();
    }

    private String conversa(String... falas) throws Exception {
        var mensagens = new java.util.ArrayList<java.util.Map<String, String>>();
        for (int i = 0; i < falas.length; i++) mensagens.add(java.util.Map.of("role", i % 2 == 0 ? "user" : "assistant", "content", falas[i]));
        // mesmo formato que o script.js envia (inclui campos antigos que o servidor ignora)
        return objectMapper.writeValueAsString(java.util.Map.of("messages", mensagens, "max_tokens", 250,
                "temperature", 0.6, "stream", false, "sessionId", "sessao-teste"));
    }

    @Test
    void respondeComIaNoMesmoContratoDoFrontEMantemContexto(CapturedOutput saida) throws Exception {
        GEMINI.sucesso("A garantia é de 90 dias.");
        long logsAntes = chatLogRepository.count();

        mockMvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content(conversa("Minha geladeira não gela", "Qual a marca?", "Brastemp. E a garantia?")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.choices[0].message.role").value("assistant"))
                .andExpect(jsonPath("$.choices[0].message.content").value("A garantia é de 90 dias."))
                .andExpect(jsonPath("$.fonte").value("ia"));

        JsonNode enviado = objectMapper.readTree(GEMINI.recebidas().get(0).corpo());
        assertThat(enviado.get("contents")).hasSize(3);                       // contexto completo
        assertThat(enviado.at("/systemInstruction/parts/0/text").asText()).contains("DITEC", "90 dias", "das 7h às 20h", "Nunca peça senhas");
        assertThat(chatLogRepository.count()).isEqualTo(logsAntes + 1);       // conversa registrada
        assertThat(saida.getAll()).doesNotContain(CHAVE);                     // chave nunca vai para o log
    }

    @Test
    void chaveInvalidaRespondeAutomaticamenteEPausaAsChamadas(CapturedOutput saida) throws Exception {
        GEMINI.responder(400, MockGeminiServer.erro(400, "INVALID_ARGUMENT", "API_KEY_INVALID"));

        mockMvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content(conversa("Qual a garantia?")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fonte").value("automatica"))
                .andExpect(jsonPath("$.choices[0].message.content").value(org.hamcrest.Matchers.containsString("90 dias")));
        mockMvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content(conversa("Atendem domingo?")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fonte").value("automatica"));

        assertThat(GEMINI.recebidas()).hasSize(1);                           // 2a pergunta nem chega a IA (pausa)
        assertThat(saida.getAll()).contains("chave Gemini invalida").doesNotContain(CHAVE);
    }

    @Test
    void limiteDoPlanoGratuitoResultaEmRespostaAmigavel() throws Exception {
        for (int i = 0; i < 3; i++) GEMINI.responder(429, MockGeminiServer.erro(429, "RESOURCE_EXHAUSTED", null));
        mockMvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content(conversa("Tem desconto no Pix?")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fonte").value("automatica"))
                .andExpect(jsonPath("$.choices[0].message.content").value(org.hamcrest.Matchers.containsString("5%")));
        assertThat(GEMINI.recebidas()).hasSize(3);                           // tentativas limitadas
    }

    @Test
    void conteudoBloqueadoRecebeMensagemEducada() throws Exception {
        GEMINI.responder(200, "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}");
        mockMvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content(conversa("conteudo improprio")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.choices[0].message.content").value(org.hamcrest.Matchers.containsString("não posso ajudar")));
    }

    @Test
    void validacoesDoEndpointContinuamAtivas() throws Exception {
        mockMvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{\"messages\":[]}"))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content(conversa("x".repeat(2001))))
                .andExpect(status().isUnprocessableEntity());
        assertThat(GEMINI.recebidas()).isEmpty();
    }
}
