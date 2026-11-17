package com.fiapx.video.security;

import com.fiapx.video.domain.Role;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fiapx.jwt")
public class JwtProperties {

    private String secret;
    private String issuer = "fiapx-auth";

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }
}
