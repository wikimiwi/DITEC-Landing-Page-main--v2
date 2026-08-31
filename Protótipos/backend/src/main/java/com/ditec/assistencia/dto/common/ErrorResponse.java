package com.ditec.assistencia.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Formato padrao de erro devolvido pela API (seção 29 do prompt mestre). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        List<Map<String, String>> camposInvalidos
) {
    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(LocalDateTime.now(), status, error, message, path, null);
    }

    public static ErrorResponse validacao(int status, String message, String path,
                                           List<Map<String, String>> camposInvalidos) {
        return new ErrorResponse(LocalDateTime.now(), status, "Validation Error", message, path, camposInvalidos);
    }
}
