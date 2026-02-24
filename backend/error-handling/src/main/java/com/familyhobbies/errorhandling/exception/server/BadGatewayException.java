package com.familyhobbies.errorhandling.exception.server;

import com.familyhobbies.errorhandling.dto.ErrorCode;
import com.familyhobbies.errorhandling.exception.BaseException;

/**
 * Thrown when an upstream service returns an invalid response (HTTP 502).
 */
public class BadGatewayException extends BaseException {

    public BadGatewayException(String message) {
        super(message, ErrorCode.BAD_GATEWAY, 502);
    }

    public BadGatewayException(String message, Throwable cause) {
        super(message, ErrorCode.BAD_GATEWAY, 502, cause);
    }
}
