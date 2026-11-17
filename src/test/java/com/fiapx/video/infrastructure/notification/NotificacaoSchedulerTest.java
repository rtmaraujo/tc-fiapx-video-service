package com.fiapx.video.infrastructure.notification;

import com.fiapx.video.application.port.EmailPort;
import com.fiapx.video.config.EmailProperties;
import com.fiapx.video.infrastructure.persistence.NotificacaoEntity;
import com.fiapx.video.infrastructure.repository.NotificacaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificacaoSchedulerTest {

    @Mock
    private NotificacaoRepository notificacaoRepository;
    @Mock
    private EmailPort emailPort;

    private NotificacaoScheduler scheduler;

    @BeforeEach
    void setUp() {
        EmailProperties properties = new EmailProperties();
        properties.setMaximoTentativas(2);
        scheduler = new NotificacaoScheduler(notificacaoRepository, emailPort, properties);
    }

    @Test
    @DisplayName("envia notificacoes pendentes")
    void deveEnviarPendentes() {
        NotificacaoEntity notificacao = notificacao();
        when(notificacaoRepository.findTop20ByStatusOrderByCriadoEmAsc(NotificacaoEntity.Status.PENDENTE))
            .thenReturn(List.of(notificacao));

        scheduler.enviarPendentes();

        verify(emailPort).enviar("maria@fiapx.com", "Falha", "corpo");
        assertThat(notificacao.getStatus()).isEqualTo(NotificacaoEntity.Status.ENVIADO);
        verify(notificacaoRepository).save(notificacao);
    }

    @Test
    @DisplayName("mantem pendente e incrementa tentativa em falha")
    void deveRegistrarFalhaDeEnvio() {
        NotificacaoEntity notificacao = notificacao();
        when(notificacaoRepository.findTop20ByStatusOrderByCriadoEmAsc(NotificacaoEntity.Status.PENDENTE))
            .thenReturn(List.of(notificacao));
        doThrow(new IllegalStateException("smtp off"))
            .when(emailPort).enviar(anyString(), anyString(), anyString());

        scheduler.enviarPendentes();

        assertThat(notificacao.getStatus()).isEqualTo(NotificacaoEntity.Status.PENDENTE);
        assertThat(notificacao.getTentativas()).isEqualTo(1);
        assertThat(notificacao.getUltimoErro()).contains("smtp off");
    }

    @Test
    @DisplayName("marca como FALHA ao atingir o limite de tentativas")
    void deveMarcarFalhaDefinitiva() {
        NotificacaoEntity notificacao = notificacao();
        notificacao.registrarFalha("anterior", 2);
        when(notificacaoRepository.findTop20ByStatusOrderByCriadoEmAsc(NotificacaoEntity.Status.PENDENTE))
            .thenReturn(List.of(notificacao));
        doThrow(new IllegalStateException("smtp off"))
            .when(emailPort).enviar(anyString(), anyString(), anyString());

        scheduler.enviarPendentes();

        assertThat(notificacao.getStatus()).isEqualTo(NotificacaoEntity.Status.FALHA);
        assertThat(notificacao.getTentativas()).isEqualTo(2);
    }

    @Test
    @DisplayName("nao envia nada quando nao ha pendentes")
    void deveIgnorarSemPendentes() {
        when(notificacaoRepository.findTop20ByStatusOrderByCriadoEmAsc(NotificacaoEntity.Status.PENDENTE))
            .thenReturn(List.of());

        scheduler.enviarPendentes();

        verify(emailPort, org.mockito.Mockito.never()).enviar(anyString(), anyString(), anyString());
        verify(notificacaoRepository, org.mockito.Mockito.never()).save(any());
    }

    private NotificacaoEntity notificacao() {
        return new NotificacaoEntity(UUID.randomUUID(), "maria@fiapx.com", "Falha", "corpo");
    }
}
