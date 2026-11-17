package com.fiapx.video.application.dto;

import java.io.InputStream;

public record ArquivoDownload(
    String nomeArquivo,
    String contentType,
    long tamanhoBytes,
    InputStream conteudo
) {
}
