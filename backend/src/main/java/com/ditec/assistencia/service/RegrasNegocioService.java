package com.ditec.assistencia.service;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Regras de negocio da DITEC (DRS secao 23) que precisam ser aplicadas de
 * verdade no backend — no frontend antigo elas so existiam no JavaScript
 * (CONFIG.bairrosAtendidos / cepRanges / HORARIOS em script.js), o que
 * significa que qualquer requisicao direta a API pulava a validacao.
 *
 * A lista de bairros/CEPs e' EXATAMENTE a mesma que ja existia em
 * script.js (CONFIG), para nao mudar quem e' ou nao atendido.
 */
@Component
public class RegrasNegocioService {

    private static final Pattern DIACRITICOS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    /** Mesmos horarios fixos que ja existiam no calendario (script.js -> HORARIOS). */
    public static final List<LocalTime> HORARIOS_PERMITIDOS = List.of(
            LocalTime.of(7, 0), LocalTime.of(8, 30), LocalTime.of(10, 0), LocalTime.of(11, 30),
            LocalTime.of(13, 0), LocalTime.of(14, 30), LocalTime.of(16, 0), LocalTime.of(17, 30),
            LocalTime.of(19, 0)
    );

    private static final Set<String> BAIRROS_ATENDIDOS = new LinkedHashSet<>(List.of(
            "moema", "vila mariana", "brooklin", "campo belo", "jabaquara", "saude",
            "ipiranga", "cursino", "sacoma", "santo amaro", "santo andre", "sao bernardo",
            "diadema", "pinheiros", "itaim bibi", "jardins", "jardim paulista",
            "consolacao", "higienopolis", "perdizes", "lapa",
            "butanta", "morumbi", "vila madalena", "barra funda", "santana", "tucuruvi",
            "vila guilherme", "tremembe", "casa verde", "limao", "penha",
            "tatuape", "sao miguel", "itaquera", "mooca", "belem",
            "carrao", "vila formosa", "se", "republica", "bela vista",
            "liberdade", "cambuci", "centro", "bras", "bom retiro", "osasco", "carapicuiba",
            "barueri", "jandira", "cotia", "embu", "taboao"
    ));

    private static final List<String[]> FAIXAS_CEP = List.of(
            new String[]{"01000000", "05999999"},
            new String[]{"06000000", "08499999"},
            new String[]{"09000000", "09999999"}
    );

    public static String normalizar(String texto) {
        if (texto == null) return "";
        String semAcento = DIACRITICOS.matcher(Normalizer.normalize(texto, Normalizer.Form.NFD)).replaceAll("");
        return semAcento.toLowerCase().trim();
    }

    public boolean isBairroAtendido(String bairro) {
        String alvo = normalizar(bairro);
        if (alvo.isEmpty()) return false;
        return BAIRROS_ATENDIDOS.stream().anyMatch(b -> alvo.contains(b) || b.contains(alvo));
    }

    public boolean isCepAtendido(String cep) {
        if (cep == null) return false;
        String digitos = cep.replaceAll("\\D", "");
        if (digitos.length() != 8) return false;
        return FAIXAS_CEP.stream().anyMatch(faixa -> digitos.compareTo(faixa[0]) >= 0 && digitos.compareTo(faixa[1]) <= 0);
    }

    public boolean isDiaAtendido(LocalDate data) {
        return data.getDayOfWeek() != DayOfWeek.SUNDAY;
    }

    public boolean isHorarioPermitido(LocalTime horario) {
        return HORARIOS_PERMITIDOS.contains(horario);
    }

    /** Aparelhos a gas recebem prioridade maxima no atendimento (DRS secao 23). */
    public boolean isAparelhoGas(String tipoAparelho) {
        String alvo = normalizar(tipoAparelho);
        return alvo.contains("gas");
    }
}
