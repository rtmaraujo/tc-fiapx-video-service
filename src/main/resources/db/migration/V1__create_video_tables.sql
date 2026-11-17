CREATE TABLE video (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL,
    email_usuario VARCHAR(180) NOT NULL,
    nome_arquivo VARCHAR(255) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    tamanho_bytes BIGINT NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    status VARCHAR(20) NOT NULL,
    frames_zip_key VARCHAR(512),
    erro_mensagem VARCHAR(1000),
    criado_em TIMESTAMP NOT NULL,
    atualizado_em TIMESTAMP NOT NULL,
    processado_em TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_video_usuario_criado ON video (usuario_id, criado_em DESC);
CREATE INDEX idx_video_status ON video (status);

CREATE TABLE outbox_evento (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    tipo VARCHAR(80) NOT NULL,
    topico VARCHAR(160) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    tentativas INTEGER NOT NULL DEFAULT 0,
    criado_em TIMESTAMP NOT NULL,
    enviado_em TIMESTAMP,
    ultimo_erro VARCHAR(500)
);

CREATE INDEX idx_outbox_status_criado ON outbox_evento (status, criado_em);

CREATE TABLE evento_processado (
    event_id VARCHAR(120) PRIMARY KEY,
    topico VARCHAR(160) NOT NULL,
    processado_em TIMESTAMP NOT NULL
);

CREATE TABLE notificacao (
    id UUID PRIMARY KEY,
    video_id UUID,
    destinatario VARCHAR(180) NOT NULL,
    assunto VARCHAR(200) NOT NULL,
    corpo TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    tentativas INTEGER NOT NULL DEFAULT 0,
    criado_em TIMESTAMP NOT NULL,
    enviado_em TIMESTAMP,
    ultimo_erro VARCHAR(500)
);

CREATE INDEX idx_notificacao_status_criado ON notificacao (status, criado_em);
