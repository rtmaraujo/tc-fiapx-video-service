package com.fiapx.video.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fiapx.video")
public class VideoProperties {

    private long maxSizeBytes = 524_288_000L;
    private int paginaPadrao = 20;
    private int paginaMaxima = 100;

    public long getMaxSizeBytes() {
        return maxSizeBytes;
    }

    public void setMaxSizeBytes(long maxSizeBytes) {
        this.maxSizeBytes = maxSizeBytes;
    }

    public int getPaginaPadrao() {
        return paginaPadrao;
    }

    public void setPaginaPadrao(int paginaPadrao) {
        this.paginaPadrao = paginaPadrao;
    }

    public int getPaginaMaxima() {
        return paginaMaxima;
    }

    public void setPaginaMaxima(int paginaMaxima) {
        this.paginaMaxima = paginaMaxima;
    }
}
