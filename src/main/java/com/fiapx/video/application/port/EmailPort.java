package com.fiapx.video.application.port;

public interface EmailPort {

    void enviar(String destinatario, String assunto, String corpo);
}
