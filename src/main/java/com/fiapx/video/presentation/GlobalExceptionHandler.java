package com.fiapx.video.presentation;

import com.fiapx.video.application.exception.ArquivoInvalidoException;
import com.fiapx.video.application.exception.ArmazenamentoException;
import com.fiapx.video.application.exception.VideoNaoEncontradoException;
import com.fiapx.video.application.exception.VideoNaoProcessadoException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResponse> tratarValidacao(MethodArgumentNotValidException ex,
                                                        HttpServletRequest request) {
        Map<String, String> detalhes = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
            .forEach(erro -> detalhes.putIfAbsent(erro.getField(), erro.getDefaultMessage()));
        return responder(HttpStatus.BAD_REQUEST, "DADOS_INVALIDOS", "Dados de entrada invalidos",
            request.getRequestURI(), detalhes);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErroResponse> tratarCorpoInvalido(HttpMessageNotReadableException ex,
                                                            HttpServletRequest request) {
        return responder(HttpStatus.BAD_REQUEST, "CORPO_INVALIDO", "Corpo da requisicao invalido",
            request.getRequestURI(), null);
    }

    @ExceptionHandler(ArquivoInvalidoException.class)
    public ResponseEntity<ErroResponse> tratarArquivoInvalido(ArquivoInvalidoException ex,
                                                              HttpServletRequest request) {
        return responder(HttpStatus.BAD_REQUEST, "ARQUIVO_INVALIDO", ex.getMessage(),
            request.getRequestURI(), null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErroResponse> tratarUploadExcedido(MaxUploadSizeExceededException ex,
                                                             HttpServletRequest request) {
        return responder(HttpStatus.PAYLOAD_TOO_LARGE, "ARQUIVO_MUITO_GRANDE",
            "Arquivo excede o tamanho maximo permitido", request.getRequestURI(), null);
    }

    @ExceptionHandler(VideoNaoEncontradoException.class)
    public ResponseEntity<ErroResponse> tratarNaoEncontrado(VideoNaoEncontradoException ex,
                                                            HttpServletRequest request) {
        return responder(HttpStatus.NOT_FOUND, "VIDEO_NAO_ENCONTRADO", ex.getMessage(),
            request.getRequestURI(), null);
    }

    @ExceptionHandler(VideoNaoProcessadoException.class)
    public ResponseEntity<ErroResponse> tratarNaoProcessado(VideoNaoProcessadoException ex,
                                                            HttpServletRequest request) {
        return responder(HttpStatus.CONFLICT, "VIDEO_NAO_PROCESSADO", ex.getMessage(),
            request.getRequestURI(), null);
    }

    @ExceptionHandler(ArmazenamentoException.class)
    public ResponseEntity<ErroResponse> tratarArmazenamento(ArmazenamentoException ex,
                                                            HttpServletRequest request) {
        log.error("Falha de armazenamento em {}", request.getRequestURI(), ex);
        return responder(HttpStatus.BAD_GATEWAY, "FALHA_ARMAZENAMENTO",
            "Nao foi possivel acessar o armazenamento de objetos", request.getRequestURI(), null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResponse> tratarErroInesperado(Exception ex, HttpServletRequest request) {
        log.error("Erro inesperado ao processar {}", request.getRequestURI(), ex);
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, "ERRO_INTERNO",
            "Erro interno ao processar a requisicao", request.getRequestURI(), null);
    }

    private ResponseEntity<ErroResponse> responder(HttpStatus status, String erro, String mensagem,
                                                   String caminho, Map<String, String> detalhes) {
        return ResponseEntity.status(status).body(new ErroResponse(
            LocalDateTime.now(), status.value(), erro, mensagem, caminho, detalhes));
    }
}
