# Story S6-002: Implement Kafka Consumers

> 8 points | Priority: P0 | Service: notification-service
> Sprint file: [Back to Sprint Index](./_index.md)
> Tests: [S6-002 Tests Companion](./S6-002-kafka-consumers-tests.md)

---

## Context

The notification-service must consume Kafka events published by user-service, association-service, and payment-service to create in-app notifications and send emails. This story implements the full event consumption pipeline: KafkaConsumerConfig (exact architecture config), four event consumer classes (UserEventConsumer, SubscriptionEventConsumer, PaymentEventConsumer, AttendanceEventConsumer), a NotificationCreationService that orchestrates notification creation with preference checking, and an EmailService that resolves Thymeleaf templates from the database and sends emails via Spring Mail. Error handling uses exponential backoff with dead-letter topics (DLT) for failed messages. This story depends on S6-001 (entities, enums, repositories) being complete.

## Tasks

| # | Task | File Path | What To Create | How To Verify |
|---|------|-----------|----------------|---------------|
| 1 | application.yml Kafka + Mail config | `backend/notification-service/src/main/resources/application.yml` | Kafka consumer + Spring Mail config block | Service starts, connects to Kafka broker |
| 2 | KafkaConsumerConfig | `backend/notification-service/src/main/java/.../config/KafkaConsumerConfig.java` | Exact config from architecture docs | Bean loads, consumers connect |
| 3 | KafkaErrorHandlerConfig | `backend/notification-service/src/main/java/.../config/KafkaErrorHandlerConfig.java` | DefaultErrorHandler with ExponentialBackOff + DLT | Failed messages retry 3x then go to DLT |
| 4 | MailConfig | `backend/notification-service/src/main/java/.../config/MailConfig.java` | Thymeleaf StringTemplateEngine bean | Template engine resolves DB templates |
| 5 | EmailService interface | `backend/notification-service/src/main/java/.../service/EmailService.java` | Interface with sendEmail method | Compiles |
| 6 | EmailServiceImpl | `backend/notification-service/src/main/java/.../service/impl/EmailServiceImpl.java` | Template resolution, Thymeleaf rendering, JavaMailSender | Email sent with rendered template |
| 7 | NotificationCreationService interface | `backend/notification-service/src/main/java/.../service/NotificationCreationService.java` | Interface for internal notification creation | Compiles |
| 8 | NotificationCreationServiceImpl | `backend/notification-service/src/main/java/.../service/impl/NotificationCreationServiceImpl.java` | Preference-aware notification creation + email trigger | Notification created per preferences |
| 9 | UserEventConsumer | `backend/notification-service/src/main/java/.../listener/UserEventConsumer.java` | Kafka listener for user.registered + user.deleted | WELCOME notification + RGPD cleanup |
| 10 | SubscriptionEventConsumer | `backend/notification-service/src/main/java/.../listener/SubscriptionEventConsumer.java` | Kafka listener for subscription.created + cancelled | Subscription notifications created |
| 11 | PaymentEventConsumer | `backend/notification-service/src/main/java/.../listener/PaymentEventConsumer.java` | Kafka listener for payment.completed + failed | Payment notifications created |
| 12 | AttendanceEventConsumer | `backend/notification-service/src/main/java/.../listener/AttendanceEventConsumer.java` | Kafka listener for attendance.marked | Attendance logged (future threshold) |
| 13 | Failing tests (TDD contract) | See [companion file](./S6-002-kafka-consumers-tests.md) | JUnit 5 test classes | Tests define contract |

---

## Task 1 Detail: application.yml Kafka + Mail Config

- **What**: YAML configuration for Kafka consumer properties and Spring Mail (SMTP) settings
- **Where**: `backend/notification-service/src/main/resources/application.yml`
- **Why**: The Kafka consumer needs bootstrap-servers, group-id, deserializer settings, and trusted packages. Spring Mail needs SMTP host/port for email delivery. MailHog is used in development.
- **Content** (add/merge into existing application.yml):

```yaml
spring:
  application:
    name: notification-service

  # ── Database ────────────────────────────────────────────────────────────
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/familyhobbies_notifications
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}
    driver-class-name: org.postgresql.Driver

  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect

  # ── Liquibase ───────────────────────────────────────────────────────────
  liquibase:
    change-log: classpath:db/changelog/db.changelog-master.xml

  # ── Kafka Consumer ─────────────────────────────────────────────────────
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    consumer:
      group-id: notification-service-group
      client-id: ${spring.application.name}-consumer
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      auto-offset-reset: earliest
      enable-auto-commit: false
      properties:
        spring.json.trusted.packages: "com.familyhobbies.common.event"
        spring.json.use.type.headers: true
    listener:
      ack-mode: RECORD
      concurrency: 3

  # ── Spring Mail (MailHog for development) ───────────────────────────────
  mail:
    host: ${MAIL_HOST:localhost}
    port: ${MAIL_PORT:1025}
    username: ${MAIL_USERNAME:}
    password: ${MAIL_PASSWORD:}
    properties:
      mail:
        smtp:
          auth: ${MAIL_SMTP_AUTH:false}
          starttls:
            enable: ${MAIL_SMTP_STARTTLS:false}
    default-encoding: UTF-8

# ── Server ──────────────────────────────────────────────────────────────
server:
  port: 8084

# ── Eureka ──────────────────────────────────────────────────────────────
eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_URI:http://localhost:8761/eureka}
  instance:
    prefer-ip-address: true

# ── Application Custom Properties ────────────────────────────────────────
notification:
  mail:
    from: ${MAIL_FROM:noreply@familyhobbies.fr}
    from-name: ${MAIL_FROM_NAME:Family Hobbies Manager}
```

- **Verify**: `mvn spring-boot:run -pl backend/notification-service` -> application starts, connects to Kafka and PostgreSQL

---

## Task 2 Detail: KafkaConsumerConfig

- **What**: Exact Kafka consumer configuration from the architecture specification
- **Where**: `backend/notification-service/src/main/java/com/familyhobbies/notificationservice/config/KafkaConsumerConfig.java`
- **Why**: Centralizes Kafka consumer factory and listener container factory configuration. Uses the exact class from architecture docs to ensure consistency across the platform.
- **Content**:

```java
package com.familyhobbies.notificationservice.config;

import com.familyhobbies.common.event.*;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.familyhobbies.common.event");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, true);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            DefaultErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(3);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
```

- **Verify**: `mvn compile -pl backend/notification-service` -> compiles; application starts and consumer connects to Kafka broker

---

## Task 3 Detail: KafkaErrorHandlerConfig

- **What**: Configuration bean producing a `DefaultErrorHandler` with exponential backoff and dead-letter topic publishing
- **Where**: `backend/notification-service/src/main/java/com/familyhobbies/notificationservice/config/KafkaErrorHandlerConfig.java`
- **Why**: Failed Kafka message processing must retry with increasing delays before sending to a dead-letter topic (DLT). This prevents infinite retry loops and ensures no messages are lost.
- **Content**:

```java
package com.familyhobbies.notificationservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Configures Kafka error handling with exponential backoff and dead-letter topic (DLT).
 *
 * <p>Retry policy:
 * <ul>
 *   <li>Initial interval: 1000ms</li>
 *   <li>Multiplier: 2.0</li>
 *   <li>Max interval: 30000ms</li>
 *   <li>Max attempts: 3</li>
 * </ul>
 *
 * <p>After all retries are exhausted, the failed record is published to a DLT
 * (dead-letter topic) named {@code {original-topic}.DLT}.
 */
@Configuration
public class KafkaErrorHandlerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaErrorHandlerConfig.class);

    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(kafkaTemplate);

        ExponentialBackOff backOff = new ExponentialBackOff();
        backOff.setInitialInterval(1000L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(30000L);
        backOff.setMaxElapsedTime(90000L); // ~3 attempts with exponential delays

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);

        errorHandler.setRetryListeners((record, ex, deliveryAttempt) ->
                log.warn("Kafka retry attempt {} for topic={}, partition={}, offset={}: {}",
                        deliveryAttempt, record.topic(), record.partition(),
                        record.offset(), ex.getMessage()));

        return errorHandler;
    }
}
```

- **Verify**: `mvn compile -pl backend/notification-service` -> compiles; send a poison pill message -> message retried 3 times then appears in DLT

> **Convention**: If `notification-service` is consumer-only (no Kafka producer configured), use `DefaultErrorHandler` with `FixedBackOff` recovery instead of `DeadLetterPublishingRecoverer`. DLT publishing requires a `KafkaTemplate` producer bean. Only use `DeadLetterPublishingRecoverer` if the service has a configured `KafkaTemplate`.

---

## Task 4 Detail: MailConfig

- **What**: Spring configuration providing a Thymeleaf `SpringTemplateEngine` bean configured for string-based template resolution (templates from DB, not files)
- **Where**: `backend/notification-service/src/main/java/com/familyhobbies/notificationservice/config/MailConfig.java`
- **Why**: Email templates are stored in the database (t_email_template), not as static files. We need a Thymeleaf engine that can process template strings loaded at runtime.
- **Content**:

```java
package com.familyhobbies.notificationservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

/**
 * Configures Thymeleaf for processing email templates stored in the database.
 *
 * <p>Uses a {@link StringTemplateResolver} so templates are resolved from
 * {@code String} content (loaded from {@code t_email_template.body_template})
 * rather than from the classpath or filesystem.
 */
@Configuration
public class MailConfig {

    @Bean
    public SpringTemplateEngine emailTemplateEngine() {
        SpringTemplateEngine engine = new SpringTemplateEngine();
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCacheable(false);
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
```

- **Verify**: `mvn compile -pl backend/notification-service` -> compiles; inject `SpringTemplateEngine` in test -> processes template strings

---

## Task 5 Detail: EmailService Interface

- **What**: Service interface defining the email sending contract
- **Where**: `backend/notification-service/src/main/java/com/familyhobbies/notificationservice/service/EmailService.java`
- **Why**: Decouples email sending from consumers. The interface allows mocking in tests and future implementation swaps (e.g., SendGrid, AWS SES).
- **Content**:

```java
package com.familyhobbies.notificationservice.service;

import com.familyhobbies.notificationservice.enums.NotificationCategory;

import java.util.Map;

/**
 * Service for sending templated emails.
 *
 * <p>Resolves the email template from the database by category code,
 * renders it with Thymeleaf using the provided variables, and sends
 * the email via Spring Mail (JavaMailSender).
 */
public interface EmailService {

    /**
     * Sends a templated email for the given notification category.
     *
     * @param to        recipient email address
     * @param category  the notification category (maps to template code)
     * @param variables template variables for Thymeleaf rendering
     *                  (e.g. "firstName" -> "Marie", "amount" -> "50.00")
     */
    void sendEmail(String to, NotificationCategory category, Map<String, Object> variables);
}
```

- **Verify**: `mvn compile -pl backend/notification-service` -> compiles

---

## Task 6 Detail: EmailServiceImpl

- **What**: Implementation that loads the email template from the database, renders subject and body with Thymeleaf, and sends via JavaMailSender
- **Where**: `backend/notification-service/src/main/java/com/familyhobbies/notificationservice/service/impl/EmailServiceImpl.java`
- **Why**: Connects the template resolution, Thymeleaf rendering, and SMTP sending into a single cohesive flow. Logs warnings if template is not found (does not throw -- email failure should not block notification creation).
- **Content**:

```java
package com.familyhobbies.notificationservice.service.impl;

import com.familyhobbies.notificationservice.entity.EmailTemplate;
import com.familyhobbies.notificationservice.enums.NotificationCategory;
import com.familyhobbies.notificationservice.repository.EmailTemplateRepository;
import com.familyhobbies.notificationservice.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Map;
import java.util.Optional;

/**
 * Sends templated emails by resolving templates from the database.
 *
 * <p>Flow:
 * <ol>
 *   <li>Load the active template by category code from {@code t_email_template}</li>
 *   <li>Render subject and body using Thymeleaf with the provided variables</li>
 *   <li>Send the email via {@link JavaMailSender} as HTML MIME message</li>
 * </ol>
 *
 * <p>If the template is not found or email sending fails, a warning is logged
 * but no exception is thrown -- email failure must not block notification creation.
 */
@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);

    private final EmailTemplateRepository emailTemplateRepository;
    private final SpringTemplateEngine emailTemplateEngine;
    private final JavaMailSender mailSender;

    @Value("${notification.mail.from}")
    private String fromAddress;

    @Value("${notification.mail.from-name}")
    private String fromName;

    public EmailServiceImpl(EmailTemplateRepository emailTemplateRepository,
                            SpringTemplateEngine emailTemplateEngine,
                            JavaMailSender mailSender) {
        this.emailTemplateRepository = emailTemplateRepository;
        this.emailTemplateEngine = emailTemplateEngine;
        this.mailSender = mailSender;
    }

    @Override
    public void sendEmail(String to, NotificationCategory category,
                          Map<String, Object> variables) {
        String templateCode = category.name();

        Optional<EmailTemplate> templateOpt =
                emailTemplateRepository.findByCodeAndActiveTrue(templateCode);

        if (templateOpt.isEmpty()) {
            log.warn("No active email template found for category={}, skipping email to {}",
                    templateCode, to);
            return;
        }

        EmailTemplate template = templateOpt.get();

        try {
            Context context = new Context();
            context.setVariables(variables);

            String renderedSubject = emailTemplateEngine
                    .process(template.getSubjectTemplate(), context);
            String renderedBody = emailTemplateEngine
                    .process(template.getBodyTemplate(), context);

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(fromAddress, fromName);
            helper.setTo(to);
            helper.setSubject(renderedSubject);
            helper.setText(renderedBody, true);

            mailSender.send(mimeMessage);
            log.info("Email sent successfully to={} category={}", to, templateCode);

        } catch (MessagingException | java.io.UnsupportedEncodingException ex) {
            log.error("Failed to send email to={} category={}: {}",
                    to, templateCode, ex.getMessage(), ex);
        }
    }
}
```

- **Verify**: `mvn compile -pl backend/notification-service` -> compiles; call `sendEmail` with MailHog running -> email appears in MailHog UI

---

## Task 7 Detail: NotificationCreationService Interface

- **What**: Internal service interface for creating notifications with preference checking
- **Where**: `backend/notification-service/src/main/java/com/familyhobbies/notificationservice/service/NotificationCreationService.java`
- **Why**: Separates the internal notification creation logic (used by Kafka consumers) from the REST-facing NotificationService (used by controllers in S6-003). This follows the single responsibility principle.
- **Content**:

```java
package com.familyhobbies.notificationservice.service;

import com.familyhobbies.notificationservice.enums.NotificationCategory;

import java.util.Map;

/**
 * Internal service for creating notifications and triggering emails.
 * Used by Kafka consumers to create notifications with preference awareness.
 *
 * <p>Before creating a notification, this service checks the user's
 * {@code NotificationPreference} for the given category:
 * <ul>
 *   <li>If {@code in_app_enabled} is true, an IN_APP notification is created</li>
 *   <li>If {@code email_enabled} is true, an email is sent via {@link EmailService}</li>
 *   <li>If no preference exists, both channels default to enabled (opt-out model)</li>
 * </ul>
 */
public interface NotificationCreationService {

    /**
     * Creates a notification and optionally sends an email, respecting user preferences.
     *
     * @param userId    the target user's ID
     * @param email     the target user's email address (for sending email)
     * @param category  the notification category
     * @param title     the notification title (displayed in notification center)
     * @param message   the notification message body
     * @param variables template variables for email rendering
     */
    void createNotification(Long userId, String email, NotificationCategory category,
                            String title, String message, Map<String, Object> variables);
}
```

- **Verify**: `mvn compile -pl backend/notification-service` -> compiles

---

## Task 8 Detail: NotificationCreationServiceImpl

- **What**: Implementation that checks preferences, creates in-app notifications, and triggers email sending
- **Where**: `backend/notification-service/src/main/java/com/familyhobbies/notificationservice/service/impl/NotificationCreationServiceImpl.java`
- **Why**: Central orchestration point for all notification creation. Kafka consumers call this service, which handles the preference check, DB write, and email trigger in a single transactional flow.
- **Content**:

```java
package com.familyhobbies.notificationservice.service.impl;

import com.familyhobbies.notificationservice.entity.Notification;
import com.familyhobbies.notificationservice.entity.NotificationPreference;
import com.familyhobbies.notificationservice.enums.NotificationCategory;
import com.familyhobbies.notificationservice.enums.NotificationType;
import com.familyhobbies.notificationservice.repository.NotificationPreferenceRepository;
import com.familyhobbies.notificationservice.repository.NotificationRepository;
import com.familyhobbies.notificationservice.service.EmailService;
import com.familyhobbies.notificationservice.service.NotificationCreationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Creates notifications with preference awareness.
 *
 * <p>Preference logic (opt-out model):
 * <ul>
 *   <li>If no preference row exists for user+category: both channels enabled</li>
 *   <li>If preference exists: respect {@code emailEnabled} and {@code inAppEnabled} flags</li>
 * </ul>
 *
 * <p>Email failures are logged but do not roll back the in-app notification.
 * The notification record tracks whether the email was sent via {@code emailSent}
 * and {@code emailSentAt} fields.
 */
@Service
public class NotificationCreationServiceImpl implements NotificationCreationService {

    private static final Logger log = LoggerFactory.getLogger(
            NotificationCreationServiceImpl.class);

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final EmailService emailService;

    public NotificationCreationServiceImpl(
            NotificationRepository notificationRepository,
            NotificationPreferenceRepository preferenceRepository,
            EmailService emailService) {
        this.notificationRepository = notificationRepository;
        this.preferenceRepository = preferenceRepository;
        this.emailService = emailService;
    }

    @Override
    @Transactional
    public void createNotification(Long userId, String email,
                                   NotificationCategory category,
                                   String title, String message,
                                   Map<String, Object> variables) {
        Optional<NotificationPreference> prefOpt =
                preferenceRepository.findByUserIdAndCategory(userId, category);

        boolean emailEnabled = prefOpt.map(NotificationPreference::getEmailEnabled)
                .orElse(true);
        boolean inAppEnabled = prefOpt.map(NotificationPreference::getInAppEnabled)
                .orElse(true);

        log.debug("Creating notification for userId={} category={} emailEnabled={} "
                + "inAppEnabled={}", userId, category, emailEnabled, inAppEnabled);

        if (inAppEnabled) {
            Notification notification = Notification.builder()
                    .userId(userId)
                    .type(NotificationType.IN_APP)
                    .category(category)
                    .title(title)
                    .message(message)
                    .build();

            if (emailEnabled && email != null && !email.isBlank()) {
                try {
                    emailService.sendEmail(email, category, variables);
                    notification.setEmailSent(true);
                    notification.setEmailSentAt(Instant.now());
                    log.info("Email sent for userId={} category={}", userId, category);
                } catch (Exception ex) {
                    log.error("Failed to send email for userId={} category={}: {}",
                            userId, category, ex.getMessage(), ex);
                }
            }

            notificationRepository.save(notification);
            log.info("IN_APP notification created for userId={} category={}", userId, category);
        }

        if (!inAppEnabled && emailEnabled && email != null && !email.isBlank()) {
            try {
                emailService.sendEmail(email, category, variables);
                Notification emailRecord = Notification.builder()
                        .userId(userId)
                        .type(NotificationType.EMAIL)
                        .category(category)
                        .title(title)
                        .message(message)
                        .emailSent(true)
                        .emailSentAt(Instant.now())
                        .build();
                notificationRepository.save(emailRecord);
                log.info("EMAIL-only notification created for userId={} category={}",
                        userId, category);
            } catch (Exception ex) {
                log.error("Failed to send email-only for userId={} category={}: {}",
                        userId, category, ex.getMessage(), ex);
            }
        }

        if (!inAppEnabled && !emailEnabled) {
            log.info("Both channels disabled for userId={} category={}, skipping",
                    userId, category);
        }
    }
}
```

- **Verify**: `mvn compile -pl backend/notification-service` -> compiles; call `createNotification` -> DB row created, email sent (or skipped per preferences)

---

## Task 9 Detail: UserEventConsumer

- **What**: Kafka listener consuming `family-hobbies.user.registered` and `family-hobbies.user.deleted` events
- **Where**: `backend/notification-service/src/main/java/com/familyhobbies/notificationservice/listener/UserEventConsumer.java`
- **Why**: Handles two critical flows: (1) welcome notification + email on new user registration, and (2) RGPD-compliant deletion of all notification data when a user is deleted.
- **Content**:

```java
package com.familyhobbies.notificationservice.listener;

import com.familyhobbies.common.event.UserDeletedEvent;
import com.familyhobbies.common.event.UserRegisteredEvent;
import com.familyhobbies.notificationservice.enums.NotificationCategory;
import com.familyhobbies.notificationservice.repository.NotificationPreferenceRepository;
import com.familyhobbies.notificationservice.repository.NotificationRepository;
import com.familyhobbies.notificationservice.service.NotificationCreationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Consumes user lifecycle events from Kafka.
 *
 * <p>Handles:
 * <ul>
 *   <li>{@link UserRegisteredEvent} -- creates WELCOME notification + sends welcome email</li>
 *   <li>{@link UserDeletedEvent} -- RGPD cleanup: deletes all notifications and preferences</li>
 * </ul>
 */
@Component
public class UserEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(UserEventConsumer.class);

    private final NotificationCreationService notificationCreationService;
    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;

    public UserEventConsumer(NotificationCreationService notificationCreationService,
                             NotificationRepository notificationRepository,
                             NotificationPreferenceRepository preferenceRepository) {
        this.notificationCreationService = notificationCreationService;
        this.notificationRepository = notificationRepository;
        this.preferenceRepository = preferenceRepository;
    }

    /**
     * Handles new user registration: creates a WELCOME notification and sends
     * a welcome email with the user's first name.
     */
    @KafkaListener(
            topics = "family-hobbies.user.registered",
            groupId = "notification-service-group"
    )
    // Available UserRegisteredEvent fields: getUserId(), getEmail(), getFirstName(), getLastName()
    // Timestamp via DomainEvent base class: getOccurredAt()
    public void handleUserRegistered(UserRegisteredEvent event) {
        log.info("Received UserRegisteredEvent: userId={}, email={}, firstName={}",
                event.getUserId(), event.getEmail(), event.getFirstName());

        Map<String, Object> variables = Map.of(
                "firstName", event.getFirstName(),
                "lastName", event.getLastName(),
                "email", event.getEmail()
        );

        notificationCreationService.createNotification(
                event.getUserId(),
                event.getEmail(),
                NotificationCategory.WELCOME,
                "Bienvenue sur Family Hobbies Manager !",
                String.format("Bonjour %s, bienvenue sur Family Hobbies Manager ! "
                        + "Decouvrez les associations pres de chez vous et inscrivez "
                        + "toute votre famille.", event.getFirstName()),
                variables
        );
    }

    /**
     * Handles user deletion: RGPD-compliant cleanup of all notification data
     * for the deleted user.
     */
    @KafkaListener(
            topics = "family-hobbies.user.deleted",
            groupId = "notification-service-group"
    )
    // Available UserDeletedEvent fields: getUserId(), getDeletionType()
    // Timestamp via DomainEvent base class: getOccurredAt()
    // Note: getEmail(), getReason(), getDeletedAt() do NOT exist on this event
    @Transactional
    public void handleUserDeleted(UserDeletedEvent event) {
        log.info("Received UserDeletedEvent: userId={}, deletionType={}",
                event.getUserId(), event.getDeletionType());

        notificationRepository.deleteByUserId(event.getUserId());
        preferenceRepository.deleteByUserId(event.getUserId());

        log.info("RGPD cleanup completed: deleted all notifications and preferences "
                + "for userId={}", event.getUserId());
    }
}
```

- **Verify**: Publish `UserRegisteredEvent` to Kafka -> WELCOME notification in DB + email in MailHog. Publish `UserDeletedEvent` -> all user's notifications and preferences deleted.

---

## Task 10 Detail: SubscriptionEventConsumer

- **What**: Kafka listener consuming `family-hobbies.subscription.created` and `family-hobbies.subscription.cancelled` events
- **Where**: `backend/notification-service/src/main/java/com/familyhobbies/notificationservice/listener/SubscriptionEventConsumer.java`
- **Why**: Notifies users when their subscription is confirmed or cancelled. The subscription created event includes activity and season details needed for the email template.
- **Content**:

```java
package com.familyhobbies.notificationservice.listener;

import com.familyhobbies.common.event.SubscriptionCancelledEvent;
import com.familyhobbies.common.event.SubscriptionCreatedEvent;
import com.familyhobbies.notificationservice.enums.NotificationCategory;
import com.familyhobbies.notificationservice.service.NotificationCreationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Consumes subscription lifecycle events from Kafka.
 *
 * <p>Handles:
 * <ul>
 *   <li>{@link SubscriptionCreatedEvent} -- creates SUBSCRIPTION_CONFIRMED notification + email</li>
 *   <li>{@link SubscriptionCancelledEvent} -- creates SUBSCRIPTION_CANCELLED notification</li>
 * </ul>
 *
 * <p>Note: The subscription events carry {@code familyMemberId} and {@code familyId}
 * but not the user's email directly. The consumer uses {@code familyId} as the userId
 * for notification routing (the family admin receives all notifications).
 */
@Component
public class SubscriptionEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(
            SubscriptionEventConsumer.class);

    private final NotificationCreationService notificationCreationService;

    public SubscriptionEventConsumer(
            NotificationCreationService notificationCreationService) {
        this.notificationCreationService = notificationCreationService;
    }

    /**
     * Handles subscription creation: creates a SUBSCRIPTION_CONFIRMED notification
     * and sends a confirmation email with activity and season details.
     */
    @KafkaListener(
            topics = "family-hobbies.subscription.created",
            groupId = "notification-service-group"
    )
    public void handleSubscriptionCreated(SubscriptionCreatedEvent event) {
        log.info("Received SubscriptionCreatedEvent: subscriptionId={}, familyId={}, "
                        + "activityId={}, season={}",
                event.getSubscriptionId(), event.getFamilyId(),
                event.getActivityId(), event.getSeason());

        Map<String, Object> variables = new HashMap<>();
        variables.put("subscriptionId", event.getSubscriptionId());
        variables.put("familyMemberId", event.getFamilyMemberId());
        variables.put("activityId", event.getActivityId());
        variables.put("season", event.getSeason());
        variables.put("amount", event.getAmount());

        notificationCreationService.createNotification(
                event.getFamilyId(),
                null, // email resolved from user-service in a future enhancement
                NotificationCategory.SUBSCRIPTION_CONFIRMED,
                "Inscription confirmee",
                String.format("L'inscription pour la saison %s a ete confirmee. "
                        + "Montant : %s EUR.", event.getSeason(), event.getAmount()),
                variables
        );
    }

    /**
     * Handles subscription cancellation: creates a SUBSCRIPTION_CANCELLED notification.
     */
    @KafkaListener(
            topics = "family-hobbies.subscription.cancelled",
            groupId = "notification-service-group"
    )
    public void handleSubscriptionCancelled(SubscriptionCancelledEvent event) {
        log.info("Received SubscriptionCancelledEvent: subscriptionId={}, cancellationReason={}",
                event.getSubscriptionId(), event.getCancellationReason());

        Map<String, Object> variables = new HashMap<>();
        variables.put("subscriptionId", event.getSubscriptionId());
        variables.put("cancellationReason", event.getCancellationReason());

        notificationCreationService.createNotification(
                event.getUserId(),
                null,
                NotificationCategory.SUBSCRIPTION_CANCELLED,
                "Inscription annulee",
                String.format("L'inscription #%d a ete annulee. Raison : %s.",
                        event.getSubscriptionId(), event.getCancellationReason()),
                variables
        );
    }
}
```

- **Verify**: Publish `SubscriptionCreatedEvent` to Kafka -> SUBSCRIPTION_CONFIRMED notification in DB. Publish `SubscriptionCancelledEvent` -> SUBSCRIPTION_CANCELLED notification in DB.

---

## Task 11 Detail: PaymentEventConsumer

- **What**: Kafka listener consuming `family-hobbies.payment.completed` and `family-hobbies.payment.failed` events
- **Where**: `backend/notification-service/src/main/java/com/familyhobbies/notificationservice/listener/PaymentEventConsumer.java`
- **Why**: Notifies users about payment outcomes. Success triggers a receipt email; failure triggers an email with retry instructions.
- **Content**:

```java
package com.familyhobbies.notificationservice.listener;

import com.familyhobbies.common.event.PaymentCompletedEvent;
import com.familyhobbies.common.event.PaymentFailedEvent;
import com.familyhobbies.notificationservice.enums.NotificationCategory;
import com.familyhobbies.notificationservice.service.NotificationCreationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Consumes payment lifecycle events from Kafka.
 *
 * <p>Handles:
 * <ul>
 *   <li>{@link PaymentCompletedEvent} -- creates PAYMENT_SUCCESS notification + receipt email</li>
 *   <li>{@link PaymentFailedEvent} -- creates PAYMENT_FAILED notification + failure email</li>
 * </ul>
 */
@Component
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);

    private final NotificationCreationService notificationCreationService;

    public PaymentEventConsumer(
            NotificationCreationService notificationCreationService) {
        this.notificationCreationService = notificationCreationService;
    }

    /**
     * Handles successful payment: creates a PAYMENT_SUCCESS notification
     * and sends a receipt email with amount, currency, and payment method.
     */
    @KafkaListener(
            topics = "family-hobbies.payment.completed",
            groupId = "notification-service-group"
    )
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        log.info("Received PaymentCompletedEvent: paymentId={}, familyId={}, "
                        + "amount={} {}, method={}",
                event.getPaymentId(), event.getFamilyId(),
                event.getAmount(), event.getCurrency(), event.getPaymentMethod());

        Map<String, Object> variables = new HashMap<>();
        variables.put("paymentId", event.getPaymentId());
        variables.put("subscriptionId", event.getSubscriptionId());
        variables.put("amount", event.getAmount());
        variables.put("currency", event.getCurrency());
        variables.put("paymentMethod", event.getPaymentMethod());
        variables.put("paidAt", event.getPaidAt());

        notificationCreationService.createNotification(
                event.getFamilyId(),
                null, // email resolved from user-service in a future enhancement
                NotificationCategory.PAYMENT_SUCCESS,
                "Paiement confirme",
                String.format("Votre paiement de %s %s a ete traite avec succes. "
                                + "Reference : #%d.",
                        event.getAmount(), event.getCurrency(), event.getPaymentId()),
                variables
        );
    }

    /**
     * Handles failed payment: creates a PAYMENT_FAILED notification
     * and sends an email with the failure reason and retry instructions.
     */
    @KafkaListener(
            topics = "family-hobbies.payment.failed",
            groupId = "notification-service-group"
    )
    public void handlePaymentFailed(PaymentFailedEvent event) {
        log.info("Received PaymentFailedEvent: paymentId={}, familyId={}, reason={}",
                event.getPaymentId(), event.getFamilyId(), event.getFailureReason());

        Map<String, Object> variables = new HashMap<>();
        variables.put("paymentId", event.getPaymentId());
        variables.put("subscriptionId", event.getSubscriptionId());
        variables.put("amount", event.getAmount());
        variables.put("failureReason", event.getFailureReason());
        variables.put("failedAt", event.getFailedAt());

        notificationCreationService.createNotification(
                event.getFamilyId(),
                null,
                NotificationCategory.PAYMENT_FAILED,
                "Echec de paiement",
                String.format("Le paiement de %s EUR a echoue. Raison : %s. "
                                + "Veuillez reessayer.",
                        event.getAmount(), event.getFailureReason()),
                variables
        );
    }
}
```

- **Verify**: Publish `PaymentCompletedEvent` to Kafka -> PAYMENT_SUCCESS notification in DB. Publish `PaymentFailedEvent` -> PAYMENT_FAILED notification in DB.

---

## Task 12 Detail: AttendanceEventConsumer

- **What**: Kafka listener consuming `family-hobbies.attendance.marked` events
- **Where**: `backend/notification-service/src/main/java/com/familyhobbies/notificationservice/listener/AttendanceEventConsumer.java`
- **Why**: Logs attendance events for future absence threshold notifications. Currently only logs; the threshold logic will be implemented in a future sprint.
- **Content**:

```java
package com.familyhobbies.notificationservice.listener;

import com.familyhobbies.common.event.AttendanceMarkedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes attendance events from Kafka.
 *
 * <p>Current behavior: logs the event for monitoring purposes.
 *
 * <p>Future enhancement: track absence counts per member per activity.
 * When the absence threshold is exceeded (e.g., 3 consecutive absences),
 * create an ATTENDANCE_REMINDER notification and send an email.
 */
@Component
public class AttendanceEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AttendanceEventConsumer.class);

    /**
     * Handles attendance marked events. Currently logs the event.
     * Future: absence threshold notifications.
     */
    @KafkaListener(
            topics = "family-hobbies.attendance.marked",
            groupId = "notification-service-group"
    )
    public void handleAttendanceMarked(AttendanceMarkedEvent event) {
        log.info("Received AttendanceMarkedEvent: attendanceId={}, sessionId={}, "
                        + "memberId={}, status={}, sessionDate={}",
                event.getAttendanceId(), event.getSessionId(),
                event.getFamilyMemberId(), event.getStatus(),
                event.getSessionDate());

        // Future: Track absence count per member per activity.
        // If absence threshold exceeded, create ATTENDANCE_REMINDER notification.
        // For now, only log the event.
    }
}
```

- **Verify**: Publish `AttendanceMarkedEvent` to Kafka -> log entry with attendance details, no exceptions

---

## Failing Tests (TDD Contract)

Tests are in the companion file: **[S6-002 Kafka Consumers Tests](./S6-002-kafka-consumers-tests.md)**

The companion file contains:
- `UserEventConsumerTest` -- 4 tests (welcome notification, RGPD cleanup, event field mapping, null handling)
- `SubscriptionEventConsumerTest` -- 3 tests (subscription confirmed, cancelled, field mapping)
- `PaymentEventConsumerTest` -- 3 tests (payment success, failure, field mapping)
- `AttendanceEventConsumerTest` -- 2 tests (event received, logging verification)
- `NotificationCreationServiceImplTest` -- 6 tests (preference check, email enabled, email disabled, in-app disabled, both disabled, email failure)
- `EmailServiceImplTest` -- 5 tests (successful send, template not found, rendering, from address, MIME type)

---

## Acceptance Criteria Checklist

- [ ] KafkaConsumerConfig matches architecture specification exactly
- [ ] Error handler configured with ExponentialBackOff (1000ms initial, 2x multiplier, 30s max, 3 attempts)
- [ ] Dead-letter topic (DLT) configured for failed messages
- [ ] `UserEventConsumer` handles UserRegisteredEvent (WELCOME notification + email)
- [ ] `UserEventConsumer` handles UserDeletedEvent (RGPD cleanup of all notifications + preferences)
- [ ] `SubscriptionEventConsumer` handles SubscriptionCreatedEvent (SUBSCRIPTION_CONFIRMED notification)
- [ ] `SubscriptionEventConsumer` handles SubscriptionCancelledEvent (SUBSCRIPTION_CANCELLED notification)
- [ ] `PaymentEventConsumer` handles PaymentCompletedEvent (PAYMENT_SUCCESS notification + receipt email)
- [ ] `PaymentEventConsumer` handles PaymentFailedEvent (PAYMENT_FAILED notification + failure email)
- [ ] `AttendanceEventConsumer` handles AttendanceMarkedEvent (logs event, future threshold logic)
- [ ] `NotificationCreationServiceImpl` checks preferences before creating notifications
- [ ] `EmailServiceImpl` resolves templates from DB, renders with Thymeleaf, sends via JavaMailSender
- [ ] Email failures logged but do not block notification creation
- [ ] All event field names match common module event schemas exactly
- [ ] All 23 JUnit 5 tests pass green
