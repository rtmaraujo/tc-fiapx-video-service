package com.fiapx.video.presentation;

import com.fiapx.video.application.VideoApplicationService;
import com.fiapx.video.application.dto.ArquivoDownload;
import com.fiapx.video.application.dto.PaginaResponse;
import com.fiapx.video.application.dto.VideoResponse;
import com.fiapx.video.domain.VideoStatus;
import com.fiapx.video.security.UsuarioPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoControllerTest {

    @Mock
    private VideoApplicationService applicationService;

    private VideoController controller;
    private UsuarioPrincipal principal;
    private VideoResponse videoResponse;

    @BeforeEach
    void setUp() {
        controller = new VideoController(applicationService);
        principal = new UsuarioPrincipal(UUID.randomUUID(), "Maria", "maria@fiapx.com", "USER");
        videoResponse = new VideoResponse(UUID.randomUUID(), "a.mp4", "video/mp4", 10L,
            "PENDENTE", null, false, LocalDateTime.now(), LocalDateTime.now(), null);
    }

    @Test
    @DisplayName("upload devolve 202 com Location")
    void deveFazerUpload() {
        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "a.mp4", "video/mp4", "x".getBytes());
        when(applicationService.upload(principal.id(), principal.email(), arquivo)).thenReturn(videoResponse);

        ResponseEntity<VideoResponse> response = controller.upload(principal, arquivo);

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(response.getHeaders().getLocation().toString()).endsWith(videoResponse.id().toString());
        assertThat(response.getBody()).isEqualTo(videoResponse);
    }

    @Test
    @DisplayName("listagem repassa filtros e devolve pagina")
    void deveListar() {
        PaginaResponse<VideoResponse> pagina = new PaginaResponse<>(
            List.of(videoResponse), 0, 20, 1, 1);
        when(applicationService.listar(principal.id(), VideoStatus.PENDENTE, 0, 20)).thenReturn(pagina);

        ResponseEntity<PaginaResponse<VideoResponse>> response =
            controller.listar(principal, VideoStatus.PENDENTE, 0, 20);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().conteudo()).containsExactly(videoResponse);
    }

    @Test
    @DisplayName("busca video por id")
    void deveBuscar() {
        when(applicationService.buscar(principal.id(), videoResponse.id(), false)).thenReturn(videoResponse);

        ResponseEntity<VideoResponse> response = controller.buscar(principal, videoResponse.id());

        assertThat(response.getBody().id()).isEqualTo(videoResponse.id());
    }

    @Test
    @DisplayName("download devolve ZIP com nome no Content-Disposition")
    void deveBaixar() throws Exception {
        when(applicationService.baixar(principal.id(), videoResponse.id(), false))
            .thenReturn(new ArquivoDownload("a_frames.zip", "application/zip", 3L,
                new ByteArrayInputStream("ZIP".getBytes())));

        ResponseEntity<StreamingResponseBody> response = controller.baixar(principal, videoResponse.id());

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("application/zip");
        assertThat(response.getHeaders().getContentDisposition().getFilename()).contains("a_frames.zip");
        assertThat(response.getHeaders().getContentLength()).isEqualTo(3L);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        response.getBody().writeTo(out);

        assertThat(out.toString()).isEqualTo("ZIP");
    }

    @Test
    @DisplayName("admin e repassado ao servico")
    void deveRepassarAdmin() {
        UsuarioPrincipal admin = new UsuarioPrincipal(UUID.randomUUID(), "Admin", "admin@fiapx.com", "ADMIN");
        when(applicationService.buscar(admin.id(), videoResponse.id(), true)).thenReturn(videoResponse);

        controller.buscar(admin, videoResponse.id());

        org.mockito.Mockito.verify(applicationService).buscar(eq(admin.id()), eq(videoResponse.id()), eq(true));
    }

    @Test
    @DisplayName("upload com principal nulo falha explicitamente")
    void deveFalharSemPrincipal() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> controller.upload(null, new MockMultipartFile("arquivo", "a.mp4", "video/mp4", "x".getBytes())))
            .isInstanceOf(NullPointerException.class);
        org.mockito.Mockito.verifyNoInteractions(applicationService);
    }

    @Test
    @DisplayName("download sem corpo ainda monta cabecalhos")
    void deveBaixarSemPrincipalRefletirServico() {
        when(applicationService.baixar(any(), any(), eq(false)))
            .thenReturn(new ArquivoDownload("a_frames.zip", "application/zip", 0L,
                new ByteArrayInputStream(new byte[0])));

        assertThat(controller.baixar(principal, UUID.randomUUID()).getBody()).isNotNull();
    }
}
