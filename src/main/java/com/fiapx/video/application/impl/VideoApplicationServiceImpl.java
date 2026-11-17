package com.fiapx.video.application.impl;

import com.fiapx.video.application.VideoApplicationService;
import com.fiapx.video.application.dto.ArquivoDownload;
import com.fiapx.video.application.dto.PaginaResponse;
import com.fiapx.video.application.dto.VideoResponse;
import com.fiapx.video.application.event.VideoSolicitadoEvent;
import com.fiapx.video.application.exception.ArquivoInvalidoException;
import com.fiapx.video.application.exception.ArmazenamentoException;
import com.fiapx.video.application.exception.VideoNaoEncontradoException;
import com.fiapx.video.application.exception.VideoNaoProcessadoException;
import com.fiapx.video.application.port.StoragePort;
import com.fiapx.video.config.KafkaTopicsProperties;
import com.fiapx.video.config.VideoProperties;
import com.fiapx.video.domain.Video;
import com.fiapx.video.domain.VideoStatus;
import com.fiapx.video.infrastructure.persistence.NotificacaoEntity;
import com.fiapx.video.infrastructure.persistence.OutboxEventoEntity;
import com.fiapx.video.infrastructure.persistence.VideoEntity;
import com.fiapx.video.infrastructure.repository.NotificacaoRepository;
import com.fiapx.video.infrastructure.repository.OutboxEventoRepository;
import com.fiapx.video.infrastructure.repository.VideoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.Locale;
import java.util.UUID;

@Service
public class VideoApplicationServiceImpl implements VideoApplicationService {

    private static final Logger log = LoggerFactory.getLogger(VideoApplicationServiceImpl.class);
    private static final String PREFIXO_VIDEOS = "videos/";

    private final VideoRepository videoRepository;
    private final OutboxEventoRepository outboxEventoRepository;
    private final NotificacaoRepository notificacaoRepository;
    private final StoragePort storagePort;
    private final ObjectMapper objectMapper;
    private final KafkaTopicsProperties topics;
    private final VideoProperties videoProperties;

    public VideoApplicationServiceImpl(VideoRepository videoRepository,
                                       OutboxEventoRepository outboxEventoRepository,
                                       NotificacaoRepository notificacaoRepository,
                                       StoragePort storagePort,
                                       ObjectMapper objectMapper,
                                       KafkaTopicsProperties topics,
                                       VideoProperties videoProperties) {
        this.videoRepository = videoRepository;
        this.outboxEventoRepository = outboxEventoRepository;
        this.notificacaoRepository = notificacaoRepository;
        this.storagePort = storagePort;
        this.objectMapper = objectMapper;
        this.topics = topics;
        this.videoProperties = videoProperties;
    }

    @Override
    @Transactional
    public VideoResponse upload(UUID usuarioId, String emailUsuario, MultipartFile arquivo) {
        validarArquivo(arquivo);

        String nomeArquivo = sanitizarNome(arquivo.getOriginalFilename());
        String contentType = normalizarContentType(arquivo.getContentType(), nomeArquivo);
        Video video = Video.novo(usuarioId, emailUsuario, nomeArquivo, contentType,
            arquivo.getSize(), null);
        String storageKey = montarStorageKey(video, nomeArquivo);
        video.setStorageKey(storageKey);

        try (InputStream conteudo = arquivo.getInputStream()) {
            storagePort.salvar(storageKey, conteudo, arquivo.getSize(), contentType);
        } catch (Exception ex) {
            throw new ArmazenamentoException("Falha ao armazenar o video " + nomeArquivo, ex);
        }

        try {
            VideoEntity salvo = videoRepository.save(new VideoEntity(video));
            registrarEventoSolicitado(salvo.toDomain());
            log.info("Video {} recebido do usuario {} ({} bytes)", salvo.getId(), usuarioId, arquivo.getSize());
            return VideoResponse.from(salvo.toDomain());
        } catch (RuntimeException ex) {
            tentarRemover(storageKey);
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaResponse<VideoResponse> listar(UUID usuarioId, VideoStatus status, int pagina, int tamanho) {
        int paginaSegura = Math.max(pagina, 0);
        int tamanhoSeguro = tamanho <= 0 ? videoProperties.getPaginaPadrao() : tamanho;
        tamanhoSeguro = Math.min(tamanhoSeguro, videoProperties.getPaginaMaxima());

        PageRequest pageRequest = PageRequest.of(paginaSegura, tamanhoSeguro,
            Sort.by(Sort.Direction.DESC, "criadoEm"));
        Page<VideoEntity> page = status == null
            ? videoRepository.findByUsuarioId(usuarioId, pageRequest)
            : videoRepository.findByUsuarioIdAndStatus(usuarioId, status, pageRequest);

        return new PaginaResponse<>(
            page.getContent().stream().map(VideoEntity::toDomain).map(VideoResponse::from).toList(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public VideoResponse buscar(UUID usuarioId, UUID videoId, boolean admin) {
        return VideoResponse.from(buscarVideo(usuarioId, videoId, admin));
    }

    @Override
    @Transactional(readOnly = true)
    public ArquivoDownload baixar(UUID usuarioId, UUID videoId, boolean admin) {
        Video video = buscarVideo(usuarioId, videoId, admin);
        if (!video.estaConcluido()) {
            throw new VideoNaoProcessadoException(
                "Video " + videoId + " ainda nao esta disponivel para download (status " + video.getStatus() + ")");
        }

        StoragePort.RecursoArmazenado recurso = storagePort.obter(video.getFramesZipKey());
        String nomeBase = video.getNomeArquivo().replaceAll("\\.[^.]+$", "");
        return new ArquivoDownload(nomeBase + "_frames.zip", "application/zip",
            recurso.tamanhoBytes(), recurso.conteudo());
    }

    @Override
    @Transactional
    public void registrarProcessamentoConcluido(UUID videoId, String framesZipKey) {
        VideoEntity entity = videoRepository.findById(videoId)
            .orElseThrow(() -> new VideoNaoEncontradoException("Video nao encontrado: " + videoId));

        Video video = entity.toDomain();
        if (video.getStatus() == VideoStatus.CONCLUIDO) {
            log.info("Evento de conclusao ignorado: video {} ja estava CONCLUIDO", videoId);
            return;
        }

        video.concluir(framesZipKey);
        entity.aplicar(video);
        videoRepository.save(entity);
        log.info("Video {} concluido", videoId);
    }

    @Override
    @Transactional
    public void registrarFalha(UUID videoId, String motivo) {
        VideoEntity entity = videoRepository.findById(videoId)
            .orElseThrow(() -> new VideoNaoEncontradoException("Video nao encontrado: " + videoId));

        Video video = entity.toDomain();
        if (video.getStatus() == VideoStatus.ERRO) {
            log.info("Evento de falha ignorado: video {} ja estava em ERRO", videoId);
            return;
        }

        video.falhar(motivo);
        entity.aplicar(video);
        videoRepository.save(entity);
        agendarNotificacaoFalha(video);
        log.warn("Video {} falhou: {}", videoId, motivo);
    }

    private void agendarNotificacaoFalha(Video video) {
        if (video.getEmailUsuario() == null || video.getEmailUsuario().isBlank()) {
            log.warn("Video {} falhou sem e-mail de usuario; notificacao nao sera enviada", video.getId());
            return;
        }
        String assunto = "Falha no processamento do video " + video.getNomeArquivo();
        String corpo = """
            Ola,

            O processamento do video "%s" falhou.

            Motivo: %s
            Identificador: %s

            Voce pode reenviar o arquivo pela API do FIAP X.

            Equipe FIAP X
            """.formatted(video.getNomeArquivo(), motivoOuPadrao(video.getErroMensagem()), video.getId());

        notificacaoRepository.save(new NotificacaoEntity(
            video.getId(), video.getEmailUsuario(), assunto, corpo));
    }

    private void registrarEventoSolicitado(Video video) {
        VideoSolicitadoEvent evento = new VideoSolicitadoEvent(
            UUID.randomUUID().toString(),
            video.getId(),
            video.getUsuarioId(),
            video.getNomeArquivo(),
            video.getContentType(),
            video.getTamanhoBytes(),
            video.getStorageKey(),
            video.getCriadoEm()
        );
        String payload = objectMapper.writeValueAsString(evento);
        outboxEventoRepository.save(new OutboxEventoEntity(
            video.getId(), "VIDEO_SOLICITADO", topics.getSolicitado(), payload));
    }

    private Video buscarVideo(UUID usuarioId, UUID videoId, boolean admin) {
        VideoEntity entity = admin
            ? videoRepository.findById(videoId)
                .orElseThrow(() -> new VideoNaoEncontradoException("Video nao encontrado: " + videoId))
            : videoRepository.findByIdAndUsuarioId(videoId, usuarioId)
                .orElseThrow(() -> new VideoNaoEncontradoException("Video nao encontrado: " + videoId));
        return entity.toDomain();
    }

    private void validarArquivo(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new ArquivoInvalidoException("Arquivo de video e obrigatorio");
        }
        if (arquivo.getSize() > videoProperties.getMaxSizeBytes()) {
            throw new ArquivoInvalidoException("Arquivo excede o limite de "
                + videoProperties.getMaxSizeBytes() + " bytes");
        }
        String nome = sanitizarNome(arquivo.getOriginalFilename());
        String contentType = normalizarContentType(arquivo.getContentType(), nome);
        if (!contentType.startsWith("video/") && !extensaoDeVideo(nome)) {
            throw new ArquivoInvalidoException("Formato de arquivo nao suportado: " + nome);
        }
    }

    private boolean extensaoDeVideo(String nome) {
        String minusculo = nome.toLowerCase(Locale.ROOT);
        return minusculo.endsWith(".mp4") || minusculo.endsWith(".mov") || minusculo.endsWith(".avi")
            || minusculo.endsWith(".mkv") || minusculo.endsWith(".webm") || minusculo.endsWith(".mpeg")
            || minusculo.endsWith(".mpg") || minusculo.endsWith(".m4v");
    }

    private String sanitizarNome(String nomeOriginal) {
        String nome = nomeOriginal == null ? "" : nomeOriginal.replace('\\', '/');
        int barra = nome.lastIndexOf('/');
        if (barra >= 0) {
            nome = nome.substring(barra + 1);
        }
        nome = nome.replaceAll("[\\p{Cntrl}]", "").trim();
        if (nome.isBlank()) {
            return "video";
        }
        return nome.length() > 255 ? nome.substring(nome.length() - 255) : nome;
    }

    private String normalizarContentType(String contentType, String nomeArquivo) {
        if (contentType != null && !contentType.isBlank() && !"application/octet-stream".equals(contentType)) {
            return contentType;
        }
        String minusculo = nomeArquivo.toLowerCase(Locale.ROOT);
        if (minusculo.endsWith(".mp4")) {
            return "video/mp4";
        }
        if (minusculo.endsWith(".mov")) {
            return "video/quicktime";
        }
        if (minusculo.endsWith(".avi")) {
            return "video/x-msvideo";
        }
        if (minusculo.endsWith(".mkv")) {
            return "video/x-matroska";
        }
        if (minusculo.endsWith(".webm")) {
            return "video/webm";
        }
        return "application/octet-stream";
    }

    private String montarStorageKey(Video video, String nomeArquivo) {
        String extensao = "bin";
        int ponto = nomeArquivo.lastIndexOf('.');
        if (ponto >= 0 && ponto < nomeArquivo.length() - 1) {
            String candidata = nomeArquivo.substring(ponto + 1).toLowerCase(Locale.ROOT);
            if (candidata.matches("[a-z0-9]{1,8}")) {
                extensao = candidata;
            }
        }
        return PREFIXO_VIDEOS + video.getUsuarioId() + "/" + video.getId() + "/original." + extensao;
    }

    private void tentarRemover(String storageKey) {
        try {
            storagePort.remover(storageKey);
        } catch (Exception ex) {
            log.warn("Nao foi possivel remover o objeto {} apos falha de persistencia", storageKey, ex);
        }
    }

    private String motivoOuPadrao(String motivo) {
        return motivo == null || motivo.isBlank() ? "erro nao informado" : motivo;
    }
}
