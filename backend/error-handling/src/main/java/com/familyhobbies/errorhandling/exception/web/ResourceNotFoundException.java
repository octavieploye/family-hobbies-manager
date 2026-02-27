package com.familyhobbies.errorhandling.exception.web;

import com.familyhobbies.errorhandling.dto.ErrorCode;
import com.familyhobbies.errorhandling.exception.BaseException;

/**
 * Thrown when a requested resource does not exist (HTTP 404).
 * Carries optional {@code resourceType} and {@code resourceId} metadata.
 */
public class ResourceNotFoundException extends BaseException {

    private final String resourceType;
    private final String resourceId;

    public ResourceNotFoundException(String message) {
        super(message, ErrorCode.RESOURCE_NOT_FOUND);
        this.resourceType = null;
        this.resourceId = null;
    }

    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, ErrorCode.RESOURCE_NOT_FOUND, cause);
        this.resourceType = null;
        this.resourceId = null;
    }

    public ResourceNotFoundException(String resourceType, String resourceId, String message) {
        super(message, ErrorCode.RESOURCE_NOT_FOUND);
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    /**
     * Static factory that builds a descriptive message from the resource type and id.
     * <p>Example: {@code ResourceNotFoundException.of("User", 42)}
     * produces message {@code "User not found with id: 42"}.
     */
    public static ResourceNotFoundException of(String resourceType, Object id) {
        String resourceId = String.valueOf(id);
        String message = resourceType + " not found with id: " + resourceId;
        return new ResourceNotFoundException(resourceType, resourceId, message);
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }
}
