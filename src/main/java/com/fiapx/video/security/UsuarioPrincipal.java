package com.fiapx.video.security;

import com.fiapx.video.domain.Role;

import java.util.UUID;

public record UsuarioPrincipal(UUID id, String nome, String email, String role) {

    public boolean admin() {
        return Role.ADMIN.name().equals(role);
    }
}
