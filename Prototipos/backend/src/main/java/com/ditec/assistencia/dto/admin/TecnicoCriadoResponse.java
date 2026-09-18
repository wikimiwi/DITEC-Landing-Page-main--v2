package com.ditec.assistencia.dto.admin;

/**
 * Resposta da criacao de um tecnico. senhaTemporaria so vem preenchida
 * quando o admin NAO informou uma senha manualmente — precisa ser repassada
 * ao tecnico (por um canal seguro) para o primeiro acesso, pois o backend
 * guarda apenas o hash e nao ha' como recupera-la depois.
 */
public record TecnicoCriadoResponse(TecnicoResponse tecnico, String senhaTemporaria) {
}
