package com.fiapx.video.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class VideoTest {

    @Test
    @DisplayName("cria video novo como PENDENTE")
    void deveCriarVideoNovo() {
        UUID usuarioId = UUID.randomUUID();

        Video video = Video.novo(usuarioId, "maria@fiapx.com", "aula.mp4", "video/mp4", 100L, "videos/a.mp4");

        assertThat(video.getId()).isNotNull();
        assertThat(video.getUsuarioId()).isEqualTo(usuarioId);
        assertThat(video.getEmailUsuario()).isEqualTo("maria@fiapx.com");
        assertThat(video.getStatus()).isEqualTo(VideoStatus.PENDENTE);
        assertThat(video.getCriadoEm()).isNotNull();
        assertThat(video.getAtualizadoEm()).isEqualTo(video.getCriadoEm());
        assertThat(video.estaConcluido()).isFalse();
        assertThat(video.pertenceA(usuarioId)).isTrue();
        assertThat(video.pertenceA(UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("marca video como PROCESSANDO e limpa erro anterior")
    void deveMarcarProcessando() {
        Video video = Video.novo(UUID.randomUUID(), "maria@fiapx.com", "a.mp4", "video/mp4", 10L, "k");
        video.falhar("erro antigo");

        video.marcarProcessando();

        assertThat(video.getStatus()).isEqualTo(VideoStatus.PROCESSANDO);
        assertThat(video.getErroMensagem()).isNull();
    }

    @Test
    @DisplayName("nao reabre video ja concluido")
    void naoDeveReabrirConcluido() {
        Video video = Video.novo(UUID.randomUUID(), "maria@fiapx.com", "a.mp4", "video/mp4", 10L, "k");
        video.concluir("frames.zip");

        video.marcarProcessando();

        assertThat(video.getStatus()).isEqualTo(VideoStatus.CONCLUIDO);
    }

    @Test
    @DisplayName("conclui video com chave do ZIP")
    void deveConcluir() {
        Video video = Video.novo(UUID.randomUUID(), "maria@fiapx.com", "a.mp4", "video/mp4", 10L, "k");

        video.concluir("frames/a.zip");

        assertThat(video.getStatus()).isEqualTo(VideoStatus.CONCLUIDO);
        assertThat(video.getFramesZipKey()).isEqualTo("frames/a.zip");
        assertThat(video.getProcessadoEm()).isNotNull();
        assertThat(video.estaConcluido()).isTrue();
        assertThat(video.getErroMensagem()).isNull();
    }

    @Test
    @DisplayName("nao considera concluido sem a chave do ZIP")
    void naoDeveConsiderarConcluidoSemZip() {
        Video video = Video.novo(UUID.randomUUID(), "maria@fiapx.com", "a.mp4", "video/mp4", 10L, "k");
        video.setStatus(VideoStatus.CONCLUIDO);
        video.setFramesZipKey(" ");

        assertThat(video.estaConcluido()).isFalse();
    }

    @Test
    @DisplayName("nao considera concluido sem chave nula")
    void naoDeveConsiderarConcluidoComZipNulo() {
        Video video = Video.novo(UUID.randomUUID(), "maria@fiapx.com", "a.mp4", "video/mp4", 10L, "k");
        video.setStatus(VideoStatus.CONCLUIDO);

        assertThat(video.estaConcluido()).isFalse();
    }

    @Test
    @DisplayName("registra falha com motivo")
    void deveFalhar() {
        Video video = Video.novo(UUID.randomUUID(), "maria@fiapx.com", "a.mp4", "video/mp4", 10L, "k");

        video.falhar("codec invalido");

        assertThat(video.getStatus()).isEqualTo(VideoStatus.ERRO);
        assertThat(video.getErroMensagem()).isEqualTo("codec invalido");
        assertThat(video.getProcessadoEm()).isNotNull();
    }

    @Test
    @DisplayName("video de outro usuario nao pertence ao usuario informado")
    void deveCompararDonoNulo() {
        Video video = Video.novo(null, "maria@fiapx.com", "a.mp4", "video/mp4", 10L, "k");

        assertThat(video.pertenceA(UUID.randomUUID())).isFalse();
    }
}
