package com.fiapx.video.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fiapx.kafka.topics")
public class KafkaTopicsProperties {

    private String solicitado = "fiapx.video.solicitado";
    private String processado = "fiapx.video.processado";
    private String falha = "fiapx.video.falha";

    public String getSolicitado() {
        return solicitado;
    }

    public void setSolicitado(String solicitado) {
        this.solicitado = solicitado;
    }

    public String getProcessado() {
        return processado;
    }

    public void setProcessado(String processado) {
        this.processado = processado;
    }

    public String getFalha() {
        return falha;
    }

    public void setFalha(String falha) {
        this.falha = falha;
    }
}
