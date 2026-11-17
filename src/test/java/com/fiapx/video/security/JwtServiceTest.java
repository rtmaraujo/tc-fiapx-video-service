package com.fiapx.video.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SEGREDO = "fiapx-test-secret-key-with-at-least-32-bytes";

    private JwtProperties properties;

    @BeforeEach
    void setUp() {
        properties = new JwtProperties();
        properties.setSecret(SEGREDO);
        properties.setIssuer("fiapx-auth");
    }

    @Test
    @DisplayName("extrai principal de token valido")
    void deveExtrairPrincipal() {
        UUID id = UUID.randomUUID();
        String token = token(id, "fiapx-auth", "USER");

        UsuarioPrincipal principal = new JwtService(properties).extrairPrincipal(token);

        assertThat(principal.id()).isEqualTo(id);
        assertThat(principal.nome()).isEqualTo("Maria");
        assertThat(principal.email()).isEqualTo("maria@fiapx.com");
        assertThat(principal.role()).isEqualTo("USER");
        assertThat(principal.admin()).isFalse();
        assertThat(new UsuarioPrincipal(id, "Maria", "maria@fiapx.com", "ADMIN").admin()).isTrue();
    }

    @Test
    @DisplayName("rejeita token de outro issuer")
    void deveRejeitarIssuerDiferente() {
        String token = token(UUID.randomUUID(), "outro", "USER");

        assertThatThrownBy(() -> new JwtService(properties).extrairPrincipal(token))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("rejeita token de outro segredo")
    void deveRejeitarAssinaturaInvalida() {
        JwtProperties outro = new JwtProperties();
        outro.setSecret("outro-segredo-com-pelo-menos-32-bytes-aqui");
        outro.setIssuer("fiapx-auth");
        String token = Jwts.builder()
            .subject(UUID.randomUUID().toString())
            .issuer("fiapx-auth")
            .expiration(Date.from(Instant.now().plusSeconds(60)))
            .claim("role", "USER")
            .signWith(Keys.hmacShaKeyFor(outro.getSecret().getBytes(StandardCharsets.UTF_8)))
            .compact();

        assertThatThrownBy(() -> new JwtService(properties).extrairPrincipal(token))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("rejeita segredo curto")
    void deveRejeitarSegredoCurto() {
        properties.setSecret("curto");

        assertThatThrownBy(() -> new JwtService(properties))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("32 bytes");
    }

    @Test
    @DisplayName("rejeita segredo nulo")
    void deveRejeitarSegredoNulo() {
        properties.setSecret(null);

        assertThatThrownBy(() -> new JwtService(properties))
            .isInstanceOf(IllegalStateException.class);
    }

    private String token(UUID id, String issuer, String role) {
        return Jwts.builder()
            .subject(id.toString())
            .issuer(issuer)
            .issuedAt(new Date())
            .expiration(Date.from(Instant.now().plusSeconds(3600)))
            .claim("nome", "Maria")
            .claim("email", "maria@fiapx.com")
            .claim("role", role)
            .signWith(Keys.hmacShaKeyFor(SEGREDO.getBytes(StandardCharsets.UTF_8)))
            .compact();
    }
}
