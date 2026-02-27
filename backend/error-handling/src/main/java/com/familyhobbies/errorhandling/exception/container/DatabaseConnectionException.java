package com.familyhobbies.errorhandling.exception.container;

import com.familyhobbies.errorhandling.dto.ErrorCode;
import com.familyhobbies.errorhandling.exception.BaseException;

/**
 * Thrown when a database connection cannot be established (HTTP 503).
 */
public class DatabaseConnectionException extends BaseException {

    public DatabaseConnectionException(String message) {
        super(message, ErrorCode.DATABASE_CONNECTION_FAILURE);
    }

    public DatabaseConnectionException(String message, Throwable cause) {
        super(message, ErrorCode.DATABASE_CONNECTION_FAILURE, cause);
    }

    /**
     * Static factory that builds a descriptive message from the database name.
     * <p>Example: {@code DatabaseConnectionException.forDatabase("familyhobbies_users")}
     * produces message {@code "Failed to connect to database: familyhobbies_users"}.
     */
    public static DatabaseConnectionException forDatabase(String databaseName) {
        String message = "Failed to connect to database: " + databaseName;
        return new DatabaseConnectionException(message);
    }
}
