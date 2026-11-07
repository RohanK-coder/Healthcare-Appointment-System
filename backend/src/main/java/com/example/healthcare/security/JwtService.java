package com.example.healthcare.security;

import com.example.healthcare.entity.AppUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {
    static final String DEV_SECRET_MARKER = "dev-only";
    private static final String TOKEN_VERSION_CLAIM = "tv";

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-ms}") long expirationMs,
                      Environment environment) {
        if (secret.length() < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 characters.");
        }
        if (environment.acceptsProfiles(Profiles.of("prod")) && secret.contains(DEV_SECRET_MARKER)) {
            throw new IllegalStateException("Refusing to start: set JWT_SECRET to a real secret in the prod profile.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(AppUser user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("uid", user.getId())
                .claim("role", user.getRole().name())
                .claim(TOKEN_VERSION_CLAIM, user.getTokenVersion())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    /** Parses and verifies signature and expiry; throws on any invalid token. */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    /**
     * A token is valid for the user it names, while the account is enabled, and only while its
     * token version matches the user's (a password change bumps the version, revoking old tokens).
     */
    public boolean isTokenValid(Claims claims, UserPrincipal principal) {
        Integer version = claims.get(TOKEN_VERSION_CLAIM, Integer.class);
        return claims.getSubject().equals(principal.getUsername())
                && principal.isEnabled()
                && version != null
                && version == principal.getUser().getTokenVersion();
    }
}
