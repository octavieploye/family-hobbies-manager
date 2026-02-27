package com.familyhobbies.errorhandling.exception.container;

import com.familyhobbies.errorhandling.dto.ErrorCode;
import com.familyhobbies.errorhandling.exception.BaseException;

/**
 * Thrown when the service registry (Eureka) cannot resolve a target service (HTTP 503).
 */
public class ServiceDiscoveryException extends BaseException {

    public ServiceDiscoveryException(String message) {
        super(message, ErrorCode.SERVICE_DISCOVERY_FAILURE);
    }

    public ServiceDiscoveryException(String message, Throwable cause) {
        super(message, ErrorCode.SERVICE_DISCOVERY_FAILURE, cause);
    }
}
