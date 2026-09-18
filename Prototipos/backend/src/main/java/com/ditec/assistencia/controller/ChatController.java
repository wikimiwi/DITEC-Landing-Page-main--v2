package com.ditec.assistencia.controller;

import com.ditec.assistencia.dto.chat.ChatCompletionResponse;
import com.ditec.assistencia.dto.chat.ChatRequest;
import com.ditec.assistencia.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Proxy do chatbot — publico, mesmo contrato ja usado pelo script.js (askMistral). */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    public ChatCompletionResponse chat(@Valid @RequestBody ChatRequest request, Authentication auth) {
        return chatService.enviar(request, auth);
    }
}
