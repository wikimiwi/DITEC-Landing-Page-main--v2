package com.ditec.assistencia.service;

/** Geracao manual de CSV (sem dependencia externa — projeto pequeno, nao justifica opencsv). */
final class CsvUtils {

    private CsvUtils() {
    }

    static String campo(Object valor) {
        String s = valor == null ? "" : String.valueOf(valor);
        boolean precisaAspas = s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains(";");
        if (precisaAspas) {
            s = s.replace("\"", "\"\"");
            return "\"" + s + "\"";
        }
        return s;
    }

    static String linha(Object... campos) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < campos.length; i++) {
            if (i > 0) sb.append(';');
            sb.append(campo(campos[i]));
        }
        return sb.append('\n').toString();
    }

    /** BOM UTF-8 no inicio para o Excel (pt-BR) abrir os acentos corretamente. */
    static byte[] bytesComBom(String conteudo) {
        return ("\uFEFF" + conteudo).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
