package com.fiapx.video.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Valida o JWT emitido pelo auth-service (segredo HS256 compartilhado).
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final String issuer;

    public JwtService(JwtProperties properties) {
        byte[] bytes = properties.getSecret() == null
            ? new byte[0]
            : properties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("fiapx.jwt.secret deve ter pelo menos 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.issuer = properties.getIssuer();
    }

    public UsuarioPrincipal extrairPrincipal(String token) {
        Claims claims = Jwts.parser()
            .verifyWith(key)
            .requireIssuer(issuer)
            .build()
            .parseSignedClaims(token)
            .getPayload();

        return new UsuarioPrincipal(
            UUID.fromString(claims.getSubject()),
            claims.get("nome", String.class),
            claims.get("email", String.class),
            claims.get("role", String.class)
        );
    }
}
