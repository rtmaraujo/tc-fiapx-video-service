package com.fiapx.video.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fiapx.email")
public class EmailProperties {

    private String remetente = "nao-responda@fiapx.com";
    private int maximoTentativas = 5;

    public String getRemetente() {
        return remetente;
    }

    public void setRemetente(String remetente) {
        this.remetente = remetente;
    }

    public int getMaximoTentativas() {
        return maximoTentativas;
    }

    public void setMaximoTentativas(int maximoTentativas) {
        this.maximoTentativas = maximoTentativas;
    }
}
