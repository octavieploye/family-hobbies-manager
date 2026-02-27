package com.familyhobbies.errorhandling.exception.web;

import com.familyhobbies.errorhandling.dto.ErrorCode;
import com.familyhobbies.errorhandling.exception.BaseException;

/**
 * Thrown when the client has exceeded the allowed request rate (HTTP 429).
 */
public class TooManyRequestsException extends BaseException {

    public TooManyRequestsException(String message) {
        super(message, ErrorCode.TOO_MANY_REQUESTS);
    }

    public TooManyRequestsException(String message, Throwable cause) {
        super(message, ErrorCode.TOO_MANY_REQUESTS, cause);
    }
}
