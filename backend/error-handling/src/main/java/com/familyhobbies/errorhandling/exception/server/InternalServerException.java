package com.familyhobbies.errorhandling.exception.server;

import com.familyhobbies.errorhandling.dto.ErrorCode;
import com.familyhobbies.errorhandling.exception.BaseException;

/**
 * Thrown when an unexpected server-side error occurs (HTTP 500).
 */
public class InternalServerException extends BaseException {

    public InternalServerException(String message) {
        super(message, ErrorCode.INTERNAL_SERVER_ERROR);
    }

    public InternalServerException(String message, Throwable cause) {
        super(message, ErrorCode.INTERNAL_SERVER_ERROR, cause);
    }
}
