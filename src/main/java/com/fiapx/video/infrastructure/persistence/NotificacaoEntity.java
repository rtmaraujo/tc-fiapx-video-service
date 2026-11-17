package com.fiapx.video.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notificacao")
public class NotificacaoEntity {

    public enum Status {
        PENDENTE,
        ENVIADO,
        FALHA
    }

    @Id
    private UUID id;

    @Column(name = "video_id")
    private UUID videoId;

    @Column(nullable = false, length = 180)
    private String destinatario;

    @Column(nullable = false, length = 200)
    private String assunto;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String corpo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(nullable = false)
    private int tentativas;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "enviado_em")
    private LocalDateTime enviadoEm;

    @Column(name = "ultimo_erro", length = 500)
    private String ultimoErro;

    protected NotificacaoEntity() {
    }

    public NotificacaoEntity(UUID videoId, String destinatario, String assunto, String corpo) {
        this.id = UUID.randomUUID();
        this.videoId = videoId;
        this.destinatario = destinatario;
        this.assunto = assunto;
        this.corpo = corpo;
        this.status = Status.PENDENTE;
        this.tentativas = 0;
        this.criadoEm = LocalDateTime.now();
    }

    public void marcarEnviado() {
        this.status = Status.ENVIADO;
        this.enviadoEm = LocalDateTime.now();
        this.ultimoErro = null;
    }

    public void registrarFalha(String erro, int maximoTentativas) {
        this.tentativas = this.tentativas + 1;
        this.ultimoErro = erro == null ? null : erro.substring(0, Math.min(erro.length(), 500));
        if (this.tentativas >= maximoTentativas) {
            this.status = Status.FALHA;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getVideoId() {
        return videoId;
    }

    public String getDestinatario() {
        return destinatario;
    }

    public String getAssunto() {
        return assunto;
    }

    public String getCorpo() {
        return corpo;
    }

    public Status getStatus() {
        return status;
    }

    public int getTentativas() {
        return tentativas;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public LocalDateTime getEnviadoEm() {
        return enviadoEm;
    }

    public String getUltimoErro() {
        return ultimoErro;
    }
}
