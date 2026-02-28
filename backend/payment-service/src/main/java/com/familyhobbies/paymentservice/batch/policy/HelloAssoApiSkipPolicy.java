package com.familyhobbies.paymentservice.batch.policy;

import com.familyhobbies.errorhandling.exception.container.ExternalApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.step.skip.SkipPolicy;

/**
 * Skip policy for the payment reconciliation batch job.
 *
 * <p>Allows the job to skip items that fail due to HelloAsso API unavailability
 * ({@link ExternalApiException}), up to a configurable maximum skip count.
 * Non-API exceptions are never skipped and cause the job to fail.
 *
 * <p>Skipped payments will be retried on the next scheduled run (they remain
 * in {@code PENDING} status and will be re-queried by the reader).
 */
public class HelloAssoApiSkipPolicy implements SkipPolicy {

    private static final Logger log = LoggerFactory.getLogger(HelloAssoApiSkipPolicy.class);

    private final int maxSkipCount;

    /**
     * @param maxSkipCount maximum number of items to skip before the job fails.
     *                     Use -1 for unlimited skips.
     */
    public HelloAssoApiSkipPolicy(int maxSkipCount) {
        this.maxSkipCount = maxSkipCount;
    }

    @Override
    public boolean shouldSkip(Throwable throwable, long skipCount) {
        if (throwable instanceof ExternalApiException) {
            if (maxSkipCount == -1 || skipCount < maxSkipCount) {
                log.warn("Skipping payment reconciliation due to HelloAsso API error "
                        + "(skip count: {}/{}): {}",
                        skipCount + 1,
                        maxSkipCount == -1 ? "unlimited" : maxSkipCount,
                        throwable.getMessage());
                return true;
            }
            log.error("Maximum skip count ({}) reached for HelloAsso API errors. Failing job.",
                    maxSkipCount);
            return false;
        }

        log.error("Non-skippable exception during payment reconciliation: {}",
                throwable.getMessage(), throwable);
        return false;
    }
}
