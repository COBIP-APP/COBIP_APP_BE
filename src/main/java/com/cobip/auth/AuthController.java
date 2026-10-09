package com.cobip.auth;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
class AuthController {
    private final AuthService auth;

    AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/email-verifications/send")
    ResponseEntity<AuthDtos.SendEmailResponse> sendCode(
            @Valid @RequestBody AuthDtos.SendEmailRequest request) {
        return ResponseEntity.accepted().body(auth.sendCode(request.email()));
    }

    @PostMapping("/email-verifications/confirm")
    AuthDtos.ConfirmEmailResponse confirmCode(
            @Valid @RequestBody AuthDtos.ConfirmEmailRequest request) {
        return auth.confirmCode(request.email(), request.code());
    }

    @PostMapping("/register")
    ResponseEntity<AuthDtos.UserResponse> register(
            @Valid @RequestBody AuthDtos.RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(auth.register(request));
    }

    @PostMapping("/login")
    AuthDtos.TokenResponse login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return auth.login(request);
    }

    @PostMapping("/refresh")
    AuthDtos.TokenResponse refresh(@Valid @RequestBody AuthDtos.RefreshRequest request) {
        return auth.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt accessToken,
                                @Valid @RequestBody AuthDtos.LogoutRequest request) {
        auth.logout(accessToken, request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
