package com.familyhobbies.common.monitoring.health;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Health indicator that checks Kafka broker connectivity.
 *
 * <p>Uses a short-lived {@link AdminClient} to describe the cluster.
 * Reports UP with broker count and cluster ID, or DOWN with the error.
 *
 * <p>Activated only when {@code spring.kafka.bootstrap-servers} is configured.
 * Timeout: 5 seconds to avoid blocking the health endpoint.
 */
@Component("kafkaHealthIndicator")
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class KafkaHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(KafkaHealthIndicator.class);
    private static final int TIMEOUT_SECONDS = 5;

    private final String bootstrapServers;

    public KafkaHealthIndicator(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    @Override
    public Health health() {
        try (AdminClient adminClient = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, TIMEOUT_SECONDS * 1000,
                AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, TIMEOUT_SECONDS * 1000
        ))) {
            DescribeClusterResult cluster = adminClient.describeCluster();

            String clusterId = cluster.clusterId()
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            int brokerCount = cluster.nodes()
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS).size();

            return Health.up()
                    .withDetail("clusterId", clusterId)
                    .withDetail("brokerCount", brokerCount)
                    .withDetail("bootstrapServers", bootstrapServers)
                    .build();

        } catch (Exception ex) {
            log.warn("Kafka health check failed: {}", ex.getMessage());
            String errorMessage = ex.getMessage() != null
                    ? ex.getMessage()
                    : ex.getClass().getSimpleName();
            return Health.down()
                    .withDetail("bootstrapServers", bootstrapServers)
                    .withDetail("error", errorMessage)
                    .build();
        }
    }
}
