package com.familyhobbies.errorhandling.exception.server;

import com.familyhobbies.errorhandling.dto.ErrorCode;
import com.familyhobbies.errorhandling.exception.BaseException;

/**
 * Thrown when the service is temporarily unavailable (HTTP 503).
 */
public class ServiceUnavailableException extends BaseException {

    public ServiceUnavailableException(String message) {
        super(message, ErrorCode.SERVICE_UNAVAILABLE);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, ErrorCode.SERVICE_UNAVAILABLE, cause);
    }
}
