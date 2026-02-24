package com.familyhobbies.errorhandling.handler;

import com.familyhobbies.errorhandling.dto.ErrorCode;
import com.familyhobbies.errorhandling.dto.ErrorResponse;
import com.familyhobbies.errorhandling.exception.BaseException;
import com.familyhobbies.errorhandling.exception.container.CircuitBreakerOpenException;
import com.familyhobbies.errorhandling.exception.container.DatabaseConnectionException;
import com.familyhobbies.errorhandling.exception.container.ExternalApiException;
import com.familyhobbies.errorhandling.exception.container.KafkaPublishException;
import com.familyhobbies.errorhandling.exception.container.ServiceDiscoveryException;
import com.familyhobbies.errorhandling.exception.server.BadGatewayException;
import com.familyhobbies.errorhandling.exception.server.GatewayTimeoutException;
import com.familyhobbies.errorhandling.exception.server.InternalServerException;
import com.familyhobbies.errorhandling.exception.server.ServiceUnavailableException;
import com.familyhobbies.errorhandling.exception.web.BadRequestException;
import com.familyhobbies.errorhandling.exception.web.ConflictException;
import com.familyhobbies.errorhandling.exception.web.ForbiddenException;
import com.familyhobbies.errorhandling.exception.web.ResourceNotFoundException;
import com.familyhobbies.errorhandling.exception.web.TooManyRequestsException;
import com.familyhobbies.errorhandling.exception.web.UnauthorizedException;
import com.familyhobbies.errorhandling.exception.web.UnprocessableEntityException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Global exception handler that converts all known exceptions into a consistent
 * {@link ErrorResponse} JSON body with the appropriate HTTP status code.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ── Web (4xx) ────────────────────────────────────────────────────────────

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex,
                                                          HttpServletRequest request) {
        return buildResponse(ex, request);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException ex,
                                                            HttpServletRequest request) {
        return buildResponse(ex, request);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException ex,
                                                         HttpServletRequest request) {
        return buildResponse(ex, request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex,
                                                        HttpServletRequest request) {
        return buildResponse(ex, request);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex,
                                                        HttpServletRequest request) {
        return buildResponse(ex, request);
    }

    @ExceptionHandler(UnprocessableEntityException.class)
    public ResponseEntity<ErrorResponse> handleUnprocessableEntity(UnprocessableEntityException ex,
                                                                   HttpServletRequest request) {
        return buildResponse(ex, request);
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ErrorResponse> handleTooManyRequests(TooManyRequestsException ex,
                                                               HttpServletRequest request) {
        return buildResponse(ex, request);
    }

    // ── Server (5xx) ─────────────────────────────────────────────────────────

    @ExceptionHandler(InternalServerException.class)
    public ResponseEntity<ErrorResponse> handleInternalServer(InternalServerException ex,
                                                              HttpServletRequest request) {
        return buildResponse(ex, request);
    }

    @ExceptionHandler(BadGatewayException.class)
    public ResponseEntity<ErrorResponse> handleBadGateway(BadGatewayException ex,
                                                          HttpServletRequest request) {
        return buildResponse(ex, request);
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleServiceUnavailable(ServiceUnavailableException ex,
                                                                  HttpServletRequest request) {
        return buildResponse(ex, request);
    }

    @ExceptionHandler(GatewayTimeoutException.class)
    public ResponseEntity<ErrorResponse> handleGatewayTimeout(GatewayTimeoutException ex,
                                                              HttpServletRequest request) {
        return buildResponse(ex, request);
    }

    // ── Container / Infrastructure ───────────────────────────────────────────

    @ExceptionHandler(CircuitBreakerOpenException.class)
    public ResponseEntity<ErrorResponse> handleCircuitBreakerOpen(CircuitBreakerOpenException ex,
                                                                  HttpServletRequest request) {
        return buildResponse(ex, request);
    }

    @ExceptionHandler(KafkaPublishException.class)
    public ResponseEntity<ErrorResponse> handleKafkaPublish(KafkaPublishException ex,
                                                            HttpServletRequest request) {
        return buildResponse(ex, request);
    }

    @ExceptionHandler(DatabaseConnectionException.class)
    public ResponseEntity<ErrorResponse> handleDatabaseConnection(DatabaseConnectionException ex,
                                                                  HttpServletRequest request) {
        return buildResponse(ex, request);
    }

    @ExceptionHandler(ExternalApiException.class)
    public ResponseEntity<ErrorResponse> handleExternalApi(ExternalApiException ex,
                                                           HttpServletRequest request) {
        return buildResponse(ex, request);
    }

    // ── Spring Validation ────────────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                          HttpServletRequest request) {
        log.warn("Validation failed on [{}] {}: {}", request.getMethod(),
                request.getRequestURI(), ex.getMessage());

        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();

        ErrorResponse body = ErrorResponse.builder()
                .timestamp(java.time.Instant.now())
                .status(400)
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message("Request validation failed")
                .path(request.getRequestURI())
                .errorCode(ErrorCode.VALIDATION_FAILED)
                .details(fieldErrors)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // ── Fallback ─────────────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex,
                                                                HttpServletRequest request) {
        log.error("Unhandled exception on [{}] {}: {}", request.getMethod(),
                request.getRequestURI(), ex.getMessage(), ex);

        ErrorResponse body = ErrorResponse.of(
                500,
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "An unexpected error occurred",
                request.getRequestURI(),
                ErrorCode.INTERNAL_SERVER_ERROR
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    // ── Private helper ───────────────────────────────────────────────────────

    private ResponseEntity<ErrorResponse> buildResponse(BaseException ex,
                                                        HttpServletRequest request) {
        int status = ex.getHttpStatus();

        if (status >= 500) {
            log.error("Server error on [{}] {}: {}", request.getMethod(),
                    request.getRequestURI(), ex.getMessage(), ex);
        } else {
            log.warn("Client error on [{}] {}: {}", request.getMethod(),
                    request.getRequestURI(), ex.getMessage());
        }

        HttpStatus httpStatus = HttpStatus.resolve(status);
        String reasonPhrase = (httpStatus != null)
                ? httpStatus.getReasonPhrase()
                : "Unknown Status";

        ErrorResponse body = ErrorResponse.of(
                status,
                reasonPhrase,
                ex.getMessage(),
                request.getRequestURI(),
                ex.getErrorCode()
        );

        return ResponseEntity.status(status).body(body);
    }
}
