package com.fiapx.video.infrastructure.storage;

import com.fiapx.video.application.exception.ArmazenamentoException;
import com.fiapx.video.application.port.StoragePort;
import com.fiapx.video.config.StorageProperties;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MinioStorageAdapterTest {

    @Mock
    private MinioClient minioClient;

    private MinioStorageAdapter adapter;

    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties();
        properties.setBucket("teste");
        adapter = new MinioStorageAdapter(minioClient, properties);
    }

    @Test
    @DisplayName("cria o bucket quando ele nao existe e salva o objeto")
    void deveCriarBucketESalvar() throws Exception {
        when(minioClient.bucketExists(any())).thenReturn(false);

        adapter.salvar("videos/a.mp4", new ByteArrayInputStream("x".getBytes()), 1L, "video/mp4");

        verify(minioClient).makeBucket(any());
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    @DisplayName("nao recria bucket existente e reaproveita o cache")
    void deveReaproveitarBucketExistente() throws Exception {
        when(minioClient.bucketExists(any())).thenReturn(true);

        adapter.salvar("a", new ByteArrayInputStream(new byte[0]), 0L, null);
        adapter.salvar("b", new ByteArrayInputStream(new byte[0]), 0L, "video/mp4");

        verify(minioClient, org.mockito.Mockito.times(1)).bucketExists(any());
        verify(minioClient, never()).makeBucket(any());
    }

    @Test
    @DisplayName("encapsula falha ao salvar")
    void deveFalharAoSalvar() throws Exception {
        when(minioClient.bucketExists(any())).thenReturn(true);
        doThrow(new RuntimeException("minio off")).when(minioClient).putObject(any(PutObjectArgs.class));

        assertThatThrownBy(() -> adapter.salvar("a", new ByteArrayInputStream(new byte[0]), 0L, "video/mp4"))
            .isInstanceOf(ArmazenamentoException.class)
            .hasMessageContaining("salvar objeto a");
    }

    @Test
    @DisplayName("obtem objeto com metadados")
    void deveObterObjeto() throws Exception {
        when(minioClient.bucketExists(any())).thenReturn(true);
        StatObjectResponse stat = mock(StatObjectResponse.class);
        when(stat.size()).thenReturn(10L);
        when(stat.contentType()).thenReturn("application/zip");
        when(minioClient.statObject(any())).thenReturn(stat);
        when(minioClient.getObject(any())).thenReturn(mock(GetObjectResponse.class));

        StoragePort.RecursoArmazenado recurso = adapter.obter("frames/a.zip");

        assertThat(recurso.tamanhoBytes()).isEqualTo(10L);
        assertThat(recurso.contentType()).isEqualTo("application/zip");
        assertThat(recurso.conteudo()).isNotNull();
    }

    @Test
    @DisplayName("encapsula falha ao obter objeto")
    void deveFalharAoObter() throws Exception {
        when(minioClient.bucketExists(any())).thenReturn(true);
        when(minioClient.statObject(any())).thenThrow(new RuntimeException("sumiu"));

        assertThatThrownBy(() -> adapter.obter("a"))
            .isInstanceOf(ArmazenamentoException.class);
    }

    @Test
    @DisplayName("retorna true quando o objeto existe")
    void deveIndicarExistencia() throws Exception {
        when(minioClient.bucketExists(any())).thenReturn(true);
        when(minioClient.statObject(any())).thenReturn(mock(StatObjectResponse.class));

        assertThat(adapter.existe("a")).isTrue();
    }

    @Test
    @DisplayName("retorna false para objeto inexistente")
    void deveIndicarInexistencia() throws Exception {
        when(minioClient.bucketExists(any())).thenReturn(true);
        when(minioClient.statObject(any())).thenThrow(mock(ErrorResponseException.class));

        assertThat(adapter.existe("a")).isFalse();
    }

    @Test
    @DisplayName("encapsula erro inesperado ao verificar existencia")
    void deveFalharAoVerificarExistencia() throws Exception {
        when(minioClient.bucketExists(any())).thenReturn(true);
        when(minioClient.statObject(any())).thenThrow(new IllegalStateException("off"));

        assertThatThrownBy(() -> adapter.existe("a"))
            .isInstanceOf(ArmazenamentoException.class);
    }

    @Test
    @DisplayName("remove objeto")
    void deveRemover() throws Exception {
        adapter.remover("a");

        verify(minioClient).removeObject(any());
    }

    @Test
    @DisplayName("encapsula falha ao remover")
    void deveFalharAoRemover() throws Exception {
        doThrow(new RuntimeException("off")).when(minioClient).removeObject(any());

        assertThatThrownBy(() -> adapter.remover("a"))
            .isInstanceOf(ArmazenamentoException.class);
    }

    @Test
    @DisplayName("encapsula falha ao preparar bucket")
    void deveFalharAoPrepararBucket() throws Exception {
        when(minioClient.bucketExists(any())).thenThrow(new RuntimeException("off"));

        assertThatThrownBy(() -> adapter.salvar("a", new ByteArrayInputStream(new byte[0]), 0L, "x"))
            .isInstanceOf(ArmazenamentoException.class)
            .hasMessageContaining("preparar bucket");
    }
}
