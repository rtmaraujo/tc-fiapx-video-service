package com.fiapx.video.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Transactional outbox: o evento e gravado na mesma transacao do agregado
 * e publicado de forma assincrona por um scheduler.
 */
@Entity
@Table(name = "outbox_evento")
public class OutboxEventoEntity {

    public enum Status {
        PENDENTE,
        ENVIADO,
        FALHA
    }

    @Id
    private UUID id;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "tipo", nullable = false, length = 80)
    private String tipo;

    @Column(name = "topico", nullable = false, length = 160)
    private String topico;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

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

    protected OutboxEventoEntity() {
    }

    public OutboxEventoEntity(UUID aggregateId, String tipo, String topico, String payload) {
        this.id = UUID.randomUUID();
        this.aggregateId = aggregateId;
        this.tipo = tipo;
        this.topico = topico;
        this.payload = payload;
        this.status = Status.PENDENTE;
        this.tentativas = 0;
        this.criadoEm = LocalDateTime.now();
    }

    public void marcarEnviado() {
        this.status = Status.ENVIADO;
        this.enviadoEm = LocalDateTime.now();
        this.ultimoErro = null;
    }

    public void registrarFalha(String erro) {
        this.tentativas = this.tentativas + 1;
        this.ultimoErro = erro == null ? null : erro.substring(0, Math.min(erro.length(), 500));
        if (this.tentativas >= 10) {
            this.status = Status.FALHA;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getTipo() {
        return tipo;
    }

    public String getTopico() {
        return topico;
    }

    public String getPayload() {
        return payload;
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
