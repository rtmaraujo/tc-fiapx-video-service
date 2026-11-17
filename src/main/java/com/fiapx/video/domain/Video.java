package com.fiapx.video.domain;

import java.time.LocalDateTime;
import java.util.UUID;

public class Video {

    private UUID id;
    private UUID usuarioId;
    private String emailUsuario;
    private String nomeArquivo;
    private String contentType;
    private long tamanhoBytes;
    private String storageKey;
    private VideoStatus status;
    private String framesZipKey;
    private String erroMensagem;
    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;
    private LocalDateTime processadoEm;

    public Video() {
    }

    public Video(UUID id, UUID usuarioId, String emailUsuario, String nomeArquivo, String contentType,
                 long tamanhoBytes, String storageKey, VideoStatus status, String framesZipKey,
                 String erroMensagem, LocalDateTime criadoEm, LocalDateTime atualizadoEm,
                 LocalDateTime processadoEm) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.emailUsuario = emailUsuario;
        this.nomeArquivo = nomeArquivo;
        this.contentType = contentType;
        this.tamanhoBytes = tamanhoBytes;
        this.storageKey = storageKey;
        this.status = status;
        this.framesZipKey = framesZipKey;
        this.erroMensagem = erroMensagem;
        this.criadoEm = criadoEm;
        this.atualizadoEm = atualizadoEm;
        this.processadoEm = processadoEm;
    }

    public static Video novo(UUID usuarioId, String emailUsuario, String nomeArquivo, String contentType,
                             long tamanhoBytes, String storageKey) {
        LocalDateTime agora = LocalDateTime.now();
        return new Video(UUID.randomUUID(), usuarioId, emailUsuario, nomeArquivo, contentType, tamanhoBytes,
            storageKey, VideoStatus.PENDENTE, null, null, agora, agora, null);
    }

    public void marcarProcessando() {
        if (status == VideoStatus.CONCLUIDO) {
            return;
        }
        this.status = VideoStatus.PROCESSANDO;
        this.erroMensagem = null;
        this.atualizadoEm = LocalDateTime.now();
    }

    public void concluir(String framesZipKey) {
        this.status = VideoStatus.CONCLUIDO;
        this.framesZipKey = framesZipKey;
        this.erroMensagem = null;
        this.processadoEm = LocalDateTime.now();
        this.atualizadoEm = this.processadoEm;
    }

    public void falhar(String motivo) {
        this.status = VideoStatus.ERRO;
        this.erroMensagem = motivo;
        this.processadoEm = LocalDateTime.now();
        this.atualizadoEm = this.processadoEm;
    }

    public boolean pertenceA(UUID usuarioId) {
        return this.usuarioId != null && this.usuarioId.equals(usuarioId);
    }

    public boolean estaConcluido() {
        return status == VideoStatus.CONCLUIDO && framesZipKey != null && !framesZipKey.isBlank();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(UUID usuarioId) {
        this.usuarioId = usuarioId;
    }

    public String getEmailUsuario() {
        return emailUsuario;
    }

    public void setEmailUsuario(String emailUsuario) {
        this.emailUsuario = emailUsuario;
    }

    public String getNomeArquivo() {
        return nomeArquivo;
    }

    public void setNomeArquivo(String nomeArquivo) {
        this.nomeArquivo = nomeArquivo;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getTamanhoBytes() {
        return tamanhoBytes;
    }

    public void setTamanhoBytes(long tamanhoBytes) {
        this.tamanhoBytes = tamanhoBytes;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public VideoStatus getStatus() {
        return status;
    }

    public void setStatus(VideoStatus status) {
        this.status = status;
    }

    public String getFramesZipKey() {
        return framesZipKey;
    }

    public void setFramesZipKey(String framesZipKey) {
        this.framesZipKey = framesZipKey;
    }

    public String getErroMensagem() {
        return erroMensagem;
    }

    public void setErroMensagem(String erroMensagem) {
        this.erroMensagem = erroMensagem;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public void setCriadoEm(LocalDateTime criadoEm) {
        this.criadoEm = criadoEm;
    }

    public LocalDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    public void setAtualizadoEm(LocalDateTime atualizadoEm) {
        this.atualizadoEm = atualizadoEm;
    }

    public LocalDateTime getProcessadoEm() {
        return processadoEm;
    }

    public void setProcessadoEm(LocalDateTime processadoEm) {
        this.processadoEm = processadoEm;
    }
}
