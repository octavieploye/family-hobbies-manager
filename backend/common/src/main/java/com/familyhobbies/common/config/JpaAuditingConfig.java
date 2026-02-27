package com.familyhobbies.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables JPA auditing so that {@code @CreatedDate}, {@code @LastModifiedDate},
 * {@code @CreatedBy}, and {@code @LastModifiedBy} annotations in
 * {@link com.familyhobbies.common.audit.BaseAuditEntity} are automatically populated.
 *
 * <p>This configuration is auto-picked-up by any service that depends on the common module,
 * thanks to Spring Boot component scanning.</p>
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
