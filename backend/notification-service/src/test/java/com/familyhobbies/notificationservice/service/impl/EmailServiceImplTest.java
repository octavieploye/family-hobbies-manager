package com.familyhobbies.notificationservice.service.impl;

import com.familyhobbies.errorhandling.exception.web.ResourceNotFoundException;
import com.familyhobbies.notificationservice.config.NotificationProperties;
import com.familyhobbies.notificationservice.entity.EmailTemplate;
import com.familyhobbies.notificationservice.repository.EmailTemplateRepository;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.IContext;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for EmailServiceImpl.
 *
 * Story: S6-005 -- Email Templates
 * Tests: 3 test methods
 */
@ExtendWith(MockitoExtension.class)
class EmailServiceImplTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private EmailTemplateRepository emailTemplateRepository;

    @Mock
    private TemplateEngine templateEngine;

    @Mock
    private NotificationProperties notificationProperties;

    @InjectMocks
    private EmailServiceImpl emailService;

    @BeforeEach
    void setUp() {
    }

    @Test
    @DisplayName("should send email when valid params")
    void should_sendEmail_when_validParams() {
        // given
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(notificationProperties.getFromEmail()).thenReturn("test@family-hobbies.fr");

        // when
        emailService.sendEmail("user@email.com", "Test Subject", "<p>Test body</p>");

        // then
        verify(mailSender).send(mimeMessage);
    }

    @Test
    @DisplayName("should send templated email when template exists")
    void should_sendTemplatedEmail_when_templateExists() {
        // given
        EmailTemplate template = EmailTemplate.builder()
                .code("WELCOME")
                .subject("Bienvenue")
                .bodyTemplate("Bonjour {{firstName}}")
                .category("WELCOME")
                .active(true)
                .build();
        when(emailTemplateRepository.findByCodeAndActiveTrue("WELCOME")).thenReturn(Optional.of(template));
        when(templateEngine.process(eq("email/welcome"), any(IContext.class))).thenReturn("<p>Processed</p>");

        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(notificationProperties.getFromEmail()).thenReturn("test@family-hobbies.fr");

        // when
        emailService.sendTemplatedEmail("user@email.com", "WELCOME", Map.of("firstName", "Jean"));

        // then
        verify(templateEngine).process(eq("email/welcome"), any(IContext.class));
        verify(mailSender).send(mimeMessage);
    }

    @Test
    @DisplayName("should throw exception when template not found")
    void should_throwException_when_templateNotFound() {
        // given
        when(emailTemplateRepository.findByCodeAndActiveTrue("NONEXISTENT")).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> emailService.sendTemplatedEmail(
                "user@email.com", "NONEXISTENT", Map.of()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Email template not found with code: NONEXISTENT");
    }
}
