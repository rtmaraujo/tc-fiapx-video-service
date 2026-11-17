package com.fiapx.video.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Controle de idempotencia: cada eventId recebido e registrado uma unica vez.
 */
@Entity
@Table(name = "evento_processado")
public class EventoProcessadoEntity {

    @Id
    @Column(name = "event_id", nullable = false, length = 120)
    private String eventId;

    @Column(name = "topico", nullable = false, length = 160)
    private String topico;

    @Column(name = "processado_em", nullable = false)
    private LocalDateTime processadoEm;

    protected EventoProcessadoEntity() {
    }

    public EventoProcessadoEntity(String eventId, String topico) {
        this.eventId = eventId;
        this.topico = topico;
        this.processadoEm = LocalDateTime.now();
    }

    public String getEventId() {
        return eventId;
    }

    public String getTopico() {
        return topico;
    }

    public LocalDateTime getProcessadoEm() {
        return processadoEm;
    }
}
