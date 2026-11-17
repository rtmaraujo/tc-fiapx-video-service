package com.fiapx.video.infrastructure.notification;

import com.fiapx.video.application.port.EmailPort;
import com.fiapx.video.config.EmailProperties;
import com.fiapx.video.infrastructure.persistence.NotificacaoEntity;
import com.fiapx.video.infrastructure.repository.NotificacaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Envia as notificacoes de falha registradas pelo video-service, com retentativa.
 */
@Component
public class NotificacaoScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificacaoScheduler.class);

    private final NotificacaoRepository notificacaoRepository;
    private final EmailPort emailPort;
    private final EmailProperties emailProperties;

    public NotificacaoScheduler(NotificacaoRepository notificacaoRepository,
                                EmailPort emailPort,
                                EmailProperties emailProperties) {
        this.notificacaoRepository = notificacaoRepository;
        this.emailPort = emailPort;
        this.emailProperties = emailProperties;
    }

    @Scheduled(fixedDelayString = "${fiapx.notificacao.intervalo-ms:5000}")
    public void enviarPendentes() {
        List<NotificacaoEntity> pendentes =
            notificacaoRepository.findTop20ByStatusOrderByCriadoEmAsc(NotificacaoEntity.Status.PENDENTE);
        for (NotificacaoEntity notificacao : pendentes) {
            enviar(notificacao);
        }
    }

    void enviar(NotificacaoEntity notificacao) {
        try {
            emailPort.enviar(notificacao.getDestinatario(), notificacao.getAssunto(), notificacao.getCorpo());
            notificacao.marcarEnviado();
            log.info("Notificacao {} enviada para {}", notificacao.getId(), notificacao.getDestinatario());
        } catch (Exception ex) {
            notificacao.registrarFalha(ex.getMessage(), emailProperties.getMaximoTentativas());
            log.warn("Falha ao enviar notificacao {} (tentativa {}): {}",
                notificacao.getId(), notificacao.getTentativas(), ex.getMessage());
        }
        notificacaoRepository.save(notificacao);
    }
}
