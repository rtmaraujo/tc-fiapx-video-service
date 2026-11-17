package com.fiapx.video.infrastructure.outbox;

import com.fiapx.video.infrastructure.persistence.OutboxEventoEntity;
import com.fiapx.video.infrastructure.repository.OutboxEventoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Publica os eventos pendentes da outbox no Kafka. O envio e assincrono em relacao
 * a transacao de negocio, garantindo que nenhuma requisicao seja perdida em picos.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventoRepository outboxEventoRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(OutboxEventoRepository outboxEventoRepository,
                           KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxEventoRepository = outboxEventoRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${fiapx.outbox.intervalo-ms:2000}")
    public void publicarPendentes() {
        List<OutboxEventoEntity> pendentes =
            outboxEventoRepository.findTop50ByStatusOrderByCriadoEmAsc(OutboxEventoEntity.Status.PENDENTE);
        for (OutboxEventoEntity evento : pendentes) {
            publicar(evento);
        }
    }

    void publicar(OutboxEventoEntity evento) {
        try {
            kafkaTemplate.send(evento.getTopico(), evento.getAggregateId().toString(), evento.getPayload())
                .get(10, TimeUnit.SECONDS);
            evento.marcarEnviado();
            outboxEventoRepository.save(evento);
            log.debug("Evento {} ({}) publicado em {}", evento.getId(), evento.getTipo(), evento.getTopico());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            registrarFalha(evento, ex);
        } catch (Exception ex) {
            registrarFalha(evento, ex);
        }
    }

    private void registrarFalha(OutboxEventoEntity evento, Exception ex) {
        evento.registrarFalha(ex.getMessage());
        outboxEventoRepository.save(evento);
        log.warn("Falha ao publicar evento {} (tentativa {}): {}",
            evento.getId(), evento.getTentativas(), ex.getMessage());
    }
}
