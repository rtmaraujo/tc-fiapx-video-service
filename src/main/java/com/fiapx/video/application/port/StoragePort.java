package com.fiapx.video.application.port;

import java.io.InputStream;

/**
 * Abstracao de armazenamento de objetos. Producao: MinIO/S3. Testes: adapter em memoria.
 */
public interface StoragePort {

    void salvar(String chave, InputStream conteudo, long tamanhoBytes, String contentType);

    RecursoArmazenado obter(String chave);

    boolean existe(String chave);

    void remover(String chave);

    record RecursoArmazenado(InputStream conteudo, long tamanhoBytes, String contentType) {
    }
}
