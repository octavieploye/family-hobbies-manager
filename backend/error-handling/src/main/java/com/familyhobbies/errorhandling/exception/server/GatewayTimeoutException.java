package com.familyhobbies.errorhandling.exception.server;

import com.familyhobbies.errorhandling.dto.ErrorCode;
import com.familyhobbies.errorhandling.exception.BaseException;

/**
 * Thrown when an upstream service does not respond within the expected time (HTTP 504).
 */
public class GatewayTimeoutException extends BaseException {

    public GatewayTimeoutException(String message) {
        super(message, ErrorCode.GATEWAY_TIMEOUT);
    }

    public GatewayTimeoutException(String message, Throwable cause) {
        super(message, ErrorCode.GATEWAY_TIMEOUT, cause);
    }
}
