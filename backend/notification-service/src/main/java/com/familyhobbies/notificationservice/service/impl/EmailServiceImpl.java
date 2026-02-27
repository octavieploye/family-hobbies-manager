package com.familyhobbies.notificationservice.service.impl;

import com.familyhobbies.errorhandling.exception.web.ResourceNotFoundException;
import com.familyhobbies.notificationservice.config.NotificationProperties;
import com.familyhobbies.notificationservice.entity.EmailTemplate;
import com.familyhobbies.notificationservice.repository.EmailTemplateRepository;
import com.familyhobbies.notificationservice.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final EmailTemplateRepository emailTemplateRepository;
    private final TemplateEngine templateEngine;
    private final NotificationProperties notificationProperties;

    @Override
    public void sendEmail(String to, String subject, String body) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(notificationProperties.getFromEmail());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, true);
            mailSender.send(message);
            log.info("Email sent to {} with subject: {}", to, subject);
        } catch (MessagingException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage(), e);
            throw new RuntimeException("Failed to send email", e);
        }
    }

    @Override
    public void sendTemplatedEmail(String to, String templateCode, Map<String, Object> variables) {
        EmailTemplate template = emailTemplateRepository.findByCodeAndActiveTrue(templateCode)
                .orElseThrow(() -> new ResourceNotFoundException("EmailTemplate", templateCode,
                        "Email template not found with code: " + templateCode));

        Context context = new Context();
        context.setVariables(variables);

        String htmlBody = templateEngine.process("email/" + templateCode.toLowerCase(), context);

        sendEmail(to, template.getSubject(), htmlBody);
    }
}
