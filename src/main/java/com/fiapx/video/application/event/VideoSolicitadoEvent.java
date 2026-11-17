package com.fiapx.video.application.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record VideoSolicitadoEvent(
    String eventId,
    UUID videoId,
    UUID usuarioId,
    String nomeArquivo,
    String contentType,
    long tamanhoBytes,
    String storageKey,
    LocalDateTime solicitadoEm
) {
}
