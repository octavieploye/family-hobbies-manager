package com.familyhobbies.notificationservice.service;

import java.util.Map;

/**
 * Service for sending emails, both plain and templated.
 */
public interface EmailService {

    /**
     * Sends a plain-text email.
     *
     * @param to      recipient email address
     * @param subject email subject
     * @param body    email body (plain text or HTML)
     */
    void sendEmail(String to, String subject, String body);

    /**
     * Sends an email using a stored template, with Thymeleaf variable substitution.
     *
     * @param to           recipient email address
     * @param templateCode the code of the email template in DB
     * @param variables    template variables for Thymeleaf processing
     */
    void sendTemplatedEmail(String to, String templateCode, Map<String, Object> variables);
}
