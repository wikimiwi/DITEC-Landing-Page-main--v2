package com.ditec.assistencia.controller;

import com.ditec.assistencia.dto.auth.AuthResponse;
import com.ditec.assistencia.dto.auth.LoginRequest;
import com.ditec.assistencia.dto.auth.RegisterRequest;
import com.ditec.assistencia.dto.auth.UsuarioMeResponse;
import com.ditec.assistencia.security.CustomUserDetails;
import com.ditec.assistencia.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.registrar(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public UsuarioMeResponse me(@AuthenticationPrincipal CustomUserDetails principal) {
        return authService.me(principal.getUsuario());
    }
}
