package com.familyhobbies.userservice.batch.processor;

import com.familyhobbies.userservice.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Anonymizes all PII (Personally Identifiable Information) fields of a deleted user.
 *
 * <p>Replaces the following fields with SHA-256 hashed values:
 * <ul>
 *     <li>{@code firstName} -> {@code ANON-{hash8}}</li>
 *     <li>{@code lastName} -> {@code ANON-{hash8}}</li>
 *     <li>{@code email} -> {@code anon-{hash8}@anonymized.local}</li>
 *     <li>{@code phone} -> {@code 0000000000}</li>
 *     <li>{@code passwordHash} -> SHA-256 hash of "ANONYMIZED"</li>
 * </ul>
 *
 * <p>After anonymization, sets {@code anonymized = true} to prevent re-processing.
 *
 * <p>The hash is computed from the original value concatenated with the user ID
 * to ensure uniqueness across users with identical PII values.
 */
public class RgpdAnonymizationProcessor implements ItemProcessor<User, User> {

    private static final Logger log = LoggerFactory.getLogger(RgpdAnonymizationProcessor.class);
    private static final String ANONYMIZED_PHONE = "0000000000";

    /**
     * Anonymize all PII fields of the given user.
     *
     * @param user the deleted user whose data must be anonymized
     * @return the user with all PII replaced by hashed/anonymized values
     */
    @Override
    public User process(User user) throws Exception {
        log.info("Anonymizing PII for user id={}", user.getId());

        String salt = String.valueOf(user.getId());

        // Anonymize name fields
        user.setFirstName("ANON-" + hashAndTruncate(user.getFirstName(), salt));
        user.setLastName("ANON-" + hashAndTruncate(user.getLastName(), salt));

        // Anonymize email -- must remain unique and valid format
        String emailHash = hashAndTruncate(user.getEmail(), salt);
        user.setEmail("anon-" + emailHash + "@anonymized.local");

        // Anonymize phone number
        user.setPhone(ANONYMIZED_PHONE);

        // Anonymize password hash -- replace with hash of "ANONYMIZED"
        user.setPasswordHash(hashAndTruncate("ANONYMIZED", salt));

        // Mark as anonymized to prevent re-processing
        user.setAnonymized(true);

        log.info("Successfully anonymized PII for user id={}", user.getId());
        return user;
    }

    /**
     * Compute a SHA-256 hash of the input combined with a salt, and return
     * the first 8 hex characters. This produces a short, irreversible identifier.
     *
     * @param input the original PII value
     * @param salt  a per-user salt (typically the user ID) to ensure uniqueness
     * @return first 8 characters of the hex-encoded SHA-256 hash
     */
    String hashAndTruncate(String input, String salt) {
        if (input == null) {
            input = "null";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(
                    (input + "|" + salt).getBytes(StandardCharsets.UTF_8)
            );
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.substring(0, 8);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available in all JVM implementations
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
