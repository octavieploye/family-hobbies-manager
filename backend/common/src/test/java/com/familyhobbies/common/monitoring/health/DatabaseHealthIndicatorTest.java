package com.familyhobbies.common.monitoring.health;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DatabaseHealthIndicator}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DatabaseHealthIndicator")
class DatabaseHealthIndicatorTest {

    @Mock
    private HikariDataSource hikariDataSource;

    @Mock
    private HikariPoolMXBean poolMXBean;

    @Test
    @DisplayName("should_return_up_with_pool_stats_when_connection_valid")
    void should_return_up_with_pool_stats_when_connection_valid() throws SQLException {
        // Given
        Connection connection = mock(Connection.class);
        when(connection.isValid(anyInt())).thenReturn(true);
        when(hikariDataSource.getConnection()).thenReturn(connection);
        when(hikariDataSource.getHikariPoolMXBean()).thenReturn(poolMXBean);
        when(hikariDataSource.getPoolName()).thenReturn("HikariPool-1");
        when(hikariDataSource.getMaximumPoolSize()).thenReturn(10);
        when(poolMXBean.getActiveConnections()).thenReturn(2);
        when(poolMXBean.getIdleConnections()).thenReturn(8);
        when(poolMXBean.getThreadsAwaitingConnection()).thenReturn(0);
        when(poolMXBean.getTotalConnections()).thenReturn(10);

        DatabaseHealthIndicator indicator = new DatabaseHealthIndicator(hikariDataSource);

        // When
        Health health = indicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("pool", "HikariPool-1");
        assertThat(health.getDetails()).containsEntry("activeConnections", 2);
        assertThat(health.getDetails()).containsEntry("idleConnections", 8);
        assertThat(health.getDetails()).containsEntry("pendingThreads", 0);
        assertThat(health.getDetails()).containsEntry("totalConnections", 10);
        assertThat(health.getDetails()).containsEntry("maxPoolSize", 10);
    }

    @Test
    @DisplayName("should_return_down_when_connection_not_valid")
    void should_return_down_when_connection_not_valid() throws SQLException {
        // Given
        Connection connection = mock(Connection.class);
        when(connection.isValid(anyInt())).thenReturn(false);
        when(hikariDataSource.getConnection()).thenReturn(connection);

        DatabaseHealthIndicator indicator = new DatabaseHealthIndicator(hikariDataSource);

        // When
        Health health = indicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("error",
                "Database connection is not valid");
    }

    @Test
    @DisplayName("should_return_down_when_connection_throws")
    void should_return_down_when_connection_throws() throws SQLException {
        // Given
        when(hikariDataSource.getConnection())
                .thenThrow(new SQLException("Connection refused"));

        DatabaseHealthIndicator indicator = new DatabaseHealthIndicator(hikariDataSource);

        // When
        Health health = indicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails().get("error").toString())
                .contains("Connection refused");
    }

    @Test
    @DisplayName("should_return_up_without_pool_stats_when_pool_mxbean_null")
    void should_return_up_without_pool_stats_when_pool_mxbean_null() throws SQLException {
        // Given
        Connection connection = mock(Connection.class);
        when(connection.isValid(anyInt())).thenReturn(true);
        when(hikariDataSource.getConnection()).thenReturn(connection);
        when(hikariDataSource.getHikariPoolMXBean()).thenReturn(null);

        DatabaseHealthIndicator indicator = new DatabaseHealthIndicator(hikariDataSource);

        // When
        Health health = indicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsKey("type");
    }
}
