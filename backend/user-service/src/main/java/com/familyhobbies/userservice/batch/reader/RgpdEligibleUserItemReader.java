package com.familyhobbies.userservice.batch.reader;

import com.familyhobbies.userservice.entity.User;
import com.familyhobbies.userservice.entity.UserStatus;
import com.familyhobbies.userservice.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemReader;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;

/**
 * ItemReader that loads all users eligible for RGPD anonymization at first read,
 * then iterates through them one by one.
 *
 * <p>Eligible users are those with:
 * <ul>
 *     <li>{@code status = DELETED}</li>
 *     <li>{@code updatedAt < NOW() - retentionDays}</li>
 *     <li>{@code anonymized = false}</li>
 * </ul>
 */
public class RgpdEligibleUserItemReader implements ItemReader<User> {

    private static final Logger log = LoggerFactory.getLogger(RgpdEligibleUserItemReader.class);

    private final UserRepository userRepository;
    private final Clock clock;
    private final int retentionDays;
    private Iterator<User> userIterator;
    private boolean initialized = false;

    public RgpdEligibleUserItemReader(UserRepository userRepository,
                                       Clock clock,
                                       int retentionDays) {
        this.userRepository = userRepository;
        this.clock = clock;
        this.retentionDays = retentionDays;
    }

    @Override
    public User read() {
        if (!initialized) {
            Instant cutoff = Instant.now(clock).minus(Duration.ofDays(retentionDays));
            log.info("RGPD cleanup: querying DELETED users older than {} days (cutoff={})",
                    retentionDays, cutoff);

            List<User> eligibleUsers = userRepository.findEligibleForAnonymization(
                    UserStatus.DELETED, cutoff);
            log.info("RGPD cleanup: found {} users eligible for anonymization",
                    eligibleUsers.size());

            this.userIterator = eligibleUsers.iterator();
            this.initialized = true;
        }

        if (userIterator != null && userIterator.hasNext()) {
            User user = userIterator.next();
            log.debug("Reading eligible user: id={}, updatedAt={}", user.getId(), user.getUpdatedAt());
            return user;
        }

        return null; // signals end of data
    }
}
