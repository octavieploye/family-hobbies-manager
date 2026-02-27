package com.familyhobbies.paymentservice.repository;

import com.familyhobbies.paymentservice.entity.Invoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link Invoice} entities.
 * No @Repository annotation -- Spring Data auto-detects JpaRepository interfaces.
 */
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByPaymentId(Long paymentId);

    List<Invoice> findAllByPaymentId(Long paymentId);

    Page<Invoice> findByBuyerEmailOrderByIssuedAtDesc(String buyerEmail, Pageable pageable);

    Optional<Invoice> findFirstByOrderByIdDesc();
}
