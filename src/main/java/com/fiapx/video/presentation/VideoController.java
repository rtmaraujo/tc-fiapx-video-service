package com.fiapx.video.presentation;

import com.fiapx.video.application.VideoApplicationService;
import com.fiapx.video.application.dto.ArquivoDownload;
import com.fiapx.video.application.dto.PaginaResponse;
import com.fiapx.video.application.dto.VideoResponse;
import com.fiapx.video.domain.VideoStatus;
import com.fiapx.video.security.UsuarioPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/videos")
@Tag(name = "Videos", description = "Upload, acompanhamento e download dos frames processados")
public class VideoController {

    private final VideoApplicationService applicationService;

    public VideoController(VideoApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Envia um video para processamento assincrono")
    public ResponseEntity<VideoResponse> upload(@AuthenticationPrincipal UsuarioPrincipal principal,
                                                @RequestPart("arquivo") MultipartFile arquivo) {
        VideoResponse response = applicationService.upload(principal.id(), principal.email(), arquivo);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .header(HttpHeaders.LOCATION, "/api/videos/" + response.id())
            .body(response);
    }

    @GetMapping
    @Operation(summary = "Lista os videos do usuario autenticado")
    public ResponseEntity<PaginaResponse<VideoResponse>> listar(
        @AuthenticationPrincipal UsuarioPrincipal principal,
        @RequestParam(name = "status", required = false) VideoStatus status,
        @RequestParam(name = "pagina", defaultValue = "0") int pagina,
        @RequestParam(name = "tamanho", defaultValue = "20") int tamanho) {
        return ResponseEntity.ok(applicationService.listar(principal.id(), status, pagina, tamanho));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta o status de um video")
    public ResponseEntity<VideoResponse> buscar(@AuthenticationPrincipal UsuarioPrincipal principal,
                                                @PathVariable UUID id) {
        return ResponseEntity.ok(applicationService.buscar(principal.id(), id, principal.admin()));
    }

    @GetMapping("/{id}/download")
    @Operation(summary = "Baixa o ZIP com os frames extraidos do video")
    public ResponseEntity<StreamingResponseBody> baixar(@AuthenticationPrincipal UsuarioPrincipal principal,
                                                        @PathVariable UUID id) {
        ArquivoDownload arquivo = applicationService.baixar(principal.id(), id, principal.admin());
        StreamingResponseBody body = outputStream -> {
            try (InputStream conteudo = arquivo.conteudo()) {
                conteudo.transferTo(outputStream);
            }
        };
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(arquivo.contentType()))
            .contentLength(arquivo.tamanhoBytes())
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(arquivo.nomeArquivo(), StandardCharsets.UTF_8)
                .build()
                .toString())
            .body(body);
    }
}
