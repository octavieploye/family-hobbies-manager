package com.familyhobbies.paymentservice.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Year;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for InvoiceNumberGenerator.
 *
 * Story: S6-006 -- Invoice Number Generation
 * Tests: 2 test methods
 *
 * Uses mocked EntityManager to verify format without requiring a database.
 */
@ExtendWith(MockitoExtension.class)
class InvoiceNumberGeneratorTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query query;

    private InvoiceNumberGenerator invoiceNumberGenerator;

    @BeforeEach
    void setUp() {
        invoiceNumberGenerator = new InvoiceNumberGenerator(entityManager);
    }

    @Test
    @DisplayName("should_generateNumberWithCorrectFormat_when_called")
    void should_generateNumberWithCorrectFormat_when_called() {
        // Given
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(1L);

        // When
        String invoiceNumber = invoiceNumberGenerator.generate();

        // Then
        int currentYear = Year.now().getValue();
        assertThat(invoiceNumber).isEqualTo("FHM-" + currentYear + "-000001");
        assertThat(invoiceNumber).matches("FHM-\\d{4}-\\d{6}");
    }

    @Test
    @DisplayName("should_generateSequentialNumbers_when_calledMultipleTimes")
    void should_generateSequentialNumbers_when_calledMultipleTimes() {
        // Given
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getSingleResult())
                .thenReturn(1L)
                .thenReturn(2L)
                .thenReturn(3L);

        // When
        String first = invoiceNumberGenerator.generate();
        String second = invoiceNumberGenerator.generate();
        String third = invoiceNumberGenerator.generate();

        // Then
        int currentYear = Year.now().getValue();
        assertThat(first).isEqualTo("FHM-" + currentYear + "-000001");
        assertThat(second).isEqualTo("FHM-" + currentYear + "-000002");
        assertThat(third).isEqualTo("FHM-" + currentYear + "-000003");

        // Verify sequential ordering
        assertThat(first).isLessThan(second);
        assertThat(second).isLessThan(third);
    }
}
