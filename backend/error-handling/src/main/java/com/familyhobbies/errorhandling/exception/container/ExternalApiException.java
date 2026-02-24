package com.familyhobbies.errorhandling.exception.container;

import com.familyhobbies.errorhandling.dto.ErrorCode;
import com.familyhobbies.errorhandling.exception.BaseException;

/**
 * Thrown when an external API (e.g. HelloAsso) returns an error response (HTTP 502).
 */
public class ExternalApiException extends BaseException {

    private final String apiName;
    private final int upstreamStatus;

    public ExternalApiException(String message, String apiName, int upstreamStatus) {
        super(message, ErrorCode.EXTERNAL_API_FAILURE, 502);
        this.apiName = apiName;
        this.upstreamStatus = upstreamStatus;
    }

    public ExternalApiException(String message, String apiName, int upstreamStatus, Throwable cause) {
        super(message, ErrorCode.EXTERNAL_API_FAILURE, 502, cause);
        this.apiName = apiName;
        this.upstreamStatus = upstreamStatus;
    }

    /**
     * Static factory that builds a descriptive message from the API name, upstream status, and reason.
     * <p>Example: {@code ExternalApiException.forApi("HelloAsso", 503, "Service Unavailable")}
     * produces message {@code "External API 'HelloAsso' returned error: Service Unavailable (status: 503)"}.
     */
    public static ExternalApiException forApi(String apiName, int upstreamStatus, String reason) {
        String message = "External API '" + apiName + "' returned error: " + reason
                + " (status: " + upstreamStatus + ")";
        return new ExternalApiException(message, apiName, upstreamStatus);
    }

    public String getApiName() {
        return apiName;
    }

    public int getUpstreamStatus() {
        return upstreamStatus;
    }
}
