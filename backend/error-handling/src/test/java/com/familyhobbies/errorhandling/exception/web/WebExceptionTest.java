package com.familyhobbies.errorhandling.exception.web;

import com.familyhobbies.errorhandling.dto.ErrorCode;
import com.familyhobbies.errorhandling.exception.BaseException;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

class WebExceptionTest {

    @Test
    @DisplayName("should create BadRequestException with message and error code")
    void should_createBadRequestException_when_messageProvided() {
        BadRequestException ex = new BadRequestException("Invalid email format");
        assertThat(ex.getMessage()).isEqualTo("Invalid email format");
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(ex.getHttpStatus()).isEqualTo(400);
        assertThat(ex).isInstanceOf(BaseException.class);
    }

    @Test
    @DisplayName("should create UnauthorizedException with correct status")
    void should_createUnauthorizedException_when_instantiated() {
        UnauthorizedException ex = new UnauthorizedException("Invalid credentials");
        assertThat(ex.getMessage()).isEqualTo("Invalid credentials");
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
        assertThat(ex.getHttpStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("should create ForbiddenException with correct status")
    void should_createForbiddenException_when_instantiated() {
        ForbiddenException ex = new ForbiddenException("Access denied");
        assertThat(ex.getMessage()).isEqualTo("Access denied");
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
        assertThat(ex.getHttpStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("should create ResourceNotFoundException with resource details")
    void should_createResourceNotFoundException_when_resourceDetailsProvided() {
        ResourceNotFoundException ex = ResourceNotFoundException.of("User", 42L);
        assertThat(ex.getMessage()).isEqualTo("User not found with id: 42");
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(ex.getHttpStatus()).isEqualTo(404);
        assertThat(ex.getResourceType()).isEqualTo("User");
        assertThat(ex.getResourceId()).isEqualTo("42");
    }

    @Test
    @DisplayName("should create ConflictException with correct status")
    void should_createConflictException_when_instantiated() {
        ConflictException ex = new ConflictException("Email already exists");
        assertThat(ex.getMessage()).isEqualTo("Email already exists");
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CONFLICT);
        assertThat(ex.getHttpStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("should create UnprocessableEntityException with correct status")
    void should_createUnprocessableEntityException_when_instantiated() {
        UnprocessableEntityException ex = new UnprocessableEntityException("Subscription already exists for this season");
        assertThat(ex.getMessage()).isEqualTo("Subscription already exists for this season");
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNPROCESSABLE_ENTITY);
        assertThat(ex.getHttpStatus()).isEqualTo(422);
    }

    @Test
    @DisplayName("should create TooManyRequestsException with correct status")
    void should_createTooManyRequestsException_when_instantiated() {
        TooManyRequestsException ex = new TooManyRequestsException("Rate limit exceeded");
        assertThat(ex.getMessage()).isEqualTo("Rate limit exceeded");
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.TOO_MANY_REQUESTS);
        assertThat(ex.getHttpStatus()).isEqualTo(429);
    }

    @Test
    @DisplayName("should preserve cause in all web exceptions")
    void should_preserveCause_when_causeProvided() {
        RuntimeException cause = new RuntimeException("original error");
        BadRequestException ex = new BadRequestException("Wrapped error", cause);
        assertThat(ex.getCause()).isEqualTo(cause);
    }
}
