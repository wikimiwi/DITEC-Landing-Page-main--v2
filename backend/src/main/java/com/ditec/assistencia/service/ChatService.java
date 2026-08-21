package com.ditec.assistencia.service;

import com.ditec.assistencia.config.HuggingFaceProperties;
import com.ditec.assistencia.dto.chat.ChatCompletionResponse;
import com.ditec.assistencia.dto.chat.ChatMessageDto;
import com.ditec.assistencia.dto.chat.ChatRequest;
import com.ditec.assistencia.entity.ChatLog;
import com.ditec.assistencia.enums.TipoUsuario;
import com.ditec.assistencia.exception.IntegracaoExternaException;
import com.ditec.assistencia.repository.ChatLogRepository;
import com.ditec.assistencia.repository.ClienteRepository;
import com.ditec.assistencia.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.UUID;

/**
 * Proxy seguro para a IA (Hugging Face) — secao 16 do prompt mestre.
 *
 * Mantem o MESMO contrato que o script.js ja implementa (askMistral):
 * recebe {model, messages, max_tokens, temperature, stream}, devolve
 * {choices: [{message: {content}}]}. A HF_API_KEY nunca sai do backend.
 * Cada pergunta/resposta e' registrada em chat_log (UC010/UC011/UC013).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final RestClient huggingFaceRestClient;
    private final HuggingFaceProperties hfProperties;
    private final ChatLogRepository chatLogRepository;
    private final ClienteRepository clienteRepository;

    public ChatCompletionResponse enviar(ChatRequest req, Authentication auth) {
        if (hfProperties.apiKey() == null || hfProperties.apiKey().isBlank()) {
            throw new IntegracaoExternaException(
                    "O chatbot ainda nao foi configurado neste servidor (HF_API_KEY ausente).");
        }
        if (req.messages() == null || req.messages().isEmpty()) {
            throw new IntegracaoExternaException("Nenhuma mensagem enviada.");
        }

        String modelo = (req.model() != null && !req.model().isBlank()) ? req.model() : hfProperties.defaultModel();
        Map<String, Object> corpo = Map.of(
                "model", modelo,
                "messages", req.messages(),
                "max_tokens", req.maxTokens() != null ? req.maxTokens() : 250,
                "temperature", req.temperature() != null ? req.temperature() : 0.6,
                "stream", false
        );

        ChatCompletionResponse resposta;
        try {
            resposta = huggingFaceRestClient.post()
                    .uri(hfProperties.baseUrl())
                    .header("Authorization", "Bearer " + hfProperties.apiKey())
                    .header("Content-Type", "application/json")
                    .body(corpo)
                    .retrieve()
                    .body(ChatCompletionResponse.class);
        } catch (RestClientException ex) {
            log.error("Falha ao chamar a Hugging Face: {}", ex.getMessage());
            throw new IntegracaoExternaException(
                    "Nao foi possivel falar com a IA agora. Tente novamente em instantes.");
        }

        String texto = resposta != null ? resposta.primeiroTexto() : null;
        if (texto == null || texto.isBlank()) {
            throw new IntegracaoExternaException("A IA respondeu em um formato inesperado.");
        }

        registrarLog(req, texto, auth);

        return ChatCompletionResponse.deTexto(texto);
    }

    private void registrarLog(ChatRequest req, String resposta, Authentication auth) {
        try {
            String pergunta = req.messages().stream()
                    .filter(m -> "user".equalsIgnoreCase(m.role()))
                    .map(ChatMessageDto::content)
                    .reduce((primeira, ultima) -> ultima) // pega a mais recente
                    .orElse("");

            String sessaoId = (req.sessionId() != null && !req.sessionId().isBlank())
                    ? req.sessionId() : UUID.randomUUID().toString();

            ChatLog.ChatLogBuilder builder = ChatLog.builder()
                    .sessaoId(sessaoId)
                    .pergunta(pergunta)
                    .resposta(resposta);

            if (auth != null && auth.getPrincipal() instanceof CustomUserDetails cud
                    && cud.getUsuario().getTipo() == TipoUsuario.CLIENTE) {
                clienteRepository.findByUsuario_Id(cud.getId()).ifPresent(builder::cliente);
            }

            chatLogRepository.save(builder.build());
        } catch (Exception ex) {
            // Falha ao logar nunca deve derrubar a resposta do chat pro usuario.
            log.warn("Nao foi possivel registrar o log do chatbot: {}", ex.getMessage());
        }
    }
}
