package com.familyhobbies.paymentservice.repository;

import com.familyhobbies.paymentservice.entity.PaymentWebhookLog;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link PaymentWebhookLog} entities.
 * No @Repository annotation -- Spring Data auto-detects JpaRepository interfaces.
 */
public interface PaymentWebhookLogRepository extends JpaRepository<PaymentWebhookLog, Long> {

    boolean existsByHelloassoEventIdAndProcessedTrue(String helloassoEventId);
}
