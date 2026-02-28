package com.familyhobbies.associationservice.batch.processor;

import com.familyhobbies.associationservice.entity.Activity;
import com.familyhobbies.associationservice.entity.Subscription;
import com.familyhobbies.associationservice.entity.enums.SubscriptionStatus;
import com.familyhobbies.associationservice.entity.enums.SubscriptionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD contract tests for {@link SubscriptionExpiryProcessor}.
 *
 * <p>Verifies:
 * <ul>
 *   <li>Status transitions from ACTIVE to EXPIRED</li>
 *   <li>expiredAt timestamp is set to current time</li>
 *   <li>Other fields remain unchanged</li>
 * </ul>
 */
class SubscriptionExpiryProcessorTest {

    private SubscriptionExpiryProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new SubscriptionExpiryProcessor();
    }

    @Test
    @DisplayName("Should set status to EXPIRED for an active subscription")
    void shouldSetStatusToExpired() {
        // Given
        Subscription subscription = createActiveSubscription(
                LocalDate.now().minusDays(1));

        // When
        Subscription result = processor.process(subscription);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
    }

    @Test
    @DisplayName("Should set expiredAt to current timestamp")
    void shouldSetExpiredAtTimestamp() {
        // Given
        Subscription subscription = createActiveSubscription(
                LocalDate.now().minusDays(5));
        assertThat(subscription.getExpiredAt()).isNull();

        // When
        Instant beforeProcess = Instant.now();
        Subscription result = processor.process(subscription);
        Instant afterProcess = Instant.now();

        // Then
        assertThat(result.getExpiredAt()).isNotNull();
        assertThat(result.getExpiredAt()).isAfterOrEqualTo(beforeProcess);
        assertThat(result.getExpiredAt()).isBeforeOrEqualTo(afterProcess);
    }

    @Test
    @DisplayName("Should preserve all other subscription fields")
    void shouldPreserveOtherFields() {
        // Given
        Long subscriptionId = 42L;
        Long userId = 100L;
        Long familyMemberId = 200L;
        Long familyId = 300L;
        LocalDate startDate = LocalDate.now().minusYears(1);
        LocalDate endDate = LocalDate.now().minusDays(1);

        Subscription subscription = new Subscription();
        subscription.setId(subscriptionId);
        subscription.setUserId(userId);
        subscription.setFamilyMemberId(familyMemberId);
        subscription.setFamilyId(familyId);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStartDate(startDate);
        subscription.setEndDate(endDate);
        subscription.setSubscriptionType(SubscriptionType.ADHESION);

        // When
        Subscription result = processor.process(subscription);

        // Then -- identity fields unchanged
        assertThat(result.getId()).isEqualTo(subscriptionId);
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getFamilyMemberId()).isEqualTo(familyMemberId);
        assertThat(result.getFamilyId()).isEqualTo(familyId);
        assertThat(result.getStartDate()).isEqualTo(startDate);
        assertThat(result.getEndDate()).isEqualTo(endDate);
        // status and expiredAt changed
        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(result.getExpiredAt()).isNotNull();
    }

    private Subscription createActiveSubscription(LocalDate endDate) {
        Activity activity = new Activity();
        activity.setId(1L);

        Subscription subscription = new Subscription();
        subscription.setId(1L);
        subscription.setUserId(10L);
        subscription.setFamilyMemberId(20L);
        subscription.setFamilyId(30L);
        subscription.setActivity(activity);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setSubscriptionType(SubscriptionType.ADHESION);
        subscription.setStartDate(LocalDate.now().minusYears(1));
        subscription.setEndDate(endDate);
        subscription.setExpiredAt(null);
        return subscription;
    }
}
