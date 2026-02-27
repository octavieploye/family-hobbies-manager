package com.familyhobbies.notificationservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for notification defaults.
 */
@Component
@ConfigurationProperties(prefix = "notification")
@Getter
@Setter
public class NotificationProperties {

    private String fromEmail;
    private String fromName;
}
