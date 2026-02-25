package com.familyhobbies.userservice.security;

import com.familyhobbies.userservice.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private static final long ACCESS_TOKEN_VALIDITY_MS = 3_600_000;    // 1 hour
    private static final long REFRESH_TOKEN_VALIDITY_MS = 604_800_000; // 7 days

    private final SecretKey signingKey;

    public JwtTokenProvider(@Value("${jwt.secret}") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate a signed JWT access token containing user identity and roles.
     */
    public String generateAccessToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + ACCESS_TOKEN_VALIDITY_MS);

        return Jwts.builder()
            .setId(UUID.randomUUID().toString())
            .setSubject(String.valueOf(user.getId()))
            .claim("email", user.getEmail())
            .claim("roles", List.of(user.getRole().name()))
            .claim("firstName", user.getFirstName())
            .claim("lastName", user.getLastName())
            .setIssuedAt(now)
            .setExpiration(expiry)
            .signWith(signingKey, SignatureAlgorithm.HS256)
            .compact();
    }

    /**
     * Generate an opaque refresh token (UUID).
     */
    public String generateRefreshToken() {
        return UUID.randomUUID().toString();
    }

    /**
     * Validate the token signature and expiry. Returns claims if valid.
     * Throws ExpiredJwtException if expired, JwtException if invalid.
     */
    public Claims validateToken(String token) {
        return Jwts.parserBuilder()
            .setSigningKey(signingKey)
            .build()
            .parseClaimsJws(token)
            .getBody();
    }

    /**
     * Extract user ID from a validated token.
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = validateToken(token);
        return Long.valueOf(claims.getSubject());
    }

    /**
     * Extract roles from a validated token.
     */
    @SuppressWarnings("unchecked")
    public List<String> getRolesFromToken(String token) {
        Claims claims = validateToken(token);
        return claims.get("roles", List.class);
    }

    /**
     * Calculate the refresh token expiry date from now.
     */
    public Date getRefreshTokenExpiry() {
        return new Date(System.currentTimeMillis() + REFRESH_TOKEN_VALIDITY_MS);
    }
}
