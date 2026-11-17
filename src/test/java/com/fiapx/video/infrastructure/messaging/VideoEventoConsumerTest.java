package com.fiapx.video.infrastructure.messaging;

import com.fiapx.video.application.VideoApplicationService;
import com.fiapx.video.config.KafkaTopicsProperties;
import com.fiapx.video.infrastructure.persistence.EventoProcessadoEntity;
import com.fiapx.video.infrastructure.repository.EventoProcessadoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoEventoConsumerTest {

    @Mock
    private VideoApplicationService videoApplicationService;
    @Mock
    private EventoProcessadoRepository eventoProcessadoRepository;

    private VideoEventoConsumer consumer;
    private KafkaTopicsProperties topics;

    @BeforeEach
    void setUp() {
        topics = new KafkaTopicsProperties();
        consumer = new VideoEventoConsumer(videoApplicationService, eventoProcessadoRepository,
            JsonMapper.builder().findAndAddModules().build(), topics);
    }

    @Test
    @DisplayName("aplica evento de conclusao e grava idempotencia")
    void deveConsumirProcessado() {
        UUID videoId = UUID.randomUUID();
        when(eventoProcessadoRepository.existsById("evt-1")).thenReturn(false);

        consumer.consumirProcessado("""
            {"eventId":"evt-1","videoId":"%s","framesZipKey":"frames/a.zip","totalFrames":10,
             "processadoEm":"2026-01-01T10:00:00"}
            """.formatted(videoId));

        verify(videoApplicationService).registrarProcessamentoConcluido(videoId, "frames/a.zip");
        ArgumentCaptor<EventoProcessadoEntity> captor = ArgumentCaptor.forClass(EventoProcessadoEntity.class);
        verify(eventoProcessadoRepository).save(captor.capture());
        assertThat(captor.getValue().getEventId()).isEqualTo("evt-1");
        assertThat(captor.getValue().getTopico()).isEqualTo(topics.getProcessado());
    }

    @Test
    @DisplayName("ignora evento de conclusao duplicado")
    void deveIgnorarProcessadoDuplicado() {
        when(eventoProcessadoRepository.existsById("evt-1")).thenReturn(true);

        consumer.consumirProcessado("""
            {"eventId":"evt-1","videoId":"%s","framesZipKey":"frames/a.zip","totalFrames":10,
             "processadoEm":"2026-01-01T10:00:00"}
            """.formatted(UUID.randomUUID()));

        verifyNoInteractions(videoApplicationService);
        verify(eventoProcessadoRepository, never()).save(any());
    }

    @Test
    @DisplayName("aplica evento de falha e grava idempotencia")
    void deveConsumirFalha() {
        UUID videoId = UUID.randomUUID();
        when(eventoProcessadoRepository.existsById("evt-2")).thenReturn(false);

        consumer.consumirFalha("""
            {"eventId":"evt-2","videoId":"%s","motivo":"ffmpeg falhou",
             "ocorridoEm":"2026-01-01T10:00:00"}
            """.formatted(videoId));

        verify(videoApplicationService).registrarFalha(videoId, "ffmpeg falhou");
        verify(eventoProcessadoRepository).save(any(EventoProcessadoEntity.class));
    }

    @Test
    @DisplayName("ignora evento de falha duplicado")
    void deveIgnorarFalhaDuplicada() {
        when(eventoProcessadoRepository.existsById("evt-2")).thenReturn(true);

        consumer.consumirFalha("""
            {"eventId":"evt-2","videoId":"%s","motivo":"erro","ocorridoEm":"2026-01-01T10:00:00"}
            """.formatted(UUID.randomUUID()));

        verifyNoInteractions(videoApplicationService);
        verify(eventoProcessadoRepository, never()).save(any());
    }

    @Test
    @DisplayName("rejeita evento sem eventId")
    void deveRejeitarEventoSemId() {
        String payload = """
            {"videoId":"%s","framesZipKey":"frames/a.zip","totalFrames":1,
             "processadoEm":"2026-01-01T10:00:00"}
            """.formatted(UUID.randomUUID());

        assertThatThrownBy(() -> consumer.consumirProcessado(payload))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("eventId");
    }

    @Test
    @DisplayName("rejeita evento com eventId nulo")
    void deveRejeitarEventoComIdNulo() {
        String payload = """
            {"eventId":null,"videoId":"%s","framesZipKey":"frames/a.zip","totalFrames":1,
             "processadoEm":"2026-01-01T10:00:00"}
            """.formatted(UUID.randomUUID());

        assertThatThrownBy(() -> consumer.consumirProcessado(payload))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
