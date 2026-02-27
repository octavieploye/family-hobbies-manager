package com.familyhobbies.userservice.controller;

import com.familyhobbies.errorhandling.exception.web.UnauthorizedException;
import com.familyhobbies.userservice.dto.request.LoginRequest;
import com.familyhobbies.userservice.dto.request.RefreshTokenRequest;
import com.familyhobbies.userservice.dto.request.RegisterRequest;
import com.familyhobbies.userservice.dto.response.AuthResponse;
import com.familyhobbies.userservice.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        if (userIdHeader == null || userIdHeader.isBlank()) {
            throw new UnauthorizedException("Missing X-User-Id header: authentication required");
        }

        Long userId;
        try {
            userId = Long.valueOf(userIdHeader.trim());
        } catch (NumberFormatException e) {
            throw new UnauthorizedException("Invalid X-User-Id header: must be a valid user identifier");
        }

        authService.logout(userId);
        return ResponseEntity.noContent().build();
    }
}
