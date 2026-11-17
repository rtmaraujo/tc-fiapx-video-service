package com.fiapx.video.presentation;

import com.fiapx.video.application.exception.ArquivoInvalidoException;
import com.fiapx.video.application.exception.ArmazenamentoException;
import com.fiapx.video.application.exception.VideoNaoEncontradoException;
import com.fiapx.video.application.exception.VideoNaoProcessadoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest();
        request.setRequestURI("/api/videos");
    }

    @Test
    @DisplayName("trata validacao com detalhes por campo")
    void deveTratarValidacao() throws Exception {
        Method method = Dummy.class.getDeclaredMethod("executar", ComNome.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(
            new ComNome(), "corpo");
        bindingResult.rejectValue("nome", "NotBlank", "nome e obrigatorio");

        var response = handler.tratarValidacao(
            new MethodArgumentNotValidException(parameter, bindingResult), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().detalhes()).containsEntry("nome", "nome e obrigatorio");
    }

    @Test
    @DisplayName("trata corpo invalido")
    void deveTratarCorpoInvalido() {
        var response = handler.tratarCorpoInvalido(
            new HttpMessageNotReadableException("x", new MockHttpInputMessage(new byte[0])), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().erro()).isEqualTo("CORPO_INVALIDO");
    }

    @Test
    @DisplayName("trata arquivo invalido")
    void deveTratarArquivoInvalido() {
        var response = handler.tratarArquivoInvalido(new ArquivoInvalidoException(" arquivo ruim"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().mensagem()).isEqualTo(" arquivo ruim");
    }

    @Test
    @DisplayName("trata upload acima do limite")
    void deveTratarUploadGrande() {
        var response = handler.tratarUploadExcedido(new MaxUploadSizeExceededException(10L), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody().erro()).isEqualTo("ARQUIVO_MUITO_GRANDE");
    }

    @Test
    @DisplayName("trata video nao encontrado")
    void deveTratarNaoEncontrado() {
        var response = handler.tratarNaoEncontrado(new VideoNaoEncontradoException("nao achou"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("trata video nao processado")
    void deveTratarNaoProcessado() {
        var response = handler.tratarNaoProcessado(new VideoNaoProcessadoException("aguarde"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().erro()).isEqualTo("VIDEO_NAO_PROCESSADO");
    }

    @Test
    @DisplayName("trata falha de armazenamento")
    void deveTratarArmazenamento() {
        var response = handler.tratarArmazenamento(
            new ArmazenamentoException("minio off", new IllegalStateException("x")), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    @DisplayName("trata erro inesperado")
    void deveTratarErroInesperado() {
        var response = handler.tratarErroInesperado(new IllegalStateException("boom"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().erro()).isEqualTo("ERRO_INTERNO");
    }

    static class Dummy {
        void executar(ComNome corpo) {
        }
    }

    public static class ComNome {
        private String nome;

        public String getNome() {
            return nome;
        }

        public void setNome(String nome) {
            this.nome = nome;
        }
    }
}
