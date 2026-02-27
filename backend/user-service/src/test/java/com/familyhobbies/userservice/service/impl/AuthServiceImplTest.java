package com.familyhobbies.userservice.service.impl;

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
import com.familyhobbies.userservice.repository.RefreshTokenRepository;
import com.familyhobbies.userservice.repository.UserRepository;
import com.familyhobbies.userservice.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthServiceImpl.
 *
 * Story: S1-002, S1-003 -- Implement User Registration, Login, Refresh, and Logout
 * Tests: 10 test methods
 *
 * These tests verify:
 * - register: success (saves user, generates tokens), duplicate email throws ConflictException
 * - login: success (validates password, generates tokens, updates lastLoginAt), wrong password throws
 *   UnauthorizedException, inactive user throws UnauthorizedException
 * - refreshToken: success (rotates token), expired token throws UnauthorizedException, revoked token
 *   throws UnauthorizedException
 * - logout: revokes all refresh tokens for user
 *
 * Uses @ExtendWith(MockitoExtension.class) -- no Spring context loaded.
 * Mocks: UserRepository, RefreshTokenRepository, PasswordEncoder, JwtTokenProvider.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthServiceImpl authService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
            .id(1L)
            .email("dupont@email.com")
            .passwordHash("$2a$12$hashedpassword")
            .firstName("Jean")
            .lastName("Dupont")
            .phone("+33612345678")
            .role(UserRole.FAMILY)
            .status(UserStatus.ACTIVE)
            .emailVerified(false)
            .build();
    }

    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("should return AuthResponse with tokens when registration is valid")
        void should_returnAuthResponseWithTokens_when_registrationIsValid() {
            // given
            RegisterRequest request = new RegisterRequest(
                "dupont@email.com", "SecureP@ss1", "Jean", "Dupont", "+33612345678");

            when(userRepository.existsByEmail("dupont@email.com")).thenReturn(false);
            when(passwordEncoder.encode("SecureP@ss1")).thenReturn("$2a$12$hashedpassword");
            when(userRepository.save(any(User.class))).thenReturn(testUser);
            when(jwtTokenProvider.generateAccessToken(testUser)).thenReturn("access-token-jwt");
            when(jwtTokenProvider.generateRefreshToken()).thenReturn("refresh-token-uuid");
            when(jwtTokenProvider.getRefreshTokenExpiry()).thenReturn(
                new Date(System.currentTimeMillis() + 604_800_000));
            when(jwtTokenProvider.getAccessTokenValiditySeconds()).thenReturn(3600L);

            // when
            AuthResponse response = authService.register(request);

            // then
            assertThat(response.accessToken()).isEqualTo("access-token-jwt");
            assertThat(response.refreshToken()).isEqualTo("refresh-token-uuid");
            assertThat(response.tokenType()).isEqualTo("Bearer");
            assertThat(response.expiresIn()).isEqualTo(3600L);

            // Verify user was saved with correct fields
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            User savedUser = userCaptor.getValue();
            assertThat(savedUser.getEmail()).isEqualTo("dupont@email.com");
            assertThat(savedUser.getPasswordHash()).isEqualTo("$2a$12$hashedpassword");
            assertThat(savedUser.getFirstName()).isEqualTo("Jean");
            assertThat(savedUser.getLastName()).isEqualTo("Dupont");
            assertThat(savedUser.getRole()).isEqualTo(UserRole.FAMILY);
            assertThat(savedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(savedUser.isEmailVerified()).isFalse();

            // Verify refresh token was saved
            verify(refreshTokenRepository).save(any(RefreshToken.class));
        }

        @Test
        @DisplayName("should throw ConflictException when email is already registered")
        void should_throwConflictException_when_emailAlreadyRegistered() {
            // given
            RegisterRequest request = new RegisterRequest(
                "dupont@email.com", "SecureP@ss1", "Jean", "Dupont", null);

            when(userRepository.existsByEmail("dupont@email.com")).thenReturn(true);

            // when & then
            // M-012: Generic message -- does NOT include the email to prevent user enumeration
            assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("An account with this email already exists");

            // Verify no user was saved
            verify(userRepository, never()).save(any(User.class));
        }
    }

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("should return AuthResponse with tokens when credentials are valid")
        void should_returnAuthResponseWithTokens_when_credentialsAreValid() {
            // given
            LoginRequest request = new LoginRequest("dupont@email.com", "SecureP@ss1");

            when(userRepository.findByEmail("dupont@email.com")).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches("SecureP@ss1", "$2a$12$hashedpassword")).thenReturn(true);
            when(jwtTokenProvider.generateAccessToken(testUser)).thenReturn("access-token-jwt");
            when(jwtTokenProvider.generateRefreshToken()).thenReturn("refresh-token-uuid");
            when(jwtTokenProvider.getRefreshTokenExpiry()).thenReturn(
                new Date(System.currentTimeMillis() + 604_800_000));
            when(jwtTokenProvider.getAccessTokenValiditySeconds()).thenReturn(3600L);

            // when
            AuthResponse response = authService.login(request);

            // then
            assertThat(response.accessToken()).isEqualTo("access-token-jwt");
            assertThat(response.refreshToken()).isEqualTo("refresh-token-uuid");
            assertThat(response.tokenType()).isEqualTo("Bearer");
            assertThat(response.expiresIn()).isEqualTo(3600L);

            // Verify lastLoginAt was updated
            verify(userRepository).save(testUser);
            assertThat(testUser.getLastLoginAt()).isNotNull();

            // Verify refresh token was saved
            verify(refreshTokenRepository).save(any(RefreshToken.class));
        }

        @Test
        @DisplayName("should throw UnauthorizedException when password is wrong")
        void should_throwUnauthorizedException_when_passwordIsWrong() {
            // given
            LoginRequest request = new LoginRequest("dupont@email.com", "WrongPassword1!");

            when(userRepository.findByEmail("dupont@email.com")).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches("WrongPassword1!", "$2a$12$hashedpassword")).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid credentials");

            // Verify no tokens were generated
            verify(jwtTokenProvider, never()).generateAccessToken(any());
        }

        @Test
        @DisplayName("should throw UnauthorizedException when user account is inactive")
        void should_throwUnauthorizedException_when_userAccountIsInactive() {
            // given
            testUser.setStatus(UserStatus.INACTIVE);
            LoginRequest request = new LoginRequest("dupont@email.com", "SecureP@ss1");

            when(userRepository.findByEmail("dupont@email.com")).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches("SecureP@ss1", "$2a$12$hashedpassword")).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid credentials");

            // Verify no tokens were generated
            verify(jwtTokenProvider, never()).generateAccessToken(any());
        }
    }

    @Nested
    @DisplayName("refreshToken")
    class RefreshTokenTests {

        @Test
        @DisplayName("should return new AuthResponse and revoke old token when refresh token is valid")
        void should_returnNewAuthResponse_when_refreshTokenIsValid() {
            // given
            RefreshTokenRequest request = new RefreshTokenRequest("valid-refresh-token");

            RefreshToken storedToken = RefreshToken.builder()
                .id(1L)
                .user(testUser)
                .token("valid-refresh-token")
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .revoked(false)
                .build();

            when(refreshTokenRepository.findByToken("valid-refresh-token")).thenReturn(Optional.of(storedToken));
            when(jwtTokenProvider.generateAccessToken(testUser)).thenReturn("new-access-token");
            when(jwtTokenProvider.generateRefreshToken()).thenReturn("new-refresh-token");
            when(jwtTokenProvider.getRefreshTokenExpiry()).thenReturn(
                new Date(System.currentTimeMillis() + 604_800_000));
            when(jwtTokenProvider.getAccessTokenValiditySeconds()).thenReturn(3600L);

            // when
            AuthResponse response = authService.refreshToken(request);

            // then
            assertThat(response.accessToken()).isEqualTo("new-access-token");
            assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
            assertThat(response.tokenType()).isEqualTo("Bearer");
            assertThat(response.expiresIn()).isEqualTo(3600L);

            // Verify old token was revoked (rotation)
            assertThat(storedToken.isRevoked()).isTrue();
            verify(refreshTokenRepository).save(storedToken);

            // Verify new refresh token was saved
            verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
        }

        @Test
        @DisplayName("should throw UnauthorizedException when refresh token has expired")
        void should_throwUnauthorizedException_when_refreshTokenHasExpired() {
            // given
            RefreshTokenRequest request = new RefreshTokenRequest("expired-refresh-token");

            RefreshToken expiredToken = RefreshToken.builder()
                .id(2L)
                .user(testUser)
                .token("expired-refresh-token")
                .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .revoked(false)
                .build();

            when(refreshTokenRepository.findByToken("expired-refresh-token")).thenReturn(Optional.of(expiredToken));

            // when & then
            assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Refresh token has expired");

            // Verify no new tokens were generated
            verify(jwtTokenProvider, never()).generateAccessToken(any());
        }

        @Test
        @DisplayName("should throw UnauthorizedException when refresh token has been revoked")
        void should_throwUnauthorizedException_when_refreshTokenHasBeenRevoked() {
            // given
            RefreshTokenRequest request = new RefreshTokenRequest("revoked-refresh-token");

            RefreshToken revokedToken = RefreshToken.builder()
                .id(3L)
                .user(testUser)
                .token("revoked-refresh-token")
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .revoked(true)
                .build();

            when(refreshTokenRepository.findByToken("revoked-refresh-token")).thenReturn(Optional.of(revokedToken));

            // when & then
            assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Refresh token has been revoked");

            // Verify no new tokens were generated
            verify(jwtTokenProvider, never()).generateAccessToken(any());
        }
    }

    @Nested
    @DisplayName("logout")
    class Logout {

        @Test
        @DisplayName("should revoke all refresh tokens for the user")
        void should_revokeAllRefreshTokens_when_logoutCalled() {
            // given
            Long userId = 1L;

            // when
            authService.logout(userId);

            // then
            verify(refreshTokenRepository).revokeAllByUserId(userId);
        }
    }
}
