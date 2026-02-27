package com.familyhobbies.userservice.service.impl;

import com.familyhobbies.common.event.UserRegisteredEvent;
import com.familyhobbies.errorhandling.exception.web.ConflictException;
import com.familyhobbies.errorhandling.exception.web.UnauthorizedException;
import com.familyhobbies.userservice.dto.request.LoginRequest;
import com.familyhobbies.userservice.dto.request.RefreshTokenRequest;
import com.familyhobbies.userservice.dto.request.RegisterRequest;
import com.familyhobbies.userservice.dto.response.AuthResponse;
import com.familyhobbies.userservice.entity.RefreshToken;
import com.familyhobbies.userservice.entity.User;
import com.familyhobbies.userservice.entity.UserRole;
import com.familyhobbies.userservice.entity.UserStatus;
import com.familyhobbies.userservice.event.UserEventPublisher;
import com.familyhobbies.userservice.repository.RefreshTokenRepository;
import com.familyhobbies.userservice.repository.UserRepository;
import com.familyhobbies.userservice.security.JwtTokenProvider;
import com.familyhobbies.userservice.service.AuthService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserEventPublisher userEventPublisher;

    public AuthServiceImpl(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            UserEventPublisher userEventPublisher) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userEventPublisher = userEventPublisher;
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Step 1: Normalize email to lowercase for case-insensitive uniqueness (M-011)
        String normalizedEmail = request.email().toLowerCase();

        // Step 2: Check email uniqueness
        if (userRepository.existsByEmail(normalizedEmail)) {
            // M-012: Generic message — do NOT include the email to prevent user enumeration
            throw new ConflictException("An account with this email already exists");
        }

        // Step 3: Create user with hashed password
        User user = User.builder()
            .email(normalizedEmail)
            .passwordHash(passwordEncoder.encode(request.password()))
            .firstName(request.firstName())
            .lastName(request.lastName())
            .phone(request.phone())
            .role(UserRole.FAMILY)
            .status(UserStatus.ACTIVE)
            .emailVerified(false)
            .build();

        user = userRepository.save(user);

        // Step 3: Publish UserRegisteredEvent (fire-and-forget — S2-006)
        UserRegisteredEvent event = new UserRegisteredEvent(
                user.getId(), user.getEmail(), user.getFirstName(), user.getLastName());
        userEventPublisher.publishUserRegistered(event);

        // Step 4: Generate token pair
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshTokenValue = createAndSaveRefreshToken(user);

        // Step 5: Return auth response
        return new AuthResponse(accessToken, refreshTokenValue, "Bearer",
            jwtTokenProvider.getAccessTokenValiditySeconds());
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        // Step 1: Normalize email to lowercase for case-insensitive lookup (M-011)
        String normalizedEmail = request.email().toLowerCase();

        // Step 2: Find user by email
        User user = userRepository.findByEmail(normalizedEmail)
            .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        // Step 3: Verify password with BCrypt
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid credentials");
        }

        // Step 4: Check account is active
        if (!user.isActive()) {
            throw new UnauthorizedException("Invalid credentials");
        }

        // Step 5: Update last login timestamp
        user.setLastLoginAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        // Step 6: Generate token pair
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshTokenValue = createAndSaveRefreshToken(user);

        return new AuthResponse(accessToken, refreshTokenValue, "Bearer",
            jwtTokenProvider.getAccessTokenValiditySeconds());
    }

    @Override
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        // Step 1: Find refresh token in database
        RefreshToken storedToken = refreshTokenRepository.findByToken(request.refreshToken())
            .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        // Step 2: Check token is not revoked
        if (storedToken.isRevoked()) {
            throw new UnauthorizedException("Refresh token has been revoked");
        }

        // Step 3: Check token is not expired
        if (storedToken.getExpiresAt().isBefore(Instant.now())) {
            throw new UnauthorizedException("Refresh token has expired");
        }

        // Step 4: Revoke the used refresh token (rotation)
        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        // Step 5: Generate new token pair
        User user = storedToken.getUser();
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String newRefreshTokenValue = createAndSaveRefreshToken(user);

        return new AuthResponse(accessToken, newRefreshTokenValue, "Bearer",
            jwtTokenProvider.getAccessTokenValiditySeconds());
    }

    @Override
    @Transactional
    public void logout(Long userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
    }

    /**
     * Generate a refresh token, save it to the database, and return the token string.
     */
    private String createAndSaveRefreshToken(User user) {
        String tokenValue = jwtTokenProvider.generateRefreshToken();

        RefreshToken refreshToken = RefreshToken.builder()
            .user(user)
            .token(tokenValue)
            .expiresAt(jwtTokenProvider.getRefreshTokenExpiry().toInstant())
            .revoked(false)
            .build();

        refreshTokenRepository.save(refreshToken);
        return tokenValue;
    }
}
