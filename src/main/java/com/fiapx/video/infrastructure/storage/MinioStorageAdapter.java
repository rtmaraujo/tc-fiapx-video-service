package com.fiapx.video.infrastructure.storage;

import com.fiapx.video.application.exception.ArmazenamentoException;
import com.fiapx.video.application.port.StoragePort;
import com.fiapx.video.config.StorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

/**
 * Adapter de producao para MinIO (compativel com S3).
 */
public class MinioStorageAdapter implements StoragePort {

    private static final Logger log = LoggerFactory.getLogger(MinioStorageAdapter.class);

    private final MinioClient minioClient;
    private final StorageProperties properties;
    private volatile boolean bucketVerificado;

    public MinioStorageAdapter(MinioClient minioClient, StorageProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    @Override
    public void salvar(String chave, InputStream conteudo, long tamanhoBytes, String contentType) {
        garantirBucket();
        try {
            minioClient.putObject(PutObjectArgs.builder()
                .bucket(properties.getBucket())
                .object(chave)
                .stream(conteudo, tamanhoBytes, -1)
                .contentType(contentType == null ? "application/octet-stream" : contentType)
                .build());
        } catch (Exception ex) {
            throw new ArmazenamentoException("Falha ao salvar objeto " + chave, ex);
        }
    }

    @Override
    public RecursoArmazenado obter(String chave) {
        garantirBucket();
        try {
            StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                .bucket(properties.getBucket())
                .object(chave)
                .build());
            GetObjectResponse resposta = minioClient.getObject(GetObjectArgs.builder()
                .bucket(properties.getBucket())
                .object(chave)
                .build());
            return new RecursoArmazenado(resposta, stat.size(), stat.contentType());
        } catch (Exception ex) {
            throw new ArmazenamentoException("Falha ao obter objeto " + chave, ex);
        }
    }

    @Override
    public boolean existe(String chave) {
        garantirBucket();
        try {
            minioClient.statObject(StatObjectArgs.builder()
                .bucket(properties.getBucket())
                .object(chave)
                .build());
            return true;
        } catch (ErrorResponseException ex) {
            return false;
        } catch (Exception ex) {
            throw new ArmazenamentoException("Falha ao verificar objeto " + chave, ex);
        }
    }

    @Override
    public void remover(String chave) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(properties.getBucket())
                .object(chave)
                .build());
        } catch (Exception ex) {
            throw new ArmazenamentoException("Falha ao remover objeto " + chave, ex);
        }
    }

    private synchronized void garantirBucket() {
        if (bucketVerificado) {
            return;
        }
        try {
            boolean existe = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(properties.getBucket()).build());
            if (!existe) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(properties.getBucket()).build());
                log.info("Bucket {} criado no MinIO", properties.getBucket());
            }
            bucketVerificado = true;
        } catch (Exception ex) {
            throw new ArmazenamentoException("Falha ao preparar bucket " + properties.getBucket(), ex);
        }
    }
}
