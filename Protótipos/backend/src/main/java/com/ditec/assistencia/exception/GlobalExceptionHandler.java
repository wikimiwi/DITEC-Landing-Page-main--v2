package com.ditec.assistencia.exception;

import com.ditec.assistencia.dto.common.ErrorResponse;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;

/**
 * Centraliza a traducao de excecoes em respostas HTTP consistentes
 * (secao 29 do prompt mestre: 401/403/404/409/422/500).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ErrorResponse> handleNaoEncontrado(RecursoNaoEncontradoException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), req);
    }

    @ExceptionHandler(ConflitoException.class)
    public ResponseEntity<ErrorResponse> handleConflito(ConflitoException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), req);
    }

    @ExceptionHandler(RegraDeNegocioException.class)
    public ResponseEntity<ErrorResponse> handleRegraNegocio(RegraDeNegocioException ex, HttpServletRequest req) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "Unprocessable Entity", ex.getMessage(), req);
    }

    @ExceptionHandler({AcessoNegadoException.class, AccessDeniedException.class})
    public ResponseEntity<ErrorResponse> handleAcessoNegado(RuntimeException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, "Forbidden", "Acesso nao autorizado.", req);
    }

    @ExceptionHandler({CredenciaisInvalidasException.class, BadCredentialsException.class,
                        AuthenticationException.class, JwtException.class})
    public ResponseEntity<ErrorResponse> handleNaoAutenticado(RuntimeException ex, HttpServletRequest req) {
        String msg = ex instanceof CredenciaisInvalidasException ? ex.getMessage() : "Nao autorizado.";
        return build(HttpStatus.UNAUTHORIZED, "Unauthorized", msg, req);
    }

    @ExceptionHandler(IntegracaoExternaException.class)
    public ResponseEntity<ErrorResponse> handleIntegracaoExterna(IntegracaoExternaException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_GATEWAY, "Bad Gateway", ex.getMessage(), req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidacao(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<Map<String, String>> campos = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of("campo", fe.getField(), "erro", String.valueOf(fe.getDefaultMessage())))
                .toList();
        ErrorResponse body = ErrorResponse.validacao(422, "Dados invalidos.", req.getRequestURI(), campos);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest req) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "Unprocessable Entity", ex.getMessage(), req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenerico(Exception ex, HttpServletRequest req) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "Ocorreu um erro inesperado. Tente novamente em instantes.", req);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String error, String message, HttpServletRequest req) {
        ErrorResponse body = ErrorResponse.of(status.value(), error, message, req.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
