package com.familyhobbies.paymentservice.service;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;

/**
 * Generates unique invoice numbers in the format FHM-{YEAR}-{SEQ}.
 * Uses the database sequence {@code invoice_number_seq} for guaranteed uniqueness.
 * Example: FHM-2026-000001
 */
@Component
public class InvoiceNumberGenerator {

    private final EntityManager entityManager;

    public InvoiceNumberGenerator(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Generates the next invoice number using the database sequence.
     *
     * @return a formatted invoice number (e.g., "FHM-2026-000001")
     */
    @Transactional
    public String generate() {
        Long nextVal = ((Number) entityManager
                .createNativeQuery("SELECT NEXT VALUE FOR invoice_number_seq")
                .getSingleResult()).longValue();

        int currentYear = Year.now().getValue();
        return String.format("FHM-%d-%06d", currentYear, nextVal);
    }
}
