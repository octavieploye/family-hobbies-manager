package com.familyhobbies.errorhandling.handler;

import com.familyhobbies.errorhandling.dto.ErrorCode;
import com.familyhobbies.errorhandling.dto.ErrorResponse;
import com.familyhobbies.errorhandling.exception.web.*;
import com.familyhobbies.errorhandling.exception.server.*;
import com.familyhobbies.errorhandling.exception.container.*;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @InjectMocks
    private GlobalExceptionHandler handler;

    @Mock
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        when(request.getRequestURI()).thenReturn("/api/v1/test");
    }

    // --- Web Exceptions (4xx) ---

    @Test
    @DisplayName("should return 400 when BadRequestException thrown")
    void should_return400_when_badRequestExceptionThrown() {
        BadRequestException ex = new BadRequestException("Invalid input");
        ResponseEntity<ErrorResponse> response = handler.handleBadRequest(ex, request);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(response.getBody().getMessage()).isEqualTo("Invalid input");
        assertThat(response.getBody().getPath()).isEqualTo("/api/v1/test");
    }

    @Test
    @DisplayName("should return 401 when UnauthorizedException thrown")
    void should_return401_when_unauthorizedExceptionThrown() {
        UnauthorizedException ex = new UnauthorizedException("Invalid token");
        ResponseEntity<ErrorResponse> response = handler.handleUnauthorized(ex, request);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody().getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("should return 403 when ForbiddenException thrown")
    void should_return403_when_forbiddenExceptionThrown() {
        ForbiddenException ex = new ForbiddenException("Access denied");
        ResponseEntity<ErrorResponse> response = handler.handleForbidden(ex, request);
        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody().getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("should return 404 when ResourceNotFoundException thrown")
    void should_return404_when_resourceNotFoundExceptionThrown() {
        ResourceNotFoundException ex = ResourceNotFoundException.of("User", 42L);
        ResponseEntity<ErrorResponse> response = handler.handleNotFound(ex, request);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(response.getBody().getMessage()).contains("User");
    }

    @Test
    @DisplayName("should return 409 when ConflictException thrown")
    void should_return409_when_conflictExceptionThrown() {
        ConflictException ex = new ConflictException("Email already exists");
        ResponseEntity<ErrorResponse> response = handler.handleConflict(ex, request);
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().getErrorCode()).isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("should return 422 when UnprocessableEntityException thrown")
    void should_return422_when_unprocessableEntityExceptionThrown() {
        UnprocessableEntityException ex = new UnprocessableEntityException("Business rule violated");
        ResponseEntity<ErrorResponse> response = handler.handleUnprocessableEntity(ex, request);
        assertThat(response.getStatusCode().value()).isEqualTo(422);
    }

    @Test
    @DisplayName("should return 429 when TooManyRequestsException thrown")
    void should_return429_when_tooManyRequestsExceptionThrown() {
        TooManyRequestsException ex = new TooManyRequestsException("Rate limit exceeded");
        ResponseEntity<ErrorResponse> response = handler.handleTooManyRequests(ex, request);
        assertThat(response.getStatusCode().value()).isEqualTo(429);
    }

    // --- Server Exceptions (5xx) ---

    @Test
    @DisplayName("should return 500 when InternalServerException thrown")
    void should_return500_when_internalServerExceptionThrown() {
        InternalServerException ex = new InternalServerException("Unexpected error");
        ResponseEntity<ErrorResponse> response = handler.handleInternalServer(ex, request);
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("should return 502 when BadGatewayException thrown")
    void should_return502_when_badGatewayExceptionThrown() {
        BadGatewayException ex = new BadGatewayException("Upstream error");
        ResponseEntity<ErrorResponse> response = handler.handleBadGateway(ex, request);
        assertThat(response.getStatusCode().value()).isEqualTo(502);
    }

    @Test
    @DisplayName("should return 503 when ServiceUnavailableException thrown")
    void should_return503_when_serviceUnavailableExceptionThrown() {
        ServiceUnavailableException ex = new ServiceUnavailableException("Service down");
        ResponseEntity<ErrorResponse> response = handler.handleServiceUnavailable(ex, request);
        assertThat(response.getStatusCode().value()).isEqualTo(503);
    }

    @Test
    @DisplayName("should return 504 when GatewayTimeoutException thrown")
    void should_return504_when_gatewayTimeoutExceptionThrown() {
        GatewayTimeoutException ex = new GatewayTimeoutException("Timeout");
        ResponseEntity<ErrorResponse> response = handler.handleGatewayTimeout(ex, request);
        assertThat(response.getStatusCode().value()).isEqualTo(504);
    }

    // --- Container Exceptions ---

    @Test
    @DisplayName("should return 503 when CircuitBreakerOpenException thrown")
    void should_return503_when_circuitBreakerOpenExceptionThrown() {
        CircuitBreakerOpenException ex = CircuitBreakerOpenException.forService("payment-service");
        ResponseEntity<ErrorResponse> response = handler.handleCircuitBreakerOpen(ex, request);
        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody().getErrorCode()).isEqualTo(ErrorCode.CIRCUIT_BREAKER_OPEN);
    }

    @Test
    @DisplayName("should return 503 when KafkaPublishException thrown")
    void should_return503_when_kafkaPublishExceptionThrown() {
        KafkaPublishException ex = KafkaPublishException.forTopic("family-hobbies.user.registered", new RuntimeException("fail"));
        ResponseEntity<ErrorResponse> response = handler.handleKafkaPublish(ex, request);
        assertThat(response.getStatusCode().value()).isEqualTo(503);
    }

    @Test
    @DisplayName("should return 503 when ServiceDiscoveryException thrown")
    void should_return503_when_serviceDiscoveryExceptionThrown() {
        ServiceDiscoveryException ex = new ServiceDiscoveryException("user-service not found in Eureka");
        ResponseEntity<ErrorResponse> response = handler.handleServiceDiscovery(ex, request);
        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody().getErrorCode()).isEqualTo(ErrorCode.SERVICE_DISCOVERY_FAILURE);
        assertThat(response.getBody().getMessage()).contains("user-service");
    }

    @Test
    @DisplayName("should return 502 when ExternalApiException thrown")
    void should_return502_when_externalApiExceptionThrown() {
        ExternalApiException ex = ExternalApiException.forApi("HelloAsso", 500, "Server Error");
        ResponseEntity<ErrorResponse> response = handler.handleExternalApi(ex, request);
        assertThat(response.getStatusCode().value()).isEqualTo(502);
        assertThat(response.getBody().getErrorCode()).isEqualTo(ErrorCode.EXTERNAL_API_FAILURE);
    }

    // --- Validation (MethodArgumentNotValidException) ---

    @Test
    @DisplayName("should return 400 with field errors when MethodArgumentNotValidException thrown")
    void should_returnFieldErrors_when_methodArgumentNotValid() {
        // given
        FieldError fieldError1 = new FieldError("userDto", "email", "must not be blank");
        FieldError fieldError2 = new FieldError("userDto", "password", "size must be between 8 and 100");

        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError1, fieldError2));

        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(ex.getMessage()).thenReturn("Validation failed");

        // when
        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex, request);

        // then
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(response.getBody().getMessage()).isEqualTo("Request validation failed");
        assertThat(response.getBody().getPath()).isEqualTo("/api/v1/test");
        assertThat(response.getBody().getTimestamp()).isNotNull();

        // Verify field error details
        assertThat(response.getBody().getDetails()).hasSize(2);
        assertThat(response.getBody().getDetails().get(0).getField()).isEqualTo("email");
        assertThat(response.getBody().getDetails().get(0).getMessage()).isEqualTo("must not be blank");
        assertThat(response.getBody().getDetails().get(1).getField()).isEqualTo("password");
        assertThat(response.getBody().getDetails().get(1).getMessage()).isEqualTo("size must be between 8 and 100");
    }

    // --- Fallback ---

    @Test
    @DisplayName("should return 500 when unexpected Exception thrown")
    void should_return500_when_unexpectedExceptionThrown() {
        Exception ex = new NullPointerException("unexpected");
        ResponseEntity<ErrorResponse> response = handler.handleGenericException(ex, request);
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getMessage()).isEqualTo("An unexpected error occurred");
    }

    // --- All responses should have timestamp and path ---

    @Test
    @DisplayName("should always include timestamp and path in error response")
    void should_includeTimestampAndPath_when_anyExceptionHandled() {
        BadRequestException ex = new BadRequestException("test");
        ResponseEntity<ErrorResponse> response = handler.handleBadRequest(ex, request);
        assertThat(response.getBody().getTimestamp()).isNotNull();
        assertThat(response.getBody().getPath()).isEqualTo("/api/v1/test");
    }

    // --- Correlation ID propagation ---

    @Test
    @DisplayName("should include correlationId when X-Correlation-Id header is present")
    void should_includeCorrelationId_when_headerPresent() {
        when(request.getHeader("X-Correlation-Id")).thenReturn("trace-abc-123");

        BadRequestException ex = new BadRequestException("Invalid input");
        ResponseEntity<ErrorResponse> response = handler.handleBadRequest(ex, request);

        assertThat(response.getBody().getCorrelationId()).isEqualTo("trace-abc-123");
    }

    @Test
    @DisplayName("should omit correlationId when X-Correlation-Id header is absent")
    void should_omitCorrelationId_when_headerAbsent() {
        // request.getHeader("X-Correlation-Id") returns null by default (Mockito mock)

        BadRequestException ex = new BadRequestException("Invalid input");
        ResponseEntity<ErrorResponse> response = handler.handleBadRequest(ex, request);

        assertThat(response.getBody().getCorrelationId()).isNull();
    }

    @Test
    @DisplayName("should propagate correlationId in generic exception handler")
    void should_propagateCorrelationId_when_genericExceptionHandled() {
        when(request.getHeader("X-Correlation-Id")).thenReturn("trace-xyz-789");

        Exception ex = new NullPointerException("unexpected");
        ResponseEntity<ErrorResponse> response = handler.handleGenericException(ex, request);

        assertThat(response.getBody().getCorrelationId()).isEqualTo("trace-xyz-789");
    }
}
