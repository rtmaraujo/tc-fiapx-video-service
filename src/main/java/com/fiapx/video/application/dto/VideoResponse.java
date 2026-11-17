package com.fiapx.video.application.dto;

import com.fiapx.video.domain.Video;

import java.time.LocalDateTime;
import java.util.UUID;

public record VideoResponse(
    UUID id,
    String nomeArquivo,
    String contentType,
    long tamanhoBytes,
    String status,
    String erroMensagem,
    boolean downloadDisponivel,
    LocalDateTime criadoEm,
    LocalDateTime atualizadoEm,
    LocalDateTime processadoEm
) {
    public static VideoResponse from(Video video) {
        return new VideoResponse(
            video.getId(),
            video.getNomeArquivo(),
            video.getContentType(),
            video.getTamanhoBytes(),
            video.getStatus().name(),
            video.getErroMensagem(),
            video.estaConcluido(),
            video.getCriadoEm(),
            video.getAtualizadoEm(),
            video.getProcessadoEm()
        );
    }
}
