package com.fiapx.video.infrastructure.email;

import com.fiapx.video.application.port.EmailPort;
import com.fiapx.video.config.EmailProperties;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailSender;

public class SmtpEmailAdapter implements EmailPort {

    private final MailSender mailSender;
    private final EmailProperties properties;

    public SmtpEmailAdapter(MailSender mailSender, EmailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void enviar(String destinatario, String assunto, String corpo) {
        SimpleMailMessage mensagem = new SimpleMailMessage();
        mensagem.setFrom(properties.getRemetente());
        mensagem.setTo(destinatario);
        mensagem.setSubject(assunto);
        mensagem.setText(corpo);
        mailSender.send(mensagem);
    }
}
