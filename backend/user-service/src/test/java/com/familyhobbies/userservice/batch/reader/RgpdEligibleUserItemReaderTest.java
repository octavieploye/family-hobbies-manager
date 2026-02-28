package com.familyhobbies.userservice.batch.reader;

import com.familyhobbies.userservice.entity.User;
import com.familyhobbies.userservice.entity.UserRole;
import com.familyhobbies.userservice.entity.UserStatus;
import com.familyhobbies.userservice.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RgpdEligibleUserItemReaderTest {

    @Mock
    private UserRepository userRepository;

    @Test
    @DisplayName("Should return eligible users one by one and null when exhausted")
    void shouldReturnUsersOneByOne() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-02-28T10:00:00Z"), ZoneId.of("UTC"));
        User user1 = buildUser(1L);
        User user2 = buildUser(2L);
        when(userRepository.findEligibleForAnonymization(eq(UserStatus.DELETED), any(Instant.class)))
                .thenReturn(List.of(user1, user2));

        RgpdEligibleUserItemReader reader = new RgpdEligibleUserItemReader(userRepository, fixedClock, 30);

        assertThat(reader.read()).isEqualTo(user1);
        assertThat(reader.read()).isEqualTo(user2);
        assertThat(reader.read()).isNull(); // end of data
    }

    @Test
    @DisplayName("Should return null immediately when no eligible users found")
    void shouldReturnNullWhenNoEligibleUsers() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-02-28T10:00:00Z"), ZoneId.of("UTC"));
        when(userRepository.findEligibleForAnonymization(eq(UserStatus.DELETED), any(Instant.class)))
                .thenReturn(List.of());

        RgpdEligibleUserItemReader reader = new RgpdEligibleUserItemReader(userRepository, fixedClock, 30);

        assertThat(reader.read()).isNull();
    }

    @Test
    @DisplayName("Should only query repository once (lazy initialization)")
    void shouldOnlyQueryOnce() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-02-28T10:00:00Z"), ZoneId.of("UTC"));
        when(userRepository.findEligibleForAnonymization(eq(UserStatus.DELETED), any(Instant.class)))
                .thenReturn(List.of(buildUser(1L)));

        RgpdEligibleUserItemReader reader = new RgpdEligibleUserItemReader(userRepository, fixedClock, 30);

        reader.read(); // first call initializes
        reader.read(); // second call returns null (end of data)
        reader.read(); // third call still returns null

        verify(userRepository, times(1)).findEligibleForAnonymization(any(), any());
    }

    private User buildUser(Long id) {
        return User.builder()
                .id(id)
                .email("user" + id + "@example.com")
                .firstName("User")
                .lastName("Test")
                .passwordHash("hash")
                .role(UserRole.FAMILY)
                .status(UserStatus.DELETED)
                .anonymized(false)
                .build();
    }
}
