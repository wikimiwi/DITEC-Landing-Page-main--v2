package com.ditec.assistencia.service;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Respostas automaticas do chatbot quando a IA externa (Google Gemini) nao esta
 * configurada ou esta fora do ar — garante o atendimento 24h com as
 * informacoes oficiais da DITEC. Toda resposta deste modo e' identificada como
 * automatica (nao e' gerada por IA), para nao simular uma integracao.
 */
@Component
public class ChatFallbackService {

    static final String AVISO = "\n\n(Resposta automática — o assistente de IA está indisponível no momento.)";

    private final Map<List<String>, String> respostas = new LinkedHashMap<>();

    public ChatFallbackService() {
        respostas.put(List.of("gas", "vazamento", "cheiro"),
                "Aparelhos a gás têm **prioridade máxima** no atendimento. Em caso de cheiro de gás, feche o registro, "
                        + "abra as janelas, não acione interruptores e fale com a gente pelo WhatsApp (11) 99999-9999.");
        respostas.put(List.of("garantia"),
                "Todos os serviços têm **garantia de 90 dias** em peças e mão de obra, contada a partir da conclusão do serviço.");
        respostas.put(List.of("diagnostico", "orcamento", "avaliacao"),
                "O **diagnostico é gratuito**. Depois da avaliação você recebe o orçamento e decide se aprova — "
                        + "se recusar, não paga nada.");
        respostas.put(List.of("pagamento", "pagar", "pix", "cartao", "dinheiro", "desconto", "parcel", "boleto"),
                "Aceitamos Pix, cartão de débito/crédito (até 12x), boleto e dinheiro. Pagamento **à vista (Pix ou dinheiro) "
                        + "tem 5% de desconto**.");
        respostas.put(List.of("nota fiscal", " nf ", " nf-e", " nfe", "fiscal", "recibo", "comprovante"),
                "Para comprovantes e documentos fiscais do seu atendimento, fale com a equipe pelo WhatsApp (11) 99999-9999.");
        respostas.put(List.of("horario", "funciona", "abre", "sabado", "domingo", "aberto"),
                "Atendimento presencial de **segunda a sábado, das 07h às 20h** (não atendemos aos domingos). "
                        + "Este chat funciona 24 horas.");
        respostas.put(List.of("bairro", "regiao", "atende", "cep", "cidade", "area"),
                "Atendemos a cidade de **São Paulo e regiões selecionadas da Grande SP** (ex.: Osasco, Barueri, Santo André, "
                        + "São Bernardo, Diadema). Use o verificador de CEP/bairro na página inicial.");
        respostas.put(List.of("rastre", "protocolo", "ordem de servico", " os", "status", "andamento"),
                "Para acompanhar sua OS, vá em **Rastrear OS** no site e informe o protocolo (DITEC-AAAA-NNNNNN) "
                        + "e o telefone usado no agendamento.");
        respostas.put(List.of("agend", "visita", "marcar", "tecnico"),
                "Você pode agendar pelo site na seção **Agendar**: escolha o dia e o horário livre no calendario e preencha "
                        + "seus dados. Você recebe um protocolo para acompanhar a OS.");
        respostas.put(List.of("servico", "conserta", "geladeira", "lavar", "fogao", "micro", "ar condicionado", "freezer", "secadora"),
                "Consertamos geladeiras e freezers, máquinas de lavar e secadoras, fogões e fornos, micro-ondas e ar-condicionado "
                        + "das principais marcas.");
    }

    public String responder(String pergunta) {
        String p = " " + RegrasNegocioService.normalizar(pergunta == null ? "" : pergunta) + " ";
        for (var entrada : respostas.entrySet()) {
            for (String chave : entrada.getKey()) {
                if (p.contains(chave)) return entrada.getValue() + AVISO;
            }
        }
        return "Sou o assistente da DITEC Assistência Técnica. Posso ajudar com serviços, agendamento, garantia (90 dias), "
                + "diagnóstico gratuito, formas de pagamento e rastreamento de OS. Para falar com a equipe, chame no "
                + "WhatsApp (11) 99999-9999." + AVISO;
    }
}
