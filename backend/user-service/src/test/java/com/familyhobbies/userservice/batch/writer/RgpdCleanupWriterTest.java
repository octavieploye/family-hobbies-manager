package com.familyhobbies.userservice.batch.writer;

import com.familyhobbies.errorhandling.exception.container.ExternalApiException;
import com.familyhobbies.userservice.adapter.AssociationServiceClient;
import com.familyhobbies.userservice.adapter.PaymentServiceClient;
import com.familyhobbies.userservice.entity.User;
import com.familyhobbies.userservice.entity.UserRole;
import com.familyhobbies.userservice.entity.UserStatus;
import com.familyhobbies.userservice.entity.enums.CrossServiceCleanupStatus;
import com.familyhobbies.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.item.Chunk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RgpdCleanupWriterTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AssociationServiceClient associationServiceClient;

    @Mock
    private PaymentServiceClient paymentServiceClient;

    private RgpdCleanupWriter writer;

    @BeforeEach
    void setUp() {
        writer = new RgpdCleanupWriter(userRepository, associationServiceClient, paymentServiceClient);
    }

    @Test
    @DisplayName("Should save user and trigger cross-service cleanup on success")
    void shouldSaveAndTriggerCleanup() throws Exception {
        User user = buildAnonymizedUser(1L);
        doNothing().when(associationServiceClient).cleanupUserData(1L);
        doNothing().when(paymentServiceClient).cleanupUserData(1L);

        writer.write(new Chunk<>(user));

        verify(userRepository).save(user);
        verify(associationServiceClient).cleanupUserData(1L);
        verify(paymentServiceClient).cleanupUserData(1L);
        assertThat(writer.getAnonymizedCount()).isEqualTo(1);
        assertThat(writer.getOverallCleanupStatus()).isEqualTo(CrossServiceCleanupStatus.SUCCESS);
        assertThat(writer.getErrorDetailsAsString()).isNull();
    }

    @Test
    @DisplayName("Should set PARTIAL_FAILURE when one service fails")
    void shouldSetPartialFailureWhenOneServiceFails() throws Exception {
        User user = buildAnonymizedUser(2L);
        doNothing().when(associationServiceClient).cleanupUserData(2L);
        doThrow(new ExternalApiException("payment down", "payment-service", 503))
                .when(paymentServiceClient).cleanupUserData(2L);

        writer.write(new Chunk<>(user));

        verify(userRepository).save(user);
        assertThat(writer.getAnonymizedCount()).isEqualTo(1);
        assertThat(writer.getOverallCleanupStatus()).isEqualTo(CrossServiceCleanupStatus.PARTIAL_FAILURE);
        assertThat(writer.getErrorDetailsAsString()).contains("payment-service");
    }

    @Test
    @DisplayName("Should set FAILED when both services fail")
    void shouldSetFailedWhenBothServicesFail() throws Exception {
        User user = buildAnonymizedUser(3L);
        doThrow(new ExternalApiException("assoc down", "association-service", 503))
                .when(associationServiceClient).cleanupUserData(3L);
        doThrow(new ExternalApiException("payment down", "payment-service", 503))
                .when(paymentServiceClient).cleanupUserData(3L);

        writer.write(new Chunk<>(user));

        verify(userRepository).save(user);
        assertThat(writer.getAnonymizedCount()).isEqualTo(1);
        assertThat(writer.getOverallCleanupStatus()).isEqualTo(CrossServiceCleanupStatus.FAILED);
        assertThat(writer.getErrorDetailsAsString()).contains("association-service", "payment-service");
    }

    @Test
    @DisplayName("Should reset counters between job executions")
    void shouldResetCounters() throws Exception {
        User user = buildAnonymizedUser(4L);
        doNothing().when(associationServiceClient).cleanupUserData(any());
        doNothing().when(paymentServiceClient).cleanupUserData(any());

        writer.write(new Chunk<>(user));
        assertThat(writer.getAnonymizedCount()).isEqualTo(1);

        writer.resetCounters();

        assertThat(writer.getAnonymizedCount()).isEqualTo(0);
        assertThat(writer.getOverallCleanupStatus()).isEqualTo(CrossServiceCleanupStatus.SUCCESS);
        assertThat(writer.getErrorDetailsAsString()).isNull();
    }

    @Test
    @DisplayName("Should handle unexpected exceptions from cross-service calls")
    void shouldHandleUnexpectedExceptions() throws Exception {
        User user = buildAnonymizedUser(5L);
        doThrow(new RuntimeException("unexpected error"))
                .when(associationServiceClient).cleanupUserData(5L);
        doNothing().when(paymentServiceClient).cleanupUserData(5L);

        writer.write(new Chunk<>(user));

        verify(userRepository).save(user);
        assertThat(writer.getAnonymizedCount()).isEqualTo(1);
        assertThat(writer.getOverallCleanupStatus()).isEqualTo(CrossServiceCleanupStatus.PARTIAL_FAILURE);
        assertThat(writer.getErrorDetailsAsString()).contains("Unexpected error");
    }

    private User buildAnonymizedUser(Long id) {
        return User.builder()
                .id(id)
                .email("anon-abc12345@anonymized.local")
                .firstName("ANON-abc12345")
                .lastName("ANON-def67890")
                .phone("0000000000")
                .passwordHash("anonhash")
                .role(UserRole.FAMILY)
                .status(UserStatus.DELETED)
                .anonymized(true)
                .build();
    }
}
