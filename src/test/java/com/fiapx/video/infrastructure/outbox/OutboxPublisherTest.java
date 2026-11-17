package com.fiapx.video.infrastructure.outbox;

import com.fiapx.video.infrastructure.persistence.OutboxEventoEntity;
import com.fiapx.video.infrastructure.repository.OutboxEventoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private OutboxEventoRepository outboxEventoRepository;
    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @AfterEach
    void limparInterrupcao() {
        Thread.interrupted();
    }

    @Test
    @DisplayName("nao faz nada quando nao ha eventos pendentes")
    void deveIgnorarSemPendentes() {
        when(outboxEventoRepository.findTop50ByStatusOrderByCriadoEmAsc(OutboxEventoEntity.Status.PENDENTE))
            .thenReturn(List.of());

        new OutboxPublisher(outboxEventoRepository, kafkaTemplate).publicarPendentes();

        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("publica evento pendente e marca como ENVIADO")
    void devePublicarEvento() {
        OutboxEventoEntity evento = evento();
        when(outboxEventoRepository.findTop50ByStatusOrderByCriadoEmAsc(OutboxEventoEntity.Status.PENDENTE))
            .thenReturn(List.of(evento));
        when(kafkaTemplate.send("fiapx.video.solicitado", evento.getAggregateId().toString(), "{}"))
            .thenReturn(CompletableFuture.completedFuture(null));

        new OutboxPublisher(outboxEventoRepository, kafkaTemplate).publicarPendentes();

        assertThat(evento.getStatus()).isEqualTo(OutboxEventoEntity.Status.ENVIADO);
        assertThat(evento.getEnviadoEm()).isNotNull();
        verify(outboxEventoRepository).save(evento);
    }

    @Test
    @DisplayName("registra tentativa quando o envio falha")
    void deveRegistrarFalhaDeEnvio() {
        OutboxEventoEntity evento = evento();
        when(outboxEventoRepository.findTop50ByStatusOrderByCriadoEmAsc(OutboxEventoEntity.Status.PENDENTE))
            .thenReturn(List.of(evento));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
            .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("kafka off")));

        new OutboxPublisher(outboxEventoRepository, kafkaTemplate).publicarPendentes();

        assertThat(evento.getStatus()).isEqualTo(OutboxEventoEntity.Status.PENDENTE);
        assertThat(evento.getTentativas()).isEqualTo(1);
        assertThat(evento.getUltimoErro()).contains("kafka off");
        verify(outboxEventoRepository).save(evento);
    }

    @Test
    @DisplayName("interrompe a thread e registra falha em InterruptedException")
    void deveTratarInterrupcao() {
        OutboxEventoEntity evento = evento();
        when(outboxEventoRepository.findTop50ByStatusOrderByCriadoEmAsc(OutboxEventoEntity.Status.PENDENTE))
            .thenReturn(List.of(evento));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
            .thenThrow(new RuntimeException(new InterruptedException("interrompido")));

        new OutboxPublisher(outboxEventoRepository, kafkaTemplate).publicarPendentes();

        assertThat(evento.getTentativas()).isEqualTo(1);
        verify(outboxEventoRepository).save(evento);
    }

    @Test
    @DisplayName("interrompe a thread em InterruptedException direto")
    void deveInterromperThread() {
        OutboxEventoEntity evento = evento();
        when(outboxEventoRepository.findTop50ByStatusOrderByCriadoEmAsc(OutboxEventoEntity.Status.PENDENTE))
            .thenReturn(List.of(evento));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
            .thenReturn(new CompletableFuture<>() {
                @Override
                public org.springframework.kafka.support.SendResult<String, String> get(long timeout, java.util.concurrent.TimeUnit unit)
                    throws InterruptedException {
                    throw new InterruptedException("interrompido");
                }
            });

        new OutboxPublisher(outboxEventoRepository, kafkaTemplate).publicarPendentes();

        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        assertThat(evento.getTentativas()).isEqualTo(1);
    }

    private OutboxEventoEntity evento() {
        return new OutboxEventoEntity(UUID.randomUUID(), "VIDEO_SOLICITADO",
            "fiapx.video.solicitado", "{}");
    }
}
