package com.familyhobbies.userservice.batch.processor;

import com.familyhobbies.userservice.entity.User;
import com.familyhobbies.userservice.entity.UserRole;
import com.familyhobbies.userservice.entity.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RgpdAnonymizationProcessorTest {

    private RgpdAnonymizationProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new RgpdAnonymizationProcessor();
    }

    @Test
    @DisplayName("Should anonymize all PII fields with SHA-256 hashed values")
    void shouldAnonymizeAllPiiFields() throws Exception {
        User user = buildDeletedUser(1L, "alice@example.com", "Alice", "Dupont",
                "+33612345678", "hashedPassword123");

        User result = processor.process(user);

        assertThat(result).isNotNull();
        assertThat(result.getFirstName()).startsWith("ANON-").hasSize(13); // ANON- + 8 hex chars
        assertThat(result.getLastName()).startsWith("ANON-").hasSize(13);
        assertThat(result.getEmail()).matches("anon-[a-f0-9]{8}@anonymized\\.local");
        assertThat(result.getPhone()).isEqualTo("0000000000");
        assertThat(result.getPasswordHash()).hasSize(8); // 8 hex chars
        assertThat(result.isAnonymized()).isTrue();
    }

    @Test
    @DisplayName("Should preserve non-PII fields (id, role, status, timestamps)")
    void shouldPreserveNonPiiFields() throws Exception {
        User user = buildDeletedUser(42L, "bob@example.com", "Bob", "Martin",
                "+33698765432", "secureHash");

        User result = processor.process(user);

        assertThat(result.getId()).isEqualTo(42L);
        assertThat(result.getRole()).isEqualTo(UserRole.FAMILY);
        assertThat(result.getStatus()).isEqualTo(UserStatus.DELETED);
    }

    @Test
    @DisplayName("Should produce different hashes for different users with same PII")
    void shouldProduceDifferentHashesForDifferentUsers() throws Exception {
        User user1 = buildDeletedUser(1L, "same@example.com", "Same", "Name",
                "+33600000000", "sameHash");
        User user2 = buildDeletedUser(2L, "same@example.com", "Same", "Name",
                "+33600000000", "sameHash");

        User result1 = processor.process(user1);
        User result2 = processor.process(user2);

        assertThat(result1.getEmail()).isNotEqualTo(result2.getEmail());
        assertThat(result1.getFirstName()).isNotEqualTo(result2.getFirstName());
    }

    @Test
    @DisplayName("Should handle null phone gracefully")
    void shouldHandleNullPhone() throws Exception {
        User user = buildDeletedUser(1L, "test@example.com", "Test", "User",
                null, "hash");

        User result = processor.process(user);

        assertThat(result.getPhone()).isEqualTo("0000000000");
        assertThat(result.isAnonymized()).isTrue();
    }

    @Test
    @DisplayName("Should produce consistent hash for same input (deterministic)")
    void shouldProduceConsistentHash() {
        String result1 = processor.hashAndTruncate("test@example.com", "1");
        String result2 = processor.hashAndTruncate("test@example.com", "1");

        assertThat(result1).isEqualTo(result2);
        assertThat(result1).hasSize(8);
        assertThat(result1).matches("[a-f0-9]{8}");
    }

    @Test
    @DisplayName("Should handle null input in hashAndTruncate")
    void shouldHandleNullInput() {
        String result = processor.hashAndTruncate(null, "1");

        assertThat(result).isNotNull();
        assertThat(result).hasSize(8);
    }

    private User buildDeletedUser(Long id, String email, String firstName, String lastName,
                                   String phone, String passwordHash) {
        return User.builder()
                .id(id)
                .email(email)
                .firstName(firstName)
                .lastName(lastName)
                .phone(phone)
                .passwordHash(passwordHash)
                .role(UserRole.FAMILY)
                .status(UserStatus.DELETED)
                .anonymized(false)
                .build();
    }
}
