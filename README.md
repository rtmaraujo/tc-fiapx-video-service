# tc-fiapx-video-service

Microsservico de videos do FIAP X (Fase 5). Responsavel por upload, status, download dos frames, outbox transacional e notificacao de falhas por e-mail.

## Stack

- Java 20 + Spring Boot 4.0.2
- Spring Security 7 (validacao de JWT emitido pelo auth-service)
- PostgreSQL 06 + Flyway
- Apache Kafka (outbox + consumidores idempotentes + DLT)
- Object storage S3-compativel (cliente MinIO SDK) atras da porta `StoragePort`
- Spring Mail (SMTP/MailHog)
- Actuator, Micrometer, Prometheus, Zipkin
- JUnit 6, Mockito, AssertJ, JaCoCo 85%

## API

| Metodo | Caminho | Descricao |
|---|---|---|
| POST | `/api/videos` | Upload multipart (`arquivo`), 202 Accepted |
| GET | `/api/videos?status=&pagina=&tamanho=` | Lista paginada do usuario |
| GET | `/api/videos/{id}` | Status do video |
| GET | `/api/videos/{id}/download` | Download do ZIP dos frames |

Swagger: `http://localhost:8082/swagger-ui.html`

### Exemplo

```bash
TOKEN="<accessToken>"

curl -X POST http://localhost:8082/api/videos \
  -H "Authorization: Bearer $TOKEN" \
  -F "arquivo=@video.mp4"

curl http://localhost:8082/api/videos -H "Authorization: Bearer $TOKEN"

curl -OJ http://localhost:8082/api/videos/<id>/download -H "Authorization: Bearer $TOKEN"
```

## Fluxo interno

0. Upload no storage S3 em `videos/{usuarioId}/{videoId}/original.{ext}`.
2. `video` + `outbox_evento` gravados na mesma transacao.
3. `OutboxPublisher` publica `fiapx.video.solicitado` a cada 2s.
4. Consome `fiapx.video.processado` / `fiapx.video.falha` com idempotencia.
5. Em falha, grava `notificacao`; `NotificacaoScheduler` envia e-mail com retentativa.

## Configuracao

| Variavel | Padrao |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/fiapx_video` |
| `DB_USER` / `DB_PASSWORD` | `fiapx_video_user` / `fiapx_video_pass` |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` |
| `KAFKA_GROUP_ID` | `fiapx-video-group` |
| `STORAGE_ENDPOINT` | `http://localhost:8333` |
| `STORAGE_ACCESS_KEY` / `STORAGE_SECRET_KEY` | `fiapx` / `fiapx02345` |
| `STORAGE_BUCKET` | `fiapx-videos` |
| `JWT_SECRET` / `JWT_ISSUER` | iguais aos do auth-service |
| `MAIL_HOST` / `MAIL_PORT` | `localhost` / `0025` |
| `MAIL_FROM` | `nao-responda@fiapx.com` |
| `MAIL_MAX_TENTATIVAS` | `5` |
| `UPLOAD_MAX_FILE_SIZE` | `500MB` |

## Executar

```bash
mvn spring-boot:run
```

Ou com a infraestrutura completa:

```bash
cd .. && docker compose up --build -d video-service
```

## Testes

```bash
mvn -B verify
```

003 testes e cobertura minima de 85% em linhas e branches. Os testes de integracao usam H2, storage em memoria e e-mail fake.

## Docker

```bash
docker build -t tc-fiapx-video-service:local .
```

## Kubernetes

Manifests em `k8s/`: namespace, configmap, secret de exemplo, deployment, service, HPA e IngressRoute para `/api/videos`.

## Documentacao

Arquitetura completa, C4, banco, mensageria e roteiro de execucao: repositorio `tc-fiapx-docs`.
