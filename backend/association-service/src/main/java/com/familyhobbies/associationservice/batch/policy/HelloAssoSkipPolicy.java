package com.familyhobbies.associationservice.batch.policy;

import com.familyhobbies.errorhandling.exception.container.ExternalApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.step.skip.SkipPolicy;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

/**
 * Skip policy for the HelloAsso sync batch step.
 *
 * <p>Skippable exceptions (transient):
 * <ul>
 *   <li>{@link ExternalApiException} with upstream status 429 (rate limit) or 5xx</li>
 *   <li>{@link WebClientRequestException} (connection refused, DNS failure)</li>
 *   <li>{@link TimeoutException} and {@link SocketTimeoutException}</li>
 *   <li>{@link ConnectException}</li>
 * </ul>
 *
 * <p>Non-skippable exceptions:
 * <ul>
 *   <li>{@link ExternalApiException} with upstream status 4xx (except 429)</li>
 *   <li>Any other unexpected exception (NullPointerException, etc.)</li>
 * </ul>
 */
public class HelloAssoSkipPolicy implements SkipPolicy {

    private static final Logger log = LoggerFactory.getLogger(HelloAssoSkipPolicy.class);

    private final int maxSkipCount;

    public HelloAssoSkipPolicy() {
        this(10);
    }

    public HelloAssoSkipPolicy(int maxSkipCount) {
        this.maxSkipCount = maxSkipCount;
    }

    @Override
    public boolean shouldSkip(Throwable throwable, long skipCount) {
        if (skipCount >= maxSkipCount) {
            log.warn("HelloAsso sync skip limit reached: maxSkipCount={}, lastException={}",
                    maxSkipCount, throwable.getMessage());
            return false;
        }

        if (throwable instanceof ExternalApiException apiEx) {
            int upstream = apiEx.getUpstreamStatus();
            if (upstream == 429 || upstream >= 500) {
                log.info("Skipping transient HelloAsso API error: upstream={}, message={}, skipCount={}",
                        upstream, apiEx.getMessage(), skipCount);
                return true;
            }
            log.warn("Non-skippable HelloAsso client error: upstream={}, message={}",
                    upstream, apiEx.getMessage());
            return false;
        }

        if (throwable instanceof WebClientRequestException
                || throwable instanceof TimeoutException
                || throwable instanceof SocketTimeoutException
                || throwable instanceof ConnectException) {
            log.info("Skipping transient connection error: type={}, message={}, skipCount={}",
                    throwable.getClass().getSimpleName(),
                    throwable.getMessage(), skipCount);
            return true;
        }

        log.warn("Non-skippable exception in HelloAsso sync: type={}, message={}",
                throwable.getClass().getSimpleName(),
                throwable.getMessage());
        return false;
    }
}
