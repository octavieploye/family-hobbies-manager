package com.familyhobbies.errorhandling.exception.web;

import com.familyhobbies.errorhandling.dto.ErrorCode;
import com.familyhobbies.errorhandling.exception.BaseException;

/**
 * Thrown when the request is syntactically valid but violates a business rule (HTTP 422).
 */
public class UnprocessableEntityException extends BaseException {

    public UnprocessableEntityException(String message) {
        super(message, ErrorCode.UNPROCESSABLE_ENTITY, 422);
    }

    public UnprocessableEntityException(String message, Throwable cause) {
        super(message, ErrorCode.UNPROCESSABLE_ENTITY, 422, cause);
    }
}
