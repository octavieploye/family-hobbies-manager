package com.familyhobbies.errorhandling.exception;

import com.familyhobbies.errorhandling.dto.ErrorCode;

import java.util.Collections;
import java.util.Map;

/**
 * Abstract base for all custom exceptions in the Family Hobbies Manager platform.
 * Carries a structured {@link ErrorCode}, the corresponding HTTP status (derived
 * from the ErrorCode), and an optional details map for structured error metadata.
 *
 * <p>The HTTP status is derived from {@link ErrorCode#getHttpStatus()} to avoid
 * DRY violations where both the ErrorCode and the exception constructor would
 * independently specify the same status code.</p>
 */
public abstract class BaseException extends RuntimeException {

    private final ErrorCode errorCode;
    private final int httpStatus;

    /**
     * Reserved for future structured error data (Phase 2 validation errors).
     * Currently not populated by any exception subclass, but the field and accessor
     * are retained so that future enhancements (e.g., field-level validation details)
     * can attach structured metadata to exceptions without a breaking API change.
     */
    private final Map<String, Object> details;

    // ── Constructors deriving httpStatus from ErrorCode (preferred) ────────

    protected BaseException(String message, ErrorCode errorCode) {
        this(message, errorCode, null, null);
    }

    protected BaseException(String message, ErrorCode errorCode, Throwable cause) {
        this(message, errorCode, null, cause);
    }

    protected BaseException(String message, ErrorCode errorCode,
                            Map<String, Object> details) {
        this(message, errorCode, details, null);
    }

    protected BaseException(String message, ErrorCode errorCode,
                            Map<String, Object> details, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = errorCode.getHttpStatus();
        this.details = details;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    /**
     * Returns an unmodifiable view of the details map, or {@code null} if no details were provided.
     * Reserved for future structured error data (Phase 2 validation errors).
     */
    public Map<String, Object> getDetails() {
        return details != null ? Collections.unmodifiableMap(details) : null;
    }
}
