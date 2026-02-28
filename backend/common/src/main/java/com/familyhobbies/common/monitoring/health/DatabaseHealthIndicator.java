package com.familyhobbies.common.monitoring.health;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Health indicator that reports database connectivity and HikariCP
 * connection pool statistics.
 *
 * <p>Reports:
 * <ul>
 *   <li>Pool name</li>
 *   <li>Active connections</li>
 *   <li>Idle connections</li>
 *   <li>Pending threads (waiting for a connection)</li>
 *   <li>Total connections</li>
 *   <li>Maximum pool size</li>
 * </ul>
 *
 * <p>Activated only when a {@link DataSource} bean exists (i.e., not in
 * discovery-service or api-gateway which have no database).
 */
@Component("databasePoolHealthIndicator")
@ConditionalOnBean(DataSource.class)
public class DatabaseHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(DatabaseHealthIndicator.class);

    private final DataSource dataSource;

    public DatabaseHealthIndicator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Health health() {
        try {
            // Verify basic connectivity
            try (var connection = dataSource.getConnection()) {
                boolean isValid = connection.isValid(3);
                if (!isValid) {
                    return Health.down()
                            .withDetail("error", "Database connection is not valid")
                            .build();
                }
            }

            // Add HikariCP pool statistics if available
            if (dataSource instanceof HikariDataSource hikariDataSource) {
                HikariPoolMXBean pool = hikariDataSource.getHikariPoolMXBean();
                if (pool != null) {
                    return Health.up()
                            .withDetail("pool", hikariDataSource.getPoolName())
                            .withDetail("activeConnections", pool.getActiveConnections())
                            .withDetail("idleConnections", pool.getIdleConnections())
                            .withDetail("pendingThreads", pool.getThreadsAwaitingConnection())
                            .withDetail("totalConnections", pool.getTotalConnections())
                            .withDetail("maxPoolSize", hikariDataSource.getMaximumPoolSize())
                            .build();
                }
            }

            return Health.up()
                    .withDetail("type", dataSource.getClass().getSimpleName())
                    .build();

        } catch (Exception ex) {
            log.warn("Database health check failed: {}", ex.getMessage());
            return Health.down()
                    .withDetail("error", ex.getMessage())
                    .build();
        }
    }
}
