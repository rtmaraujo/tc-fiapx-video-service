package com.fiapx.video.application;

import com.fiapx.video.application.dto.ArquivoDownload;
import com.fiapx.video.application.dto.PaginaResponse;
import com.fiapx.video.application.dto.VideoResponse;
import com.fiapx.video.domain.VideoStatus;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface VideoApplicationService {

    VideoResponse upload(UUID usuarioId, String emailUsuario, MultipartFile arquivo);

    PaginaResponse<VideoResponse> listar(UUID usuarioId, VideoStatus status, int pagina, int tamanho);

    VideoResponse buscar(UUID usuarioId, UUID videoId, boolean admin);

    ArquivoDownload baixar(UUID usuarioId, UUID videoId, boolean admin);

    void registrarProcessamentoConcluido(UUID videoId, String framesZipKey);

    void registrarFalha(UUID videoId, String motivo);
}
