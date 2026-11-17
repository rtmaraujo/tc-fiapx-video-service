package com.fiapx.video.config;

import com.fiapx.video.infrastructure.email.SmtpEmailAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.MailSender;

@Configuration
public class EmailConfig {

    @Bean
    public SmtpEmailAdapter emailPort(MailSender mailSender, EmailProperties properties) {
        return new SmtpEmailAdapter(mailSender, properties);
    }
}
