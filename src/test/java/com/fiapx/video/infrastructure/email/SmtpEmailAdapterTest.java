package com.fiapx.video.infrastructure.email;

import com.fiapx.video.config.EmailProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SmtpEmailAdapterTest {

    @Mock
    private MailSender mailSender;

    @Test
    @DisplayName("monta e envia mensagem simples")
    void deveEnviarEmail() {
        EmailProperties properties = new EmailProperties();
        properties.setRemetente("nao-responda@fiapx.com");
        SmtpEmailAdapter adapter = new SmtpEmailAdapter(mailSender, properties);

        adapter.enviar("maria@fiapx.com", "Assunto", "Corpo");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage mensagem = captor.getValue();
        assertThat(mensagem.getFrom()).isEqualTo("nao-responda@fiapx.com");
        assertThat(mensagem.getTo()).containsExactly("maria@fiapx.com");
        assertThat(mensagem.getSubject()).isEqualTo("Assunto");
        assertThat(mensagem.getText()).isEqualTo("Corpo");
    }

    @Test
    @DisplayName("propaga falha do servidor de e-mail")
    void devePropagarFalha() {
        SmtpEmailAdapter adapter = new SmtpEmailAdapter(mailSender, new EmailProperties());
        doThrow(new IllegalStateException("smtp off")).when(mailSender).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));

        assertThatThrownBy(() -> adapter.enviar("maria@fiapx.com", "Assunto", "Corpo"))
            .isInstanceOf(IllegalStateException.class);
    }
}
