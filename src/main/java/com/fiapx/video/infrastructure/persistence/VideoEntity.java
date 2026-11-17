package com.fiapx.video.infrastructure.persistence;

import com.fiapx.video.domain.Video;
import com.fiapx.video.domain.VideoStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "video")
public class VideoEntity {

    @Id
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "email_usuario", nullable = false, length = 180)
    private String emailUsuario;

    @Column(name = "nome_arquivo", nullable = false, length = 255)
    private String nomeArquivo;

    @Column(name = "content_type", nullable = false, length = 120)
    private String contentType;

    @Column(name = "tamanho_bytes", nullable = false)
    private long tamanhoBytes;

    @Column(name = "storage_key", nullable = false, length = 512)
    private String storageKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VideoStatus status;

    @Column(name = "frames_zip_key", length = 512)
    private String framesZipKey;

    @Column(name = "erro_mensagem", length = 1000)
    private String erroMensagem;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @Column(name = "processado_em")
    private LocalDateTime processadoEm;

    @Version
    @Column(nullable = false)
    private long version;

    protected VideoEntity() {
    }

    public VideoEntity(Video video) {
        this.id = video.getId();
        this.usuarioId = video.getUsuarioId();
        this.emailUsuario = video.getEmailUsuario();
        this.nomeArquivo = video.getNomeArquivo();
        this.contentType = video.getContentType();
        this.tamanhoBytes = video.getTamanhoBytes();
        this.storageKey = video.getStorageKey();
        this.status = video.getStatus();
        this.framesZipKey = video.getFramesZipKey();
        this.erroMensagem = video.getErroMensagem();
        this.criadoEm = video.getCriadoEm();
        this.atualizadoEm = video.getAtualizadoEm();
        this.processadoEm = video.getProcessadoEm();
    }

    public void aplicar(Video video) {
        this.status = video.getStatus();
        this.framesZipKey = video.getFramesZipKey();
        this.erroMensagem = video.getErroMensagem();
        this.atualizadoEm = video.getAtualizadoEm();
        this.processadoEm = video.getProcessadoEm();
    }

    public Video toDomain() {
        return new Video(id, usuarioId, emailUsuario, nomeArquivo, contentType, tamanhoBytes, storageKey,
            status, framesZipKey, erroMensagem, criadoEm, atualizadoEm, processadoEm);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public String getEmailUsuario() {
        return emailUsuario;
    }

    public String getNomeArquivo() {
        return nomeArquivo;
    }

    public String getContentType() {
        return contentType;
    }

    public long getTamanhoBytes() {
        return tamanhoBytes;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public VideoStatus getStatus() {
        return status;
    }

    public String getFramesZipKey() {
        return framesZipKey;
    }

    public String getErroMensagem() {
        return erroMensagem;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public LocalDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    public LocalDateTime getProcessadoEm() {
        return processadoEm;
    }

    public long getVersion() {
        return version;
    }
}
