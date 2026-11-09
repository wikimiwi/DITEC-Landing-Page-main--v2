package com.ditec.assistencia.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Regras auxiliares: conferencia de telefone no rastreio e FAQ do chatbot (sem Spring). */
class RegrasAuxiliaresTest {

    @Test
    void telefoneConfereIgnorandoMascaraEDdi() {
        assertThat(OrdemServicoService.telefoneConfere("(11) 97777-2222", "11977772222")).isTrue();
        assertThat(OrdemServicoService.telefoneConfere("(11) 97777-2222", "+55 11 97777-2222")).isTrue();
        assertThat(OrdemServicoService.telefoneConfere("(11) 97777-2222", "(11) 97777-2223")).isFalse();
        assertThat(OrdemServicoService.telefoneConfere("(11) 97777-2222", "2222")).isFalse();
        assertThat(OrdemServicoService.telefoneConfere(null, "11977772222")).isFalse();
    }

    @Test
    void chatbotAutomaticoRespondeRegrasDeNegocio() {
        ChatFallbackService faq = new ChatFallbackService();
        assertThat(faq.responder("Qual a garantia?")).contains("90 dias");
        assertThat(faq.responder("O diagnóstico é gratuito?")).contains("gratuito");
        assertThat(faq.responder("Tem desconto no pix?")).contains("5%");
        assertThat(faq.responder("Vocês abrem no domingo?")).contains("07h às 20h");
        assertThat(faq.responder("Meu fogão a gás está com cheiro")).contains("prioridade");
        assertThat(faq.responder("Emitem nota fiscal?")).contains("WhatsApp").doesNotContain("Emitimos");
        assertThat(faq.responder("Quero mais informações")).doesNotContain("documentos fiscais")
                .contains(ChatFallbackService.AVISO.trim());
    }
}
