package com.familyhobbies.errorhandling.exception.server;

import com.familyhobbies.errorhandling.dto.ErrorCode;
import com.familyhobbies.errorhandling.exception.BaseException;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

class ServerExceptionTest {

    @Test
    @DisplayName("should create InternalServerException with 500 status")
    void should_createInternalServerException_when_instantiated() {
        InternalServerException ex = new InternalServerException("Unexpected error occurred");
        assertThat(ex.getMessage()).isEqualTo("Unexpected error occurred");
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);
        assertThat(ex.getHttpStatus()).isEqualTo(500);
        assertThat(ex).isInstanceOf(BaseException.class);
    }

    @Test
    @DisplayName("should create BadGatewayException with 502 status")
    void should_createBadGatewayException_when_instantiated() {
        BadGatewayException ex = new BadGatewayException("Upstream service returned invalid response");
        assertThat(ex.getMessage()).isEqualTo("Upstream service returned invalid response");
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_GATEWAY);
        assertThat(ex.getHttpStatus()).isEqualTo(502);
    }

    @Test
    @DisplayName("should create ServiceUnavailableException with 503 status")
    void should_createServiceUnavailableException_when_instantiated() {
        ServiceUnavailableException ex = new ServiceUnavailableException("Service is temporarily unavailable");
        assertThat(ex.getMessage()).isEqualTo("Service is temporarily unavailable");
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
        assertThat(ex.getHttpStatus()).isEqualTo(503);
    }

    @Test
    @DisplayName("should create GatewayTimeoutException with 504 status")
    void should_createGatewayTimeoutException_when_instantiated() {
        GatewayTimeoutException ex = new GatewayTimeoutException("Upstream service timed out");
        assertThat(ex.getMessage()).isEqualTo("Upstream service timed out");
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.GATEWAY_TIMEOUT);
        assertThat(ex.getHttpStatus()).isEqualTo(504);
    }

    @Test
    @DisplayName("should preserve cause in server exceptions")
    void should_preserveCause_when_causeProvided() {
        Exception cause = new java.net.SocketTimeoutException("Connection timed out");
        GatewayTimeoutException ex = new GatewayTimeoutException("Upstream timed out", cause);
        assertThat(ex.getCause()).isEqualTo(cause);
    }
}
