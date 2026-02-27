package com.familyhobbies.common.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Shared HelloAsso configuration properties.
 * Used by both association-service and payment-service.
 * Bound to the {@code helloasso.*} YAML namespace.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "helloasso")
public class HelloAssoProperties {

    @NotBlank
    private String baseUrl;

    @NotBlank
    private String clientId;

    @NotBlank
    private String clientSecret;

    @NotBlank
    private String tokenUrl;

    private String webhookSecret;

    @Positive
    private int connectTimeout = 5000;

    @Positive
    private int readTimeout = 10000;

    private Sync sync = new Sync();

    @Getter
    @Setter
    public static class Sync {
        private List<String> cities = List.of("Paris", "Lyon", "Marseille", "Toulouse", "Bordeaux", "Nantes");

        @Positive
        private int pageSize = 20;
    }
}
