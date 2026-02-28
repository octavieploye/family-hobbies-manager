package com.familyhobbies.common.logging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link LoggingConstants}.
 * Verifies all MDC key constants and header names are correctly defined.
 */
@DisplayName("LoggingConstants")
class LoggingConstantsTest {

    @Test
    @DisplayName("should define traceId MDC key")
    void shouldDefineTraceIdMdcKey() {
        assertThat(LoggingConstants.MDC_TRACE_ID).isEqualTo("traceId");
    }

    @Test
    @DisplayName("should define spanId MDC key")
    void shouldDefineSpanIdMdcKey() {
        assertThat(LoggingConstants.MDC_SPAN_ID).isEqualTo("spanId");
    }

    @Test
    @DisplayName("should define userId MDC key")
    void shouldDefineUserIdMdcKey() {
        assertThat(LoggingConstants.MDC_USER_ID).isEqualTo("userId");
    }

    @Test
    @DisplayName("should define serviceName MDC key")
    void shouldDefineServiceNameMdcKey() {
        assertThat(LoggingConstants.MDC_SERVICE_NAME).isEqualTo("serviceName");
    }

    @Test
    @DisplayName("should define X-Request-Id header")
    void shouldDefineRequestIdHeader() {
        assertThat(LoggingConstants.HEADER_REQUEST_ID).isEqualTo("X-Request-Id");
    }

    @Test
    @DisplayName("should define X-User-Id header")
    void shouldDefineUserIdHeader() {
        assertThat(LoggingConstants.HEADER_USER_ID).isEqualTo("X-User-Id");
    }

    @Test
    @DisplayName("should define X-User-Roles header")
    void shouldDefineUserRolesHeader() {
        assertThat(LoggingConstants.HEADER_USER_ROLES).isEqualTo("X-User-Roles");
    }

    @Test
    @DisplayName("should not be instantiable")
    void shouldNotBeInstantiable() throws NoSuchMethodException {
        Constructor<LoggingConstants> constructor =
                LoggingConstants.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThatThrownBy(constructor::newInstance)
                .isInstanceOf(InvocationTargetException.class);
    }

    @Test
    @DisplayName("should use camelCase for all MDC keys")
    void shouldUseCamelCaseForAllMdcKeys() {
        assertThat(LoggingConstants.MDC_TRACE_ID).matches("[a-zA-Z]+");
        assertThat(LoggingConstants.MDC_SPAN_ID).matches("[a-zA-Z]+");
        assertThat(LoggingConstants.MDC_USER_ID).matches("[a-zA-Z]+");
        assertThat(LoggingConstants.MDC_SERVICE_NAME).matches("[a-zA-Z]+");
    }
}
