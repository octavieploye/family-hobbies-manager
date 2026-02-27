package com.familyhobbies.apigateway.config;

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
 * See docs/architecture/05-security-architecture.md section 10 for the
 * full specification and header descriptions.
 */
@Configuration
public class SecurityHeadersConfig {

    @Bean
    public WebFilter securityHeadersFilter() {
        return (exchange, chain) -> {
            // Prevent MIME-type sniffing -- browser must use declared Content-Type
            exchange.getResponse().getHeaders().add(
                "X-Content-Type-Options", "nosniff");

            // Prevent embedding in iframes -- mitigates clickjacking
            exchange.getResponse().getHeaders().add(
                "X-Frame-Options", "DENY");

            // Disable legacy XSS auditor -- modern CSP is more effective
            // and the legacy auditor can itself introduce vulnerabilities
            exchange.getResponse().getHeaders().add(
                "X-XSS-Protection", "0");

            // Force HTTPS for 1 year, including subdomains, eligible for preload
            exchange.getResponse().getHeaders().add(
                "Strict-Transport-Security",
                "max-age=31536000; includeSubDomains; preload");

            // Content Security Policy -- restrict allowed sources for all content types
            exchange.getResponse().getHeaders().add(
                "Content-Security-Policy",
                "default-src 'self'; "
                + "script-src 'self'; "
                + "style-src 'self' 'unsafe-inline'; "
                + "img-src 'self' data:; "
                + "font-src 'self'; "
                + "connect-src 'self' https://api.helloasso.com; "
                + "frame-ancestors 'none'; "
                + "base-uri 'self'; "
                + "form-action 'self'");

            // Limit referrer information sent to external sites
            exchange.getResponse().getHeaders().add(
                "Referrer-Policy", "strict-origin-when-cross-origin");

            // Disable browser APIs the application does not need
            exchange.getResponse().getHeaders().add(
                "Permissions-Policy", "camera=(), microphone=(), geolocation=()");

            return chain.filter(exchange);
        };
    }
}
