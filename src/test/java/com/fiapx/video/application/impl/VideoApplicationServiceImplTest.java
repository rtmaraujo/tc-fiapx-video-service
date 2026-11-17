package com.fiapx.video.application.impl;

import com.fiapx.video.application.dto.ArquivoDownload;
import com.fiapx.video.application.dto.PaginaResponse;
import com.fiapx.video.application.dto.VideoResponse;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoApplicationServiceImplTest {

    @Mock
    private VideoRepository videoRepository;
    @Mock
    private OutboxEventoRepository outboxEventoRepository;
    @Mock
    private NotificacaoRepository notificacaoRepository;
    @Mock
    private StoragePort storagePort;
    @Captor
    private ArgumentCaptor<OutboxEventoEntity> outboxCaptor;
    @Captor
    private ArgumentCaptor<NotificacaoEntity> notificacaoCaptor;

    private VideoApplicationServiceImpl service;
    private UUID usuarioId;

    @BeforeEach
    void setUp() {
        KafkaTopicsProperties topics = new KafkaTopicsProperties();
        VideoProperties videoProperties = new VideoProperties();
        videoProperties.setMaxSizeBytes(1_000L);
        service = new VideoApplicationServiceImpl(videoRepository, outboxEventoRepository,
            notificacaoRepository, storagePort,
            JsonMapper.builder().findAndAddModules().build(), topics, videoProperties);
        usuarioId = UUID.randomUUID();
    }

    @Test
    @DisplayName("faz upload, salva no storage e grava evento na outbox")
    void deveFazerUpload() {
        MockMultipartFile arquivo = new MockMultipartFile(
            "arquivo", "C:\\tmp\\Aula 1.MP4", "video/mp4", "conteudo".getBytes(StandardCharsets.UTF_8));
        when(videoRepository.save(any(VideoEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        VideoResponse response = service.upload(usuarioId, "maria@fiapx.com", arquivo);

        assertThat(response.status()).isEqualTo("PENDENTE");
        assertThat(response.nomeArquivo()).isEqualTo("Aula 1.MP4");
        assertThat(response.downloadDisponivel()).isFalse();
        verify(storagePort).salvar(startsWith("videos/" + usuarioId + "/"), any(InputStream.class),
            eq(8L), eq("video/mp4"));
        verify(outboxEventoRepository).save(outboxCaptor.capture());
        OutboxEventoEntity evento = outboxCaptor.getValue();
        assertThat(evento.getTipo()).isEqualTo("VIDEO_SOLICITADO");
        assertThat(evento.getTopico()).isEqualTo("fiapx.video.solicitado");
        assertThat(evento.getPayload()).contains(response.id().toString(), "Aula 1.MP4", "videos/");
    }

    @Test
    @DisplayName("deduz content type pelo nome quando vier octet-stream")
    void deveDeduzirContentType() {
        MockMultipartFile arquivo = new MockMultipartFile(
            "arquivo", "clipe.mov", "application/octet-stream", "x".getBytes(StandardCharsets.UTF_8));
        when(videoRepository.save(any(VideoEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        VideoResponse response = service.upload(usuarioId, "maria@fiapx.com", arquivo);

        assertThat(response.contentType()).isEqualTo("video/quicktime");
        verify(storagePort).salvar(startsWith("videos/"), any(InputStream.class), eq(1L), eq("video/quicktime"));
    }

    @Test
    @DisplayName("deduz content type para avi, mkv, webm e desconhecido")
    void deveDeduzirOutrosContentTypes() {
        assertThat(uploadComNome("a.avi", null).contentType()).isEqualTo("video/x-msvideo");
        assertThat(uploadComNome("a.mkv", "application/octet-stream").contentType()).isEqualTo("video/x-matroska");
        assertThat(uploadComNome("a.webm", "").contentType()).isEqualTo("video/webm");
        assertThat(uploadComNome("a.mpeg", null).contentType()).isEqualTo("application/octet-stream");
    }

    @Test
    @DisplayName("usa nome padrao quando arquivo nao tem nome")
    void deveUsarNomePadrao() {
        MockMultipartFile arquivo = new MockMultipartFile("arquivo", null, "video/mp4", "x".getBytes());
        when(videoRepository.save(any(VideoEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        VideoResponse response = service.upload(usuarioId, "maria@fiapx.com", arquivo);

        assertThat(response.nomeArquivo()).isEqualTo("video");
    }

    @Test
    @DisplayName("recusa arquivo nulo")
    void deveRecusarArquivoNulo() {
        assertThatThrownBy(() -> service.upload(usuarioId, "maria@fiapx.com", null))
            .isInstanceOf(ArquivoInvalidoException.class)
            .hasMessageContaining("obrigatorio");
    }

    @Test
    @DisplayName("recusa arquivo vazio")
    void deveRecusarArquivoVazio() {
        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "a.mp4", "video/mp4", new byte[0]);

        assertThatThrownBy(() -> service.upload(usuarioId, "maria@fiapx.com", arquivo))
            .isInstanceOf(ArquivoInvalidoException.class);
    }

    @Test
    @DisplayName("recusa arquivo acima do limite")
    void deveRecusarArquivoGrande() {
        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "a.mp4", "video/mp4", new byte[2_000]);

        assertThatThrownBy(() -> service.upload(usuarioId, "maria@fiapx.com", arquivo))
            .isInstanceOf(ArquivoInvalidoException.class)
            .hasMessageContaining("limite");
    }

    @Test
    @DisplayName("recusa extensao nao suportada")
    void deveRecusarExtensaoInvalida() {
        MockMultipartFile arquivo = new MockMultipartFile(
            "arquivo", "nota.txt", "text/plain", "x".getBytes());

        assertThatThrownBy(() -> service.upload(usuarioId, "maria@fiapx.com", arquivo))
            .isInstanceOf(ArquivoInvalidoException.class)
            .hasMessageContaining("nao suportado");
    }

    @Test
    @DisplayName("aceita extensao de video mesmo com content type desconhecido")
    void deveAceitarExtensaoDeVideo() {
        MockMultipartFile arquivo = new MockMultipartFile(
            "arquivo", "a.M4V", "application/octet-stream", "x".getBytes());
        when(videoRepository.save(any(VideoEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.upload(usuarioId, "maria@fiapx.com", arquivo).status()).isEqualTo("PENDENTE");
    }

    @Test
    @DisplayName("encapsula falha do storage")
    void deveEncapsularFalhaDeStorage() {
        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "a.mp4", "video/mp4", "x".getBytes());
        doThrow(new IllegalStateException("minio off")).when(storagePort)
            .salvar(anyString(), any(InputStream.class), anyLong(), anyString());

        assertThatThrownBy(() -> service.upload(usuarioId, "maria@fiapx.com", arquivo))
            .isInstanceOf(ArmazenamentoException.class)
            .hasMessageContaining("Falha ao armazenar");
        verifyNoInteractions(videoRepository);
    }

    @Test
    @DisplayName("remove objeto do storage quando a persistencia falha")
    void deveRemoverObjetoQuandoPersistenciaFalha() {
        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "a.mp4", "video/mp4", "x".getBytes());
        when(videoRepository.save(any(VideoEntity.class))).thenThrow(new IllegalStateException("db off"));

        assertThatThrownBy(() -> service.upload(usuarioId, "maria@fiapx.com", arquivo))
            .isInstanceOf(IllegalStateException.class);

        verify(storagePort).remover(startsWith("videos/"));
    }

    @Test
    @DisplayName("lista videos sem filtro de status")
    void deveListarSemFiltro() {
        VideoEntity entity = new VideoEntity(Video.novo(usuarioId, "maria@fiapx.com", "a.mp4",
            "video/mp4", 10L, "k"));
        when(videoRepository.findByUsuarioId(eq(usuarioId), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of(entity), PageRequest.of(0, 20), 1));

        PaginaResponse<VideoResponse> pagina = service.listar(usuarioId, null, 0, 20);

        assertThat(pagina.conteudo()).hasSize(1);
        assertThat(pagina.totalElementos()).isEqualTo(1);
        assertThat(pagina.totalPaginas()).isEqualTo(1);
    }

    @Test
    @DisplayName("lista videos filtrando por status")
    void deveListarComFiltro() {
        when(videoRepository.findByUsuarioIdAndStatus(eq(usuarioId), eq(VideoStatus.ERRO),
            any(PageRequest.class))).thenReturn(new PageImpl<>(List.of()));

        PaginaResponse<VideoResponse> pagina = service.listar(usuarioId, VideoStatus.ERRO, -5, 500);

        assertThat(pagina.conteudo()).isEmpty();
        assertThat(pagina.pagina()).isZero();
        verify(videoRepository).findByUsuarioIdAndStatus(eq(usuarioId), eq(VideoStatus.ERRO),
            org.mockito.ArgumentMatchers.argThat(pr -> pr.getPageSize() == 100 && pr.getPageNumber() == 0));
    }

    @Test
    @DisplayName("usa tamanho padrao quando pagina invalida")
    void deveUsarTamanhoPadrao() {
        when(videoRepository.findByUsuarioId(eq(usuarioId), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of()));

        service.listar(usuarioId, null, 0, 0);

        verify(videoRepository).findByUsuarioId(eq(usuarioId),
            org.mockito.ArgumentMatchers.argThat(pr -> pr.getPageSize() == 20));
    }

    @Test
    @DisplayName("busca video do proprio usuario")
    void deveBuscarVideoDoUsuario() {
        Video video = Video.novo(usuarioId, "maria@fiapx.com", "a.mp4", "video/mp4", 10L, "k");
        when(videoRepository.findByIdAndUsuarioId(video.getId(), usuarioId))
            .thenReturn(Optional.of(new VideoEntity(video)));

        assertThat(service.buscar(usuarioId, video.getId(), false).id()).isEqualTo(video.getId());
    }

    @Test
    @DisplayName("admin busca video de qualquer usuario")
    void adminDeveBuscarQualquerVideo() {
        Video video = Video.novo(UUID.randomUUID(), "maria@fiapx.com", "a.mp4", "video/mp4", 10L, "k");
        when(videoRepository.findById(video.getId())).thenReturn(Optional.of(new VideoEntity(video)));

        assertThat(service.buscar(usuarioId, video.getId(), true).id()).isEqualTo(video.getId());
    }

    @Test
    @DisplayName("retorna 404 quando video nao existe")
    void deveFalharBusca() {
        UUID id = UUID.randomUUID();
        when(videoRepository.findByIdAndUsuarioId(id, usuarioId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscar(usuarioId, id, false))
            .isInstanceOf(VideoNaoEncontradoException.class);
    }

    @Test
    @DisplayName("admin recebe 404 quando video nao existe")
    void adminDeveReceber404() {
        UUID id = UUID.randomUUID();
        when(videoRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscar(usuarioId, id, true))
            .isInstanceOf(VideoNaoEncontradoException.class);
    }

    @Test
    @DisplayName("baixa ZIP de video concluido")
    void deveBaixarZip() {
        Video video = videoConcluido("nota fiscal.mp4", "frames/x.zip");
        when(videoRepository.findByIdAndUsuarioId(video.getId(), usuarioId))
            .thenReturn(Optional.of(new VideoEntity(video)));
        when(storagePort.obter("frames/x.zip")).thenReturn(new StoragePort.RecursoArmazenado(
            new ByteArrayInputStream("zip".getBytes()), 3L, "application/zip"));

        ArquivoDownload download = service.baixar(usuarioId, video.getId(), false);

        assertThat(download.nomeArquivo()).isEqualTo("nota fiscal_frames.zip");
        assertThat(download.contentType()).isEqualTo("application/zip");
        assertThat(download.tamanhoBytes()).isEqualTo(3L);
    }

    @Test
    @DisplayName("recusa download de video ainda nao processado")
    void deveRecusarDownloadNaoProcessado() {
        Video video = Video.novo(usuarioId, "maria@fiapx.com", "a.mp4", "video/mp4", 10L, "k");
        when(videoRepository.findByIdAndUsuarioId(video.getId(), usuarioId))
            .thenReturn(Optional.of(new VideoEntity(video)));

        assertThatThrownBy(() -> service.baixar(usuarioId, video.getId(), false))
            .isInstanceOf(VideoNaoProcessadoException.class);
        verifyNoInteractions(storagePort);
    }

    @Test
    @DisplayName("registra conclusao do processamento")
    void deveRegistrarConclusao() {
        Video video = Video.novo(usuarioId, "maria@fiapx.com", "a.mp4", "video/mp4", 10L, "k");
        when(videoRepository.findById(video.getId())).thenReturn(Optional.of(new VideoEntity(video)));

        service.registrarProcessamentoConcluido(video.getId(), "frames/a.zip");

        verify(videoRepository).save(org.mockito.ArgumentMatchers.argThat(
            e -> e.getStatus() == VideoStatus.CONCLUIDO && "frames/a.zip".equals(e.getFramesZipKey())));
    }

    @Test
    @DisplayName("ignora conclusao repetida")
    void deveIgnorarConclusaoRepetida() {
        Video video = videoConcluido("a.mp4", "frames/a.zip");
        when(videoRepository.findById(video.getId())).thenReturn(Optional.of(new VideoEntity(video)));

        service.registrarProcessamentoConcluido(video.getId(), "frames/a.zip");

        verify(videoRepository, never()).save(any());
    }

    @Test
    @DisplayName("falha ao concluir video inexistente")
    void deveFalharConclusaoInexistente() {
        UUID id = UUID.randomUUID();
        when(videoRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registrarProcessamentoConcluido(id, "frames/a.zip"))
            .isInstanceOf(VideoNaoEncontradoException.class);
    }

    @Test
    @DisplayName("registra falha e cria notificacao por e-mail")
    void deveRegistrarFalha() {
        Video video = Video.novo(usuarioId, "maria@fiapx.com", "a.mp4", "video/mp4", 10L, "k");
        when(videoRepository.findById(video.getId())).thenReturn(Optional.of(new VideoEntity(video)));

        service.registrarFalha(video.getId(), "codec invalido");

        verify(videoRepository).save(org.mockito.ArgumentMatchers.argThat(
            e -> e.getStatus() == VideoStatus.ERRO && "codec invalido".equals(e.getErroMensagem())));
        verify(notificacaoRepository).save(notificacaoCaptor.capture());
        NotificacaoEntity notificacao = notificacaoCaptor.getValue();
        assertThat(notificacao.getDestinatario()).isEqualTo("maria@fiapx.com");
        assertThat(notificacao.getAssunto()).contains("a.mp4");
        assertThat(notificacao.getCorpo()).contains("codec invalido", video.getId().toString());
    }

    @Test
    @DisplayName("registra falha sem e-mail cadastrado e nao notifica")
    void deveRegistrarFalhaSemEmail() {
        Video video = Video.novo(usuarioId, " ", "a.mp4", "video/mp4", 10L, "k");
        when(videoRepository.findById(video.getId())).thenReturn(Optional.of(new VideoEntity(video)));

        service.registrarFalha(video.getId(), null);

        verify(notificacaoRepository, never()).save(any());
        verify(videoRepository).save(any(VideoEntity.class));
    }

    @Test
    @DisplayName("ignora evento de falha repetido")
    void deveIgnorarFalhaRepetida() {
        Video video = Video.novo(usuarioId, "maria@fiapx.com", "a.mp4", "video/mp4", 10L, "k");
        video.falhar("primeira");
        when(videoRepository.findById(video.getId())).thenReturn(Optional.of(new VideoEntity(video)));

        service.registrarFalha(video.getId(), "segunda");

        verify(videoRepository, never()).save(any());
        verify(notificacaoRepository, never()).save(any());
    }

    @Test
    @DisplayName("falha ao registrar erro de video inexistente")
    void deveFalharRegistroDeErroInexistente() {
        UUID id = UUID.randomUUID();
        when(videoRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registrarFalha(id, "erro"))
            .isInstanceOf(VideoNaoEncontradoException.class);
    }

    @Test
    @DisplayName("aceita todas as extensoes de video conhecidas mesmo com content type generico")
    void deveAceitarExtensoesConhecidas() {
        for (String extensao : List.of("mp4", "mov", "avi", "mkv", "webm", "mpeg", "mpg", "m4v")) {
            assertThat(uploadComNome("arquivo." + extensao, "text/plain").status()).isEqualTo("PENDENTE");
        }
    }

    @Test
    @DisplayName("recusa extensao desconhecida com content type generico")
    void deveRecusarExtensaoDesconhecidaComTipoGenerico() {
        MockMultipartFile arquivo = new MockMultipartFile(
            "arquivo", "nota.txt", "text/plain", "x".getBytes());

        assertThatThrownBy(() -> service.upload(usuarioId, "maria@fiapx.com", arquivo))
            .isInstanceOf(ArquivoInvalidoException.class);
    }

    @Test
    @DisplayName("deduz video/mp4 quando o content type e octet-stream")
    void deveDeduzirMp4() {
        assertThat(uploadComNome("a.mp4", "application/octet-stream").contentType())
            .isEqualTo("video/mp4");
    }

    @Test
    @DisplayName("trunca nome de arquivo muito longo")
    void deveTruncarNomeLongo() {
        String nome = "a".repeat(300) + ".mp4";

        VideoResponse response = uploadComNome(nome, "video/mp4");

        assertThat(response.nomeArquivo()).hasSize(255).endsWith(".mp4");
    }

    @Test
    @DisplayName("usa extensao bin quando o nome termina em ponto")
    void deveUsarExtensaoBinParaNomeComPontoFinal() {
        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "a.", "video/mp4", "x".getBytes());
        when(videoRepository.save(any(VideoEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service.upload(usuarioId, "maria@fiapx.com", arquivo);

        verify(storagePort).salvar(org.mockito.ArgumentMatchers.endsWith("original.bin"),
            any(InputStream.class), anyLong(), eq("video/mp4"));
    }

    @Test
    @DisplayName("usa extensao bin quando o sufixo nao e alfanumerico curto")
    void deveUsarExtensaoBinParaSufixoInvalido() {
        MockMultipartFile arquivo = new MockMultipartFile(
            "arquivo", "a.extensao-muito-longa", "video/mp4", "x".getBytes());
        when(videoRepository.save(any(VideoEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service.upload(usuarioId, "maria@fiapx.com", arquivo);

        verify(storagePort).salvar(org.mockito.ArgumentMatchers.endsWith("original.bin"),
            any(InputStream.class), anyLong(), eq("video/mp4"));
    }

    @Test
    @DisplayName("nao notifica quando o e-mail do usuario e nulo")
    void deveIgnorarNotificacaoSemEmailNulo() {
        Video video = Video.novo(usuarioId, null, "a.mp4", "video/mp4", 10L, "k");
        when(videoRepository.findById(video.getId())).thenReturn(Optional.of(new VideoEntity(video)));

        service.registrarFalha(video.getId(), "erro");

        verify(notificacaoRepository, never()).save(any());
    }

    @Test
    @DisplayName("usa motivo padrao quando motivo da falha e nulo")
    void deveUsarMotivoPadraoNulo() {
        Video video = Video.novo(usuarioId, "maria@fiapx.com", "a.mp4", "video/mp4", 10L, "k");
        when(videoRepository.findById(video.getId())).thenReturn(Optional.of(new VideoEntity(video)));

        service.registrarFalha(video.getId(), null);

        verify(notificacaoRepository).save(notificacaoCaptor.capture());
        assertThat(notificacaoCaptor.getValue().getCorpo()).contains("erro nao informado");
    }

    @Test
    @DisplayName("usa motivo padrao quando motivo da falha e branco")
    void deveUsarMotivoPadraoBranco() {
        Video video = Video.novo(usuarioId, "maria@fiapx.com", "a.mp4", "video/mp4", 10L, "k");
        when(videoRepository.findById(video.getId())).thenReturn(Optional.of(new VideoEntity(video)));

        service.registrarFalha(video.getId(), " ");

        verify(notificacaoRepository).save(notificacaoCaptor.capture());
        assertThat(notificacaoCaptor.getValue().getCorpo()).contains("erro nao informado");
    }

    private VideoResponse uploadComNome(String nome, String contentType) {
        MockMultipartFile arquivo = new MockMultipartFile(
            "arquivo", nome, contentType, "x".getBytes(StandardCharsets.UTF_8));
        when(videoRepository.save(any(VideoEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        return service.upload(usuarioId, "maria@fiapx.com", arquivo);
    }

    private Video videoConcluido(String nome, String framesKey) {
        Video video = Video.novo(usuarioId, "maria@fiapx.com", nome, "video/mp4", 10L, "k");
        video.concluir(framesKey);
        return video;
    }
}
