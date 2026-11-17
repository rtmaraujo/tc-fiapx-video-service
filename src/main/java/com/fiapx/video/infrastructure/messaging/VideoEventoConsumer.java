package com.fiapx.video.infrastructure.messaging;

import com.fiapx.video.application.VideoApplicationService;
import com.fiapx.video.application.event.VideoFalhaEvent;
import com.fiapx.video.application.event.VideoProcessadoEvent;
import com.fiapx.video.config.KafkaTopicsProperties;
import com.fiapx.video.infrastructure.persistence.EventoProcessadoEntity;
import com.fiapx.video.infrastructure.repository.EventoProcessadoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Consome os resultados do processing-service de forma idempotente.
 */
@Component
public class VideoEventoConsumer {

    private static final Logger log = LoggerFactory.getLogger(VideoEventoConsumer.class);

    private final VideoApplicationService videoApplicationService;
    private final EventoProcessadoRepository eventoProcessadoRepository;
    private final ObjectMapper objectMapper;
    private final KafkaTopicsProperties topics;

    public VideoEventoConsumer(VideoApplicationService videoApplicationService,
                               EventoProcessadoRepository eventoProcessadoRepository,
                               ObjectMapper objectMapper,
                               KafkaTopicsProperties topics) {
        this.videoApplicationService = videoApplicationService;
        this.eventoProcessadoRepository = eventoProcessadoRepository;
        this.objectMapper = objectMapper;
        this.topics = topics;
    }

    @KafkaListener(topics = "${fiapx.kafka.topics.processado}",
        groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void consumirProcessado(String payload) {
        VideoProcessadoEvent evento = objectMapper.readValue(payload, VideoProcessadoEvent.class);
        if (jaProcessado(evento.eventId(), topics.getProcessado())) {
            return;
        }
        videoApplicationService.registrarProcessamentoConcluido(evento.videoId(), evento.framesZipKey());
        eventoProcessadoRepository.save(new EventoProcessadoEntity(evento.eventId(), topics.getProcessado()));
        log.info("Evento de conclusao {} aplicado ao video {}", evento.eventId(), evento.videoId());
    }

    @KafkaListener(topics = "${fiapx.kafka.topics.falha}",
        groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void consumirFalha(String payload) {
        VideoFalhaEvent evento = objectMapper.readValue(payload, VideoFalhaEvent.class);
        if (jaProcessado(evento.eventId(), topics.getFalha())) {
            return;
        }
        videoApplicationService.registrarFalha(evento.videoId(), evento.motivo());
        eventoProcessadoRepository.save(new EventoProcessadoEntity(evento.eventId(), topics.getFalha()));
        log.warn("Evento de falha {} aplicado ao video {}: {}", evento.eventId(), evento.videoId(), evento.motivo());
    }

    private boolean jaProcessado(String eventId, String topico) {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("Evento sem eventId no topico " + topico);
        }
        return eventoProcessadoRepository.existsById(eventId);
    }
}
