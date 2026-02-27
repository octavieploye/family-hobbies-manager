package com.familyhobbies.apigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.WebFilter;

/**
 * Adds HTTP security headers to every response returned by the API Gateway.
 *
 * These headers are set at the gateway level so that downstream services
 * do not need to configure them individually. All responses pass through
 * the gateway, so all responses get these headers.
 *
 * Header values are externalized in application.yml under security.headers.*
 * and helloasso.base-url so that they can be overridden per environment
 * without code changes (M-015, M-016).
 *
 * See docs/architecture/05-security-architecture.md section 10 for the
 * full specification and header descriptions.
 */
@Configuration
public class SecurityHeadersConfig {

    @Value("${security.headers.content-type-options:nosniff}")
    private String contentTypeOptions;

    @Value("${security.headers.frame-options:DENY}")
    private String frameOptions;

    @Value("${security.headers.xss-protection:0}")
    private String xssProtection;

    @Value("${security.headers.strict-transport-security:max-age=31536000; includeSubDomains; preload}")
    private String strictTransportSecurity;

    @Value("${security.headers.referrer-policy:strict-origin-when-cross-origin}")
    private String referrerPolicy;

    @Value("${security.headers.permissions-policy:camera=(), microphone=(), geolocation=()}")
    private String permissionsPolicy;

    @Value("${helloasso.base-url:https://api.helloasso.com}")
    private String helloassoBaseUrl;

    @Bean
    public WebFilter securityHeadersFilter() {
        return (exchange, chain) -> {
            // Prevent MIME-type sniffing -- browser must use declared Content-Type
            exchange.getResponse().getHeaders().add(
                "X-Content-Type-Options", contentTypeOptions);

            // Prevent embedding in iframes -- mitigates clickjacking
            exchange.getResponse().getHeaders().add(
                "X-Frame-Options", frameOptions);

            // Disable legacy XSS auditor -- modern CSP is more effective
            // and the legacy auditor can itself introduce vulnerabilities
            exchange.getResponse().getHeaders().add(
                "X-XSS-Protection", xssProtection);

            // Force HTTPS for 1 year, including subdomains, eligible for preload
            exchange.getResponse().getHeaders().add(
                "Strict-Transport-Security", strictTransportSecurity);

            // Content Security Policy -- restrict allowed sources for all content types
            // M-015: HelloAsso URL is injected from config, supports sandbox and production
            exchange.getResponse().getHeaders().add(
                "Content-Security-Policy",
                "default-src 'self'; "
                + "script-src 'self'; "
                + "style-src 'self' 'unsafe-inline'; "
                + "img-src 'self' data:; "
                + "font-src 'self'; "
                + "connect-src 'self' " + helloassoBaseUrl + "; "
                + "frame-ancestors 'none'; "
                + "base-uri 'self'; "
                + "form-action 'self'");

            // Limit referrer information sent to external sites
            exchange.getResponse().getHeaders().add(
                "Referrer-Policy", referrerPolicy);

            // Disable browser APIs the application does not need
            exchange.getResponse().getHeaders().add(
                "Permissions-Policy", permissionsPolicy);

            return chain.filter(exchange);
        };
    }
}
