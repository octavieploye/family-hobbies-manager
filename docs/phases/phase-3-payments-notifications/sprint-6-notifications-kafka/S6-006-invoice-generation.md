# Story S6-006: Implement Invoice Generation

> 5 points | Priority: P1 | Service: payment-service
> Sprint file: [Back to Sprint Index](./_index.md)
> Tests: [S6-006 Tests Companion](./S6-006-invoice-generation-tests.md)

---

## Context

When a payment is successfully completed via HelloAsso, the system must automatically generate an invoice for the family. This story implements the full invoice generation pipeline inside the payment-service: a Kafka consumer that listens on `family-hobbies.payment.completed`, an `InvoiceService` that creates the invoice with a sequential number (`FHM-{YYYY}-{000001}`), an `InvoicePdfGenerator` that produces a PDF using OpenPDF with all required legal mentions (association loi 1901, not subject to TVA), and an `InvoiceController` for querying and downloading invoices. The Invoice entity and InvoiceRepository already exist from S5-004; this story adds the generation logic, PDF adapter, Kafka consumer, DTOs, mapper, and REST API. The PDF is stored at `data/invoices/{invoiceNumber}.pdf`. S6-007 (Angular invoice download) depends on this story.

## Tasks

| # | Task | File Path | What To Create | How To Verify |
|---|------|-----------|----------------|---------------|
| 1 | Liquibase 004 -- add columns to t_invoice | `backend/payment-service/src/main/resources/db/changelog/changesets/004-alter-invoice-add-columns.yaml` | Add pdf_path, payer_email, payer_name, member_name, activity_name, association_name, season columns | Migration runs clean |
| 2 | Liquibase 005 -- invoice number sequence | `backend/payment-service/src/main/resources/db/changelog/changesets/005-create-invoice-number-sequence.yaml` | DB sequence for invoice numbering | Sequence exists |
| 3 | Update Invoice entity | `backend/payment-service/src/main/java/.../entity/Invoice.java` | Add new fields to entity | Compiles, Hibernate validates |
| 4 | Update InvoiceRepository | `backend/payment-service/src/main/java/.../repository/InvoiceRepository.java` | Add findByFamilyId paginated query + sequence method | Compiles |
| 5 | InvoiceResponse DTO | `backend/payment-service/src/main/java/.../dto/response/InvoiceResponse.java` | Full invoice detail response | Compiles |
| 6 | InvoiceSummaryResponse DTO | `backend/payment-service/src/main/java/.../dto/response/InvoiceSummaryResponse.java` | Summary for list views | Compiles |
| 7 | LineItemResponse DTO | `backend/payment-service/src/main/java/.../dto/response/LineItemResponse.java` | Single line item | Compiles |
| 8 | InvoiceMapper | `backend/payment-service/src/main/java/.../mapper/InvoiceMapper.java` | Entity <-> DTO conversions | Unit tests pass |
| 9 | InvoiceService interface | `backend/payment-service/src/main/java/.../service/InvoiceService.java` | Service contract | Compiles |
| 10 | InvoiceNumberGenerator | `backend/payment-service/src/main/java/.../service/InvoiceNumberGenerator.java` | Sequential number with DB sequence | Unit tests pass |
| 11 | InvoicePdfGenerator | `backend/payment-service/src/main/java/.../adapter/InvoicePdfGenerator.java` | OpenPDF-based PDF generation | PDF file created |
| 12 | InvoiceServiceImpl | `backend/payment-service/src/main/java/.../service/impl/InvoiceServiceImpl.java` | Full implementation | Unit tests pass |
| 13 | PaymentCompletedEventConsumer | `backend/payment-service/src/main/java/.../listener/PaymentCompletedEventConsumer.java` | Kafka consumer triggering invoice generation | Integration tests pass |
| 14 | InvoiceController | `backend/payment-service/src/main/java/.../controller/InvoiceController.java` | 3 REST endpoints | Integration tests pass |
| 15 | OpenPDF Maven dependency | `backend/payment-service/pom.xml` | Add OpenPDF dependency | Compiles |
| 16 | Failing tests (TDD) | See companion file | JUnit 5 test classes | Tests compile, fail (TDD) |

---

## Task 1 Detail: Liquibase 004 -- Add Columns to t_invoice

- **What**: Liquibase changeset adding extra columns to `t_invoice` for PDF generation data: pdf_path, payer_email, payer_name, member_name, activity_name, association_name, season
- **Where**: `backend/payment-service/src/main/resources/db/changelog/changesets/004-alter-invoice-add-columns.yaml`
- **Why**: The original t_invoice from S5-004 stores only financial data. Invoice generation requires descriptive fields for the PDF (who paid, for what activity, at which association, which season).
- **Content**:

```yaml
databaseChangeLog:
  - changeSet:
      id: 004-alter-invoice-add-columns
      author: family-hobbies-team
      changes:
        - addColumn:
            tableName: t_invoice
            columns:
              - column:
                  name: pdf_path
                  type: VARCHAR(255)
              - column:
                  name: payer_email
                  type: VARCHAR(255)
              - column:
                  name: payer_name
                  type: VARCHAR(255)
              - column:
                  name: member_name
                  type: VARCHAR(255)
              - column:
                  name: activity_name
                  type: VARCHAR(255)
              - column:
                  name: association_name
                  type: VARCHAR(255)
              - column:
                  name: season
                  type: VARCHAR(20)
              - column:
                  name: paid_at
                  type: TIMESTAMPTZ
              - column:
                  name: currency
                  type: VARCHAR(3)
                  defaultValue: 'EUR'
```

- **Verify**: `mvn liquibase:update -pl backend/payment-service` -> columns added to t_invoice

---

## Task 2 Detail: Liquibase 005 -- Invoice Number Sequence

- **What**: Liquibase changeset creating a PostgreSQL sequence for generating sequential invoice numbers
- **Where**: `backend/payment-service/src/main/resources/db/changelog/changesets/005-create-invoice-number-sequence.yaml`
- **Why**: Invoice numbers must be sequential per year (`FHM-2026-000001`). A DB sequence guarantees uniqueness and concurrency safety without application-level synchronization.
- **Content**:

```yaml
databaseChangeLog:
  - changeSet:
      id: 005-create-invoice-number-sequence
      author: family-hobbies-team
      changes:
        - createSequence:
            sequenceName: invoice_number_seq
            startValue: 1
            incrementBy: 1
            minValue: 1
            cacheSize: 1
      rollback:
        - dropSequence:
            sequenceName: invoice_number_seq
```

- **Verify**: `SELECT nextval('invoice_number_seq')` in psql -> returns 1

---

## Task 3 Detail: Update Invoice Entity

- **What**: Add new fields to the Invoice JPA entity to support PDF generation data
- **Where**: `backend/payment-service/src/main/java/com/familyhobbies/paymentservice/entity/Invoice.java`
- **Why**: The entity must map all new columns added in Task 1. These fields are populated by InvoiceServiceImpl when generating the invoice from a PaymentCompletedEvent.
- **Content** (additions to the existing entity from S5-004):

```java
package com.familyhobbies.paymentservice.entity;

import com.familyhobbies.paymentservice.enums.InvoiceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * JPA entity mapping {@code t_invoice} table.
 *
 * <p>Each invoice is linked to exactly one {@link Payment} via FK.
 * The {@code invoice_number} is a unique business identifier generated
 * in the format {@code FHM-{YYYY}-{000001}}.
 *
 * <p>Updated in S6-006 with PDF generation fields: payer info, activity
 * details, association name, season, and PDF storage path.
 */
@Entity
@Table(name = "t_invoice")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "invoice_number", nullable = false, unique = true, length = 20)
    private String invoiceNumber;

    @Column(name = "family_id", nullable = false)
    private Long familyId;

    @Column(name = "association_id", nullable = false)
    private Long associationId;

    @Column(name = "amount_ht", nullable = false, precision = 10, scale = 2)
    private BigDecimal amountHt;

    @Column(name = "tax_amount", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "amount_ttc", nullable = false, precision = 10, scale = 2)
    private BigDecimal amountTtc;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private InvoiceStatus status = InvoiceStatus.DRAFT;

    @Column(name = "issued_at")
    private Instant issuedAt;

    // --- S6-006 additions ---

    @Column(name = "pdf_path")
    private String pdfPath;

    @Column(name = "payer_email")
    private String payerEmail;

    @Column(name = "payer_name")
    private String payerName;

    @Column(name = "member_name")
    private String memberName;

    @Column(name = "activity_name")
    private String activityName;

    @Column(name = "association_name")
    private String associationName;

    @Column(name = "season", length = 20)
    private String season;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "currency", length = 3)
    @Builder.Default
    private String currency = "EUR";

    // --- end S6-006 additions ---

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
```

- **Verify**: `mvn compile -pl backend/payment-service` -> compiles; Hibernate validates new columns

---

## Task 4 Detail: Update InvoiceRepository

- **What**: Add paginated family query and sequence-based next value method to InvoiceRepository
- **Where**: `backend/payment-service/src/main/java/com/familyhobbies/paymentservice/repository/InvoiceRepository.java`
- **Why**: S6-006 needs `findByFamilyId` for the family invoice list endpoint and `getNextSequenceValue` for invoice number generation.
- **Content**:

```java
package com.familyhobbies.paymentservice.repository;

import com.familyhobbies.paymentservice.entity.Invoice;
import com.familyhobbies.paymentservice.enums.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Invoice}.
 *
 * <p>Extended in S6-006 with paginated family queries and DB sequence access.
 */
@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    /** Find invoice by unique invoice number. */
    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    /** Find invoice linked to a specific payment. */
    Optional<Invoice> findByPaymentId(Long paymentId);

    /**
     * Find invoices for a family with optional filters.
     * Null parameters are ignored.
     */
    @Query("""
            SELECT i FROM Invoice i
            WHERE i.familyId = :familyId
              AND (:season IS NULL OR i.season = :season)
              AND (:status IS NULL OR i.status = :status)
              AND (:from IS NULL OR i.createdAt >= :from)
              AND (:to IS NULL OR i.createdAt <= :to)
            ORDER BY i.createdAt DESC
            """)
    Page<Invoice> findByFamilyIdWithFilters(
            @Param("familyId") Long familyId,
            @Param("season") String season,
            @Param("status") InvoiceStatus status,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);

    /**
     * Gets the next value from the invoice_number_seq PostgreSQL sequence.
     * Used by {@link com.familyhobbies.paymentservice.service.InvoiceNumberGenerator}.
     */
    @Query(value = "SELECT nextval('invoice_number_seq')", nativeQuery = true)
    Long getNextSequenceValue();
}
```

- **Verify**: `mvn compile -pl backend/payment-service` -> compiles

---

## Task 5 Detail: InvoiceResponse DTO

- **What**: Full invoice detail response DTO with all fields for the detail view
- **Where**: `backend/payment-service/src/main/java/com/familyhobbies/paymentservice/dto/response/InvoiceResponse.java`
- **Why**: Returned by `GET /api/v1/invoices/{id}`. Contains all invoice data including line items.
- **Content**:

```java
package com.familyhobbies.paymentservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Full invoice detail response DTO.
 *
 * <p>Contains all invoice fields including payer info, activity details,
 * financial amounts, and a downloadUrl for the PDF.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceResponse {

    private Long id;
    private String invoiceNumber;
    private Long paymentId;
    private Long subscriptionId;
    private Long familyId;
    private String familyName;
    private String associationName;
    private String activityName;
    private String familyMemberName;
    private String season;
    private List<LineItemResponse> lineItems;
    private BigDecimal subtotal;
    private BigDecimal tax;
    private BigDecimal total;
    private String currency;
    private String status;
    private Instant issuedAt;
    private Instant paidAt;
    private String payerEmail;
    private String payerName;
    private String downloadUrl;
    private Instant createdAt;
}
```

- **Verify**: `mvn compile -pl backend/payment-service` -> compiles

---

## Task 6 Detail: InvoiceSummaryResponse DTO

- **What**: Summary response DTO for invoice list views
- **Where**: `backend/payment-service/src/main/java/com/familyhobbies/paymentservice/dto/response/InvoiceSummaryResponse.java`
- **Why**: Returned by `GET /api/v1/invoices/family/{familyId}`. Contains only the essential fields for a list row.
- **Content**:

```java
package com.familyhobbies.paymentservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Summary response DTO for invoice list views.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceSummaryResponse {

    private Long id;
    private String invoiceNumber;
    private String associationName;
    private String activityName;
    private String familyMemberName;
    private String season;
    private BigDecimal total;
    private String currency;
    private String status;
    private Instant issuedAt;
    private Instant paidAt;
    private String downloadUrl;
}
```

- **Verify**: `mvn compile -pl backend/payment-service` -> compiles

---

## Task 7 Detail: LineItemResponse DTO

- **What**: DTO representing a single line item on an invoice
- **Where**: `backend/payment-service/src/main/java/com/familyhobbies/paymentservice/dto/response/LineItemResponse.java`
- **Why**: Part of `InvoiceResponse.lineItems`. For MVP, a single line item per invoice (the subscription). Structure supports future multi-line invoices.
- **Content**:

```java
package com.familyhobbies.paymentservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * DTO representing a single line item on an invoice.
 *
 * <p>For MVP, each invoice has a single line item corresponding to the
 * subscription payment. The structure supports future multi-line invoices.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LineItemResponse {

    private String description;
    private BigDecimal amount;
}
```

- **Verify**: `mvn compile -pl backend/payment-service` -> compiles

---

## Task 8 Detail: InvoiceMapper

- **What**: Mapper component converting between Invoice entity and response DTOs
- **Where**: `backend/payment-service/src/main/java/com/familyhobbies/paymentservice/mapper/InvoiceMapper.java`
- **Why**: Centralizes entity-to-DTO conversion. Generates the downloadUrl and constructs line items from invoice data.
- **Content**:

```java
package com.familyhobbies.paymentservice.mapper;

import com.familyhobbies.paymentservice.dto.response.InvoiceResponse;
import com.familyhobbies.paymentservice.dto.response.InvoiceSummaryResponse;
import com.familyhobbies.paymentservice.dto.response.LineItemResponse;
import com.familyhobbies.paymentservice.entity.Invoice;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Maps Invoice entities to response DTOs.
 *
 * <p>The download URL is generated as a relative path:
 * {@code /api/v1/invoices/{id}/download}
 */
@Component
public class InvoiceMapper {

    /**
     * Converts an Invoice entity to a full detail response.
     *
     * @param entity the Invoice entity
     * @return the full response DTO
     */
    public InvoiceResponse toInvoiceResponse(Invoice entity) {
        // Build single line item for MVP
        LineItemResponse lineItem = LineItemResponse.builder()
                .description(buildLineItemDescription(entity))
                .amount(entity.getAmountHt())
                .build();

        return InvoiceResponse.builder()
                .id(entity.getId())
                .invoiceNumber(entity.getInvoiceNumber())
                .paymentId(entity.getPayment().getId())
                .subscriptionId(entity.getPayment().getSubscriptionId())
                .familyId(entity.getFamilyId())
                .familyName(entity.getPayerName())
                .associationName(entity.getAssociationName())
                .activityName(entity.getActivityName())
                .familyMemberName(entity.getMemberName())
                .season(entity.getSeason())
                .lineItems(List.of(lineItem))
                .subtotal(entity.getAmountHt())
                .tax(entity.getTaxAmount())
                .total(entity.getAmountTtc())
                .currency(entity.getCurrency())
                .status(entity.getStatus().name())
                .issuedAt(entity.getIssuedAt())
                .paidAt(entity.getPaidAt())
                .payerEmail(entity.getPayerEmail())
                .payerName(entity.getPayerName())
                .downloadUrl("/api/v1/invoices/" + entity.getId() + "/download")
                .createdAt(entity.getCreatedAt())
                .build();
    }

    /**
     * Converts an Invoice entity to a summary response for list views.
     *
     * @param entity the Invoice entity
     * @return the summary response DTO
     */
    public InvoiceSummaryResponse toInvoiceSummaryResponse(Invoice entity) {
        return InvoiceSummaryResponse.builder()
                .id(entity.getId())
                .invoiceNumber(entity.getInvoiceNumber())
                .associationName(entity.getAssociationName())
                .activityName(entity.getActivityName())
                .familyMemberName(entity.getMemberName())
                .season(entity.getSeason())
                .total(entity.getAmountTtc())
                .currency(entity.getCurrency())
                .status(entity.getStatus().name())
                .issuedAt(entity.getIssuedAt())
                .paidAt(entity.getPaidAt())
                .downloadUrl("/api/v1/invoices/" + entity.getId() + "/download")
                .build();
    }

    private String buildLineItemDescription(Invoice entity) {
        StringBuilder sb = new StringBuilder();
        if (entity.getActivityName() != null) {
            sb.append(entity.getActivityName());
        }
        if (entity.getAssociationName() != null) {
            sb.append(" - ").append(entity.getAssociationName());
        }
        if (entity.getMemberName() != null) {
            sb.append(" (").append(entity.getMemberName()).append(")");
        }
        if (entity.getSeason() != null) {
            sb.append(" - Saison ").append(entity.getSeason());
        }
        return sb.toString();
    }
}
```

- **Verify**: `mvn compile -pl backend/payment-service` -> compiles

---

## Task 9 Detail: InvoiceService Interface

- **What**: Service interface for invoice operations
- **Where**: `backend/payment-service/src/main/java/com/familyhobbies/paymentservice/service/InvoiceService.java`
- **Why**: Decouples the controller and Kafka consumer from the implementation
- **Content**:

```java
package com.familyhobbies.paymentservice.service;

import com.familyhobbies.paymentservice.dto.response.InvoiceResponse;
import com.familyhobbies.paymentservice.dto.response.InvoiceSummaryResponse;
import com.familyhobbies.paymentservice.enums.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;

/**
 * Service contract for invoice operations.
 */
public interface InvoiceService {

    /**
     * Generates an invoice for a completed payment.
     * Called by the PaymentCompletedEvent Kafka consumer.
     *
     * @param paymentId the payment ID that was completed
     * @return the generated invoice response
     */
    InvoiceResponse generateInvoice(Long paymentId);

    /**
     * Finds an invoice by its ID.
     *
     * @param invoiceId the invoice ID
     * @return the invoice response
     * @throws com.familyhobbies.errorhandling.exception.web.ResourceNotFoundException
     *         if not found
     */
    InvoiceResponse findById(Long invoiceId);

    /**
     * Finds invoices for a family with optional filters.
     *
     * @param familyId the family ID
     * @param season   optional season filter (e.g. "2025-2026")
     * @param status   optional status filter
     * @param from     optional start date filter
     * @param to       optional end date filter
     * @param pageable pagination parameters
     * @return paginated invoice summaries
     */
    Page<InvoiceSummaryResponse> findByFamilyId(
            Long familyId, String season, InvoiceStatus status,
            Instant from, Instant to, Pageable pageable);

    /**
     * Downloads the invoice PDF as a byte array.
     *
     * @param invoiceId the invoice ID
     * @return the PDF content as bytes
     * @throws com.familyhobbies.errorhandling.exception.web.ResourceNotFoundException
     *         if invoice not found or PDF not generated
     */
    byte[] downloadInvoice(Long invoiceId);
}
```

- **Verify**: `mvn compile -pl backend/payment-service` -> compiles

---

## Task 10 Detail: InvoiceNumberGenerator

- **What**: Component that generates sequential invoice numbers using the PostgreSQL sequence
- **Where**: `backend/payment-service/src/main/java/com/familyhobbies/paymentservice/service/InvoiceNumberGenerator.java`
- **Why**: Invoice numbers must be unique and sequential per year. Uses DB sequence for concurrency safety.
- **Content**:

```java
package com.familyhobbies.paymentservice.service;

import com.familyhobbies.paymentservice.repository.InvoiceRepository;
import org.springframework.stereotype.Component;

import java.time.Year;

/**
 * Generates sequential invoice numbers in the format {@code FHM-{YYYY}-{000001}}.
 *
 * <p>Uses a PostgreSQL sequence ({@code invoice_number_seq}) to guarantee
 * uniqueness and concurrency safety. The year prefix is the current year
 * at the time of generation.
 *
 * <p>Examples:
 * <ul>
 *   <li>{@code FHM-2026-000001}</li>
 *   <li>{@code FHM-2026-000002}</li>
 *   <li>{@code FHM-2027-000001} (sequence continues, year changes)</li>
 * </ul>
 */
@Component
public class InvoiceNumberGenerator {

    private final InvoiceRepository invoiceRepository;

    public InvoiceNumberGenerator(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    /**
     * Generates the next invoice number.
     *
     * @return the next invoice number in format FHM-YYYY-NNNNNN
     */
    public String generateNextInvoiceNumber() {
        Long sequenceValue = invoiceRepository.getNextSequenceValue();
        int currentYear = Year.now().getValue();
        return String.format("FHM-%d-%06d", currentYear, sequenceValue);
    }
}
```

- **Verify**: `mvn compile -pl backend/payment-service` -> compiles

---

## Task 11 Detail: InvoicePdfGenerator

- **What**: OpenPDF-based PDF generator for invoices with French legal mentions
- **Where**: `backend/payment-service/src/main/java/com/familyhobbies/paymentservice/adapter/InvoicePdfGenerator.java`
- **Why**: Generates the actual PDF document stored on disk and downloadable via the API. Uses OpenPDF (LGPL) instead of commercial iText.
- **Content**:

```java
package com.familyhobbies.paymentservice.adapter;

import com.familyhobbies.paymentservice.entity.Invoice;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Generates invoice PDFs using OpenPDF (LGPL fork of iText 2.1).
 *
 * <p>PDF contents:
 * <ul>
 *   <li>Company header ("Family Hobbies Manager")</li>
 *   <li>Invoice number and date</li>
 *   <li>Family info (name, email)</li>
 *   <li>Activity/subscription details (association, activity, season, member)</li>
 *   <li>Amount and payment method</li>
 *   <li>Legal mentions (association loi 1901, not subject to TVA)</li>
 *   <li>Footer with app URL</li>
 * </ul>
 *
 * <p>PDFs are stored at {@code data/invoices/{invoiceNumber}.pdf}.
 */
@Component
public class InvoicePdfGenerator {

    private static final Logger log =
            LoggerFactory.getLogger(InvoicePdfGenerator.class);

    private static final Font TITLE_FONT =
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Color.DARK_GRAY);
    private static final Font HEADER_FONT =
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.DARK_GRAY);
    private static final Font NORMAL_FONT =
            FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
    private static final Font SMALL_FONT =
            FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy")
                    .withZone(ZoneId.of("Europe/Paris"));

    private static final NumberFormat CURRENCY_FORMAT =
            NumberFormat.getCurrencyInstance(Locale.FRANCE);

    @Value("${app.invoices.storage-path:data/invoices}")
    private String storagePath;

    /**
     * Generates a PDF for the given invoice and writes it to disk.
     *
     * @param invoice the invoice entity with all fields populated
     * @return the absolute path to the generated PDF file
     * @throws RuntimeException if PDF generation or file writing fails
     */
    public String generatePdf(Invoice invoice) {
        String fileName = invoice.getInvoiceNumber() + ".pdf";
        Path directory = Paths.get(storagePath);
        Path filePath = directory.resolve(fileName);

        try {
            Files.createDirectories(directory);

            Document document = new Document(PageSize.A4, 50, 50, 50, 50);
            PdfWriter.getInstance(document, new FileOutputStream(filePath.toFile()));
            document.open();

            addCompanyHeader(document);
            addInvoiceInfo(document, invoice);
            addClientInfo(document, invoice);
            addLineItemsTable(document, invoice);
            addTotalsTable(document, invoice);
            addLegalMentions(document);
            addFooter(document);

            document.close();

            log.info("Generated invoice PDF: {} at {}", invoice.getInvoiceNumber(),
                    filePath.toAbsolutePath());

            return filePath.toString();

        } catch (DocumentException | IOException e) {
            log.error("Failed to generate PDF for invoice {}: {}",
                    invoice.getInvoiceNumber(), e.getMessage(), e);
            throw new RuntimeException(
                    "Erreur lors de la generation du PDF: " + e.getMessage(), e);
        }
    }

    private void addCompanyHeader(Document document) throws DocumentException {
        Paragraph title = new Paragraph("Family Hobbies Manager", TITLE_FONT);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(20);
        document.add(title);

        Paragraph subtitle = new Paragraph(
                "Plateforme de gestion des activites familiales", NORMAL_FONT);
        subtitle.setAlignment(Element.ALIGN_CENTER);
        subtitle.setSpacingAfter(30);
        document.add(subtitle);
    }

    private void addInvoiceInfo(Document document, Invoice invoice)
            throws DocumentException {
        Paragraph header = new Paragraph("FACTURE", HEADER_FONT);
        header.setSpacingAfter(10);
        document.add(header);

        document.add(new Paragraph(
                "Numero : " + invoice.getInvoiceNumber(), NORMAL_FONT));

        String issuedDate = invoice.getIssuedAt() != null
                ? DATE_FORMATTER.format(invoice.getIssuedAt())
                : DATE_FORMATTER.format(invoice.getCreatedAt());
        document.add(new Paragraph(
                "Date d'emission : " + issuedDate, NORMAL_FONT));

        if (invoice.getPaidAt() != null) {
            document.add(new Paragraph(
                    "Date de paiement : " + DATE_FORMATTER.format(invoice.getPaidAt()),
                    NORMAL_FONT));
        }

        Paragraph separator = new Paragraph(" ");
        separator.setSpacingAfter(20);
        document.add(separator);
    }

    private void addClientInfo(Document document, Invoice invoice)
            throws DocumentException {
        Paragraph header = new Paragraph("Client", HEADER_FONT);
        header.setSpacingAfter(10);
        document.add(header);

        if (invoice.getPayerName() != null) {
            document.add(new Paragraph("Nom : " + invoice.getPayerName(),
                    NORMAL_FONT));
        }
        if (invoice.getPayerEmail() != null) {
            document.add(new Paragraph("Email : " + invoice.getPayerEmail(),
                    NORMAL_FONT));
        }
        if (invoice.getMemberName() != null) {
            document.add(new Paragraph("Membre inscrit : " + invoice.getMemberName(),
                    NORMAL_FONT));
        }

        Paragraph separator = new Paragraph(" ");
        separator.setSpacingAfter(20);
        document.add(separator);
    }

    private void addLineItemsTable(Document document, Invoice invoice)
            throws DocumentException {
        PdfPTable table = new PdfPTable(new float[]{4, 1});
        table.setWidthPercentage(100);
        table.setSpacingBefore(10);

        // Header row
        PdfPCell descHeader = new PdfPCell(new Phrase("Description", HEADER_FONT));
        descHeader.setBackgroundColor(new Color(240, 240, 240));
        descHeader.setPadding(8);
        table.addCell(descHeader);

        PdfPCell amountHeader = new PdfPCell(new Phrase("Montant", HEADER_FONT));
        amountHeader.setBackgroundColor(new Color(240, 240, 240));
        amountHeader.setPadding(8);
        amountHeader.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(amountHeader);

        // Line item row
        String description = buildDescription(invoice);
        PdfPCell descCell = new PdfPCell(new Phrase(description, NORMAL_FONT));
        descCell.setPadding(8);
        table.addCell(descCell);

        PdfPCell amountCell = new PdfPCell(
                new Phrase(formatCurrency(invoice.getAmountHt()), NORMAL_FONT));
        amountCell.setPadding(8);
        amountCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(amountCell);

        document.add(table);
    }

    private void addTotalsTable(Document document, Invoice invoice)
            throws DocumentException {
        PdfPTable table = new PdfPTable(new float[]{4, 1});
        table.setWidthPercentage(100);
        table.setSpacingBefore(5);

        // Subtotal
        addTotalRow(table, "Sous-total HT", formatCurrency(invoice.getAmountHt()));

        // Tax
        addTotalRow(table, "TVA (0% - Association loi 1901)",
                formatCurrency(invoice.getTaxAmount()));

        // Total TTC (bold)
        PdfPCell totalLabel = new PdfPCell(new Phrase("Total TTC", HEADER_FONT));
        totalLabel.setPadding(8);
        totalLabel.setBorderWidthTop(2);
        totalLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(totalLabel);

        PdfPCell totalAmount = new PdfPCell(
                new Phrase(formatCurrency(invoice.getAmountTtc()), HEADER_FONT));
        totalAmount.setPadding(8);
        totalAmount.setBorderWidthTop(2);
        totalAmount.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(totalAmount);

        document.add(table);
    }

    private void addTotalRow(PdfPTable table, String label, String amount) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, NORMAL_FONT));
        labelCell.setPadding(6);
        labelCell.setBorder(0);
        labelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(labelCell);

        PdfPCell amountCell = new PdfPCell(new Phrase(amount, NORMAL_FONT));
        amountCell.setPadding(6);
        amountCell.setBorder(0);
        amountCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(amountCell);
    }

    private void addLegalMentions(Document document) throws DocumentException {
        Paragraph separator = new Paragraph(" ");
        separator.setSpacingAfter(30);
        document.add(separator);

        document.add(new Paragraph(
                "Mentions legales :", HEADER_FONT));
        document.add(new Paragraph(
                "Association loi 1901 - Non assujettie a la TVA "
                        + "(article 261-7-1-b du CGI)", SMALL_FONT));
        document.add(new Paragraph(
                "Ce document tient lieu de facture pour le paiement "
                        + "effectue via la plateforme HelloAsso.", SMALL_FONT));
    }

    private void addFooter(Document document) throws DocumentException {
        Paragraph separator = new Paragraph(" ");
        separator.setSpacingAfter(40);
        document.add(separator);

        Paragraph footer = new Paragraph(
                "Family Hobbies Manager - https://familyhobbies.fr",
                SMALL_FONT);
        footer.setAlignment(Element.ALIGN_CENTER);
        document.add(footer);

        Paragraph generated = new Paragraph(
                "Document genere automatiquement - Ne pas renvoyer",
                SMALL_FONT);
        generated.setAlignment(Element.ALIGN_CENTER);
        document.add(generated);
    }

    private String buildDescription(Invoice invoice) {
        StringBuilder sb = new StringBuilder();
        if (invoice.getActivityName() != null) {
            sb.append(invoice.getActivityName());
        }
        if (invoice.getAssociationName() != null) {
            if (sb.length() > 0) sb.append("\n");
            sb.append("Association : ").append(invoice.getAssociationName());
        }
        if (invoice.getMemberName() != null) {
            if (sb.length() > 0) sb.append("\n");
            sb.append("Membre : ").append(invoice.getMemberName());
        }
        if (invoice.getSeason() != null) {
            if (sb.length() > 0) sb.append("\n");
            sb.append("Saison : ").append(invoice.getSeason());
        }
        return sb.toString();
    }

    private String formatCurrency(java.math.BigDecimal amount) {
        if (amount == null) return "0,00 EUR";
        return CURRENCY_FORMAT.format(amount);
    }
}
```

- **Verify**: `mvn compile -pl backend/payment-service` -> compiles

---

## Task 12 Detail: InvoiceServiceImpl

- **What**: Full implementation of InvoiceService, orchestrating invoice creation, PDF generation, and file access
- **Where**: `backend/payment-service/src/main/java/com/familyhobbies/paymentservice/service/impl/InvoiceServiceImpl.java`
- **Why**: Core business logic triggered by PaymentCompletedEvent. Creates the Invoice entity, generates the invoice number, triggers PDF generation, and persists everything.
- **Content**:

```java
package com.familyhobbies.paymentservice.service.impl;

import com.familyhobbies.errorhandling.exception.web.ResourceNotFoundException;
import com.familyhobbies.paymentservice.adapter.InvoicePdfGenerator;
import com.familyhobbies.paymentservice.dto.response.InvoiceResponse;
import com.familyhobbies.paymentservice.dto.response.InvoiceSummaryResponse;
import com.familyhobbies.paymentservice.entity.Invoice;
import com.familyhobbies.paymentservice.entity.Payment;
import com.familyhobbies.paymentservice.enums.InvoiceStatus;
import com.familyhobbies.paymentservice.mapper.InvoiceMapper;
import com.familyhobbies.paymentservice.repository.InvoiceRepository;
import com.familyhobbies.paymentservice.repository.PaymentRepository;
import com.familyhobbies.paymentservice.service.InvoiceNumberGenerator;
import com.familyhobbies.paymentservice.service.InvoiceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

/**
 * Implementation of {@link InvoiceService}.
 *
 * <p>Invoice generation flow:
 * <ol>
 *   <li>Load the Payment entity by ID</li>
 *   <li>Check if an invoice already exists (idempotency)</li>
 *   <li>Generate sequential invoice number via DB sequence</li>
 *   <li>Create Invoice entity with all payment/subscription data</li>
 *   <li>Generate PDF via InvoicePdfGenerator</li>
 *   <li>Save the invoice with PDF path and ISSUED status</li>
 * </ol>
 *
 * <p>All amounts are in EUR. Tax is always 0 (association loi 1901).
 */
@Service
@Transactional(readOnly = true)
public class InvoiceServiceImpl implements InvoiceService {

    private static final Logger log =
            LoggerFactory.getLogger(InvoiceServiceImpl.class);

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final InvoiceNumberGenerator invoiceNumberGenerator;
    private final InvoicePdfGenerator invoicePdfGenerator;
    private final InvoiceMapper invoiceMapper;

    public InvoiceServiceImpl(
            InvoiceRepository invoiceRepository,
            PaymentRepository paymentRepository,
            InvoiceNumberGenerator invoiceNumberGenerator,
            InvoicePdfGenerator invoicePdfGenerator,
            InvoiceMapper invoiceMapper) {
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.invoiceNumberGenerator = invoiceNumberGenerator;
        this.invoicePdfGenerator = invoicePdfGenerator;
        this.invoiceMapper = invoiceMapper;
    }

    @Override
    @Transactional
    public InvoiceResponse generateInvoice(Long paymentId) {
        log.info("Generating invoice for paymentId={}", paymentId);

        // Idempotency: check if invoice already exists
        invoiceRepository.findByPaymentId(paymentId).ifPresent(existing -> {
            log.warn("Invoice already exists for paymentId={}: {}",
                    paymentId, existing.getInvoiceNumber());
            throw new IllegalStateException(
                    "Facture deja generee pour le paiement " + paymentId);
        });

        // Load payment
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Paiement non trouve: " + paymentId));

        // Generate invoice number
        String invoiceNumber = invoiceNumberGenerator.generateNextInvoiceNumber();

        // Build invoice entity
        Instant now = Instant.now();
        Invoice invoice = Invoice.builder()
                .payment(payment)
                .invoiceNumber(invoiceNumber)
                .familyId(payment.getFamilyId())
                .associationId(0L) // populated from subscription data if available
                .amountHt(payment.getAmount())
                .taxAmount(BigDecimal.ZERO) // Association loi 1901, not subject to TVA
                .amountTtc(payment.getAmount()) // HT = TTC (no tax)
                .status(InvoiceStatus.ISSUED)
                .issuedAt(now)
                .paidAt(payment.getPaidAt())
                .currency(payment.getCurrency())
                .payerName(payment.getDescription()) // fallback until enrichment
                .season(determineSeason())
                .build();

        // Generate PDF
        String pdfPath = invoicePdfGenerator.generatePdf(invoice);
        invoice.setPdfPath(pdfPath);

        // Save
        Invoice saved = invoiceRepository.save(invoice);
        log.info("Invoice {} generated for paymentId={}", invoiceNumber, paymentId);

        return invoiceMapper.toInvoiceResponse(saved);
    }

    @Override
    public InvoiceResponse findById(Long invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Facture non trouvee: " + invoiceId));

        return invoiceMapper.toInvoiceResponse(invoice);
    }

    @Override
    public Page<InvoiceSummaryResponse> findByFamilyId(
            Long familyId, String season, InvoiceStatus status,
            Instant from, Instant to, Pageable pageable) {

        Page<Invoice> invoices = invoiceRepository.findByFamilyIdWithFilters(
                familyId, season, status, from, to, pageable);

        return invoices.map(invoiceMapper::toInvoiceSummaryResponse);
    }

    @Override
    public byte[] downloadInvoice(Long invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Facture non trouvee: " + invoiceId));

        if (invoice.getPdfPath() == null) {
            throw new ResourceNotFoundException(
                    "PDF non disponible pour la facture " + invoiceId);
        }

        Path pdfPath = Paths.get(invoice.getPdfPath());
        if (!Files.exists(pdfPath)) {
            throw new ResourceNotFoundException(
                    "Fichier PDF non trouve: " + invoice.getInvoiceNumber());
        }

        try {
            return Files.readAllBytes(pdfPath);
        } catch (IOException e) {
            log.error("Failed to read PDF for invoice {}: {}",
                    invoice.getInvoiceNumber(), e.getMessage(), e);
            throw new RuntimeException(
                    "Erreur de lecture du PDF: " + e.getMessage(), e);
        }
    }

    /**
     * Determines the current season based on the calendar.
     * Seasons run from September to August.
     * Before September: current year - 1 to current year.
     * From September: current year to current year + 1.
     */
    private String determineSeason() {
        int month = java.time.LocalDate.now().getMonthValue();
        int year = java.time.LocalDate.now().getYear();
        if (month >= 9) {
            return year + "-" + (year + 1);
        } else {
            return (year - 1) + "-" + year;
        }
    }
}
```

- **Verify**: `mvn compile -pl backend/payment-service` -> compiles

---

## Task 13 Detail: PaymentCompletedEventConsumer

- **What**: Kafka consumer listening on `family-hobbies.payment.completed` that triggers invoice generation
- **Where**: `backend/payment-service/src/main/java/com/familyhobbies/paymentservice/listener/PaymentCompletedEventConsumer.java`
- **Why**: Automates invoice generation on payment completion. Runs within payment-service (same service that owns the Payment and Invoice entities).
- **Content**:

```java
package com.familyhobbies.paymentservice.listener;

import com.familyhobbies.common.event.PaymentCompletedEvent;
import com.familyhobbies.paymentservice.service.InvoiceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for {@link PaymentCompletedEvent}.
 *
 * <p>Listens on topic {@code family-hobbies.payment.completed} and triggers
 * invoice generation via {@link InvoiceService#generateInvoice(Long)}.
 *
 * <p>If invoice generation fails (e.g., payment not found, duplicate invoice),
 * the error is logged but does not cause the consumer to fail permanently.
 * The Kafka error handler with exponential backoff and DLT handles retries.
 */
@Component
public class PaymentCompletedEventConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(PaymentCompletedEventConsumer.class);

    private final InvoiceService invoiceService;

    public PaymentCompletedEventConsumer(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    /**
     * Processes a payment completed event by generating an invoice.
     *
     * @param event the payment completed event from Kafka
     */
    @KafkaListener(
            topics = "family-hobbies.payment.completed",
            groupId = "payment-service-invoice-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        log.info("Received PaymentCompletedEvent: paymentId={}, familyId={}, "
                        + "amount={}",
                event.getPaymentId(), event.getFamilyId(), event.getAmount());

        try {
            invoiceService.generateInvoice(event.getPaymentId());
            log.info("Invoice generated successfully for paymentId={}",
                    event.getPaymentId());
        } catch (IllegalStateException e) {
            // Duplicate invoice -- idempotency guard, not an error
            log.warn("Invoice already exists for paymentId={}: {}",
                    event.getPaymentId(), e.getMessage());
        } catch (Exception e) {
            log.error("Failed to generate invoice for paymentId={}: {}",
                    event.getPaymentId(), e.getMessage(), e);
            throw e; // Let Kafka error handler retry
        }
    }
}
```

- **Verify**: `mvn compile -pl backend/payment-service` -> compiles

---

## Task 14 Detail: InvoiceController

- **What**: REST controller with 3 invoice endpoints: detail, family list, PDF download
- **Where**: `backend/payment-service/src/main/java/com/familyhobbies/paymentservice/controller/InvoiceController.java`
- **Why**: S6-007 (Angular invoice download) depends on these endpoints. Supports family own-data access and admin access.
- **Content**:

```java
package com.familyhobbies.paymentservice.controller;

import com.familyhobbies.paymentservice.dto.response.InvoiceResponse;
import com.familyhobbies.paymentservice.dto.response.InvoiceSummaryResponse;
import com.familyhobbies.paymentservice.enums.InvoiceStatus;
import com.familyhobbies.paymentservice.service.InvoiceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * REST controller for invoice operations.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /api/v1/invoices/{id}} -- invoice details</li>
 *   <li>{@code GET /api/v1/invoices/family/{familyId}} -- family invoice list</li>
 *   <li>{@code GET /api/v1/invoices/{id}/download} -- download PDF</li>
 * </ul>
 *
 * <p>Auth: FAMILY (own data), ADMIN (all data). Enforced via X-User-Id
 * and X-User-Roles headers set by the API gateway.
 */
@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceController {

    private static final Logger log =
            LoggerFactory.getLogger(InvoiceController.class);

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    /**
     * GET /api/v1/invoices/{id} -- invoice details.
     *
     * @param userId injected from X-User-Id header
     * @param id     the invoice ID
     * @return full invoice response with line items
     */
    @GetMapping("/{id}")
    public ResponseEntity<InvoiceResponse> getInvoice(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id) {

        log.debug("GET /invoices/{} userId={}", id, userId);

        InvoiceResponse response = invoiceService.findById(id);

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/invoices/family/{familyId} -- family invoice list.
     *
     * @param userId   injected from X-User-Id header
     * @param familyId the family ID
     * @param season   optional season filter (e.g. "2025-2026")
     * @param status   optional status filter
     * @param from     optional start date filter
     * @param to       optional end date filter
     * @param pageable pagination params
     * @return paginated invoice summaries
     */
    @GetMapping("/family/{familyId}")
    public ResponseEntity<Page<InvoiceSummaryResponse>> getFamilyInvoices(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long familyId,
            @RequestParam(required = false) String season,
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        log.debug("GET /invoices/family/{} userId={}", familyId, userId);

        Page<InvoiceSummaryResponse> invoices =
                invoiceService.findByFamilyId(
                        familyId, season, status, from, to, pageable);

        return ResponseEntity.ok(invoices);
    }

    /**
     * GET /api/v1/invoices/{id}/download -- download invoice PDF.
     *
     * @param userId injected from X-User-Id header
     * @param id     the invoice ID
     * @return PDF binary with Content-Disposition attachment header
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> downloadInvoice(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id) {

        log.debug("GET /invoices/{}/download userId={}", id, userId);

        byte[] pdfContent = invoiceService.downloadInvoice(id);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(
                ContentDisposition.attachment()
                        .filename("facture-" + id + ".pdf")
                        .build());

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfContent);
    }
}
```

- **Verify**: `mvn compile -pl backend/payment-service` -> compiles

---

## Task 15 Detail: OpenPDF Maven Dependency

- **What**: Add OpenPDF dependency to payment-service pom.xml
- **Where**: `backend/payment-service/pom.xml` (additions to `<dependencies>` section)
- **Why**: OpenPDF is the LGPL-friendly fork of iText used for PDF generation. Must be declared as a Maven dependency.
- **Content** (add to existing pom.xml dependencies):

```xml
<!-- OpenPDF for invoice PDF generation (LGPL license) -->
<dependency>
    <groupId>com.github.librepdf</groupId>
    <artifactId>openpdf</artifactId>
    <version>2.0.3</version>
</dependency>
```

- **Verify**: `mvn compile -pl backend/payment-service` -> dependency resolved, compiles

---

## Failing Tests (TDD Contract)

Tests are in the companion file: **[S6-006 Invoice Generation Tests](./S6-006-invoice-generation-tests.md)**

The companion file contains:
- `InvoiceMapperTest` -- 4 tests (full response mapping, summary mapping, line item description, download URL generation)
- `InvoiceNumberGeneratorTest` -- 3 tests (format validation, sequential generation, year prefix)
- `InvoiceServiceImplTest` -- 8 tests (generate invoice success, idempotency, payment not found, find by id, find by id not found, find by family, download success, download no pdf)
- `PaymentCompletedEventConsumerTest` -- 3 tests (successful consumption, idempotent duplicate, error propagation)
- `InvoiceControllerTest` -- 5 tests (get invoice, get family invoices, download PDF, invoice not found, PDF not found)

---

## Acceptance Criteria Checklist

- [ ] Invoice generated automatically on PaymentCompletedEvent via Kafka consumer
- [ ] Invoice number is sequential: `FHM-{YYYY}-{000001}` format
- [ ] PDF generated with OpenPDF and contains: company header, invoice info, client info, line items, totals, legal mentions, footer
- [ ] PDF stored at `data/invoices/{invoiceNumber}.pdf`
- [ ] Legal mentions: "Association loi 1901 - Non assujettie a la TVA"
- [ ] Tax amount always 0 (association loi 1901 not subject to TVA)
- [ ] `GET /api/v1/invoices/{id}` returns full invoice with line items
- [ ] `GET /api/v1/invoices/family/{familyId}` returns paginated invoice summaries with filters
- [ ] `GET /api/v1/invoices/{id}/download` returns PDF binary with correct Content-Type and Content-Disposition
- [ ] Idempotency: duplicate PaymentCompletedEvent does not create duplicate invoice
- [ ] All 23 JUnit 5 tests pass green
