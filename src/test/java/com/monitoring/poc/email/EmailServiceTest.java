package com.monitoring.poc.email;

import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class EmailServiceTest {

    @Test
    void mailDisabledSkipsSendEntirely() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        EmailServiceImpl service = new EmailServiceImpl(mailSender, "noreply@example.com", false);

        service.sendOtpEmail("user@example.com", "123456");

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void mailExceptionFromSenderIsSwallowed() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        doThrow(new MailSendException("smtp unreachable")).when(mailSender).send(any(SimpleMailMessage.class));
        EmailServiceImpl service = new EmailServiceImpl(mailSender, "noreply@example.com", true);

        service.sendOtpEmail("user@example.com", "123456");

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void blankRecipientIsSkipped() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        EmailServiceImpl service = new EmailServiceImpl(mailSender, "noreply@example.com", true);

        service.sendMetricApprovedEmail("  ", "some_metric");

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }
}
