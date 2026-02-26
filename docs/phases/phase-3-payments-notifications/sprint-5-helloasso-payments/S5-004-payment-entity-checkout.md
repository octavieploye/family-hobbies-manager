# Story S5-004: Payment Entity + HelloAssoCheckoutClient

> 8 points | Priority: P0 | Service: payment-service
> Sprint file: [Back to Sprint Index](./_index.md)

---

## Context

The payment-service is the financial backbone of the Family Hobbies Manager platform. This story establishes the core payment domain: the `Payment` and `Invoice` JPA entities with Liquibase migrations, the `HelloAssoCheckoutClient` adapter for initiating checkout sessions on the HelloAsso platform, and the `PaymentService` orchestration layer that ties checkout initiation to local persistence. When a family initiates a payment for a subscription (e.g., annual membership at a sports club), the service creates a local `Payment` record in PENDING status, calls HelloAsso to create a checkout session, stores the returned `helloassoCheckoutId`, and returns the checkout URL so the frontend can redirect the user to the HelloAsso payment page. The payment lifecycle is completed in S5-005 when HelloAsso sends a webhook confirming payment success or failure. This story depends on S5-001 (HelloAssoTokenManager for OAuth2 authentication) and on S3-003 (subscription entity in association-service, referenced by cross-service ID). All error handling uses the project's `error-handling` module exceptions, letting the `GlobalExceptionHandler` produce consistent error responses.

## Tasks

| # | Task | File Path | What To Create | How To Verify |
|---|------|-----------|----------------|---------------|
| 1 | PaymentStatus enum | `backend/payment-service/src/main/java/.../enums/PaymentStatus.java` | Enum: PENDING, AUTHORIZED, COMPLETED, FAILED, REFUNDED, CANCELLED | Compiles |
| 2 | PaymentMethod enum | `backend/payment-service/src/main/java/.../enums/PaymentMethod.java` | Enum: CARD, SEPA, INSTALLMENT_3X, INSTALLMENT_10X | Compiles |
| 3 | InvoiceStatus enum | `backend/payment-service/src/main/java/.../enums/InvoiceStatus.java` | Enum: DRAFT, ISSUED, PAID, CANCELLED | Compiles |
| 4 | Liquibase 001 -- t_payment | `backend/payment-service/src/main/resources/db/changesets/001-create-payment-table.xml` | Table with check constraint and 4 indexes | Migration runs clean |
| 5 | Liquibase 002 -- t_invoice | `backend/payment-service/src/main/resources/db/changesets/002-create-invoice-table.xml` | Table with FK, unique constraint, check constraint | Migration runs clean |
| 6 | Payment entity | `backend/payment-service/src/main/java/.../entity/Payment.java` | JPA entity mapping t_payment | Compiles, Hibernate validates |
| 7 | Invoice entity | `backend/payment-service/src/main/java/.../entity/Invoice.java` | JPA entity mapping t_invoice | Compiles, Hibernate validates |
| 8 | PaymentRepository | `backend/payment-service/src/main/java/.../repository/PaymentRepository.java` | Spring Data JPA with custom queries | Compiles |
| 9 | InvoiceRepository | `backend/payment-service/src/main/java/.../repository/InvoiceRepository.java` | Spring Data JPA | Compiles |
| 10 | CheckoutRequest DTO | `backend/payment-service/src/main/java/.../dto/request/CheckoutRequest.java` | Validated request for checkout initiation | Compiles |
| 11 | CheckoutResponse DTO | `backend/payment-service/src/main/java/.../dto/response/CheckoutResponse.java` | Response with checkoutUrl and paymentId | Compiles |
| 12 | PaymentResponse DTO | `backend/payment-service/src/main/java/.../dto/response/PaymentResponse.java` | Full payment detail response | Compiles |
| 13 | HelloAssoCheckoutClient | `backend/payment-service/src/main/java/.../adapter/HelloAssoCheckoutClient.java` | WebClient adapter calling HelloAsso checkout | MockWebServer tests pass |
| 14 | PaymentMapper | `backend/payment-service/src/main/java/.../mapper/PaymentMapper.java` | Entity <-> DTO conversions | Unit tests pass |
| 15 | PaymentService interface | `backend/payment-service/src/main/java/.../service/PaymentService.java` | Service contract | Compiles |
| 16 | PaymentServiceImpl | `backend/payment-service/src/main/java/.../service/impl/PaymentServiceImpl.java` | Full checkout + query implementation | Unit tests pass |
| 17 | HelloAssoProperties config | `backend/payment-service/src/main/java/.../config/HelloAssoProperties.java` | Config binding for helloasso.* | Properties load |
| 18 | WebClientConfig | `backend/payment-service/src/main/java/.../config/WebClientConfig.java` | WebClient.Builder bean | Bean injectable |
| 19 | SecurityConfig | `backend/payment-service/src/main/java/.../config/SecurityConfig.java` | Spring Security with JWT + webhook public path | Endpoint access correct |
| 20 | PaymentController | `backend/payment-service/src/main/java/.../controller/PaymentController.java` | REST endpoints for checkout + queries | Endpoints return expected responses |
| 21 | application.yml | `backend/payment-service/src/main/resources/application.yml` | Full config with HelloAsso, DB, Kafka, Eureka | Service starts |
| 22 | Failing tests (TDD) | See companion file | JUnit 5 test classes | Tests compile, fail (TDD) |

---

## Task 1 Detail: PaymentStatus Enum

- **What**: Enum representing the lifecycle states of a payment
- **Where**: `backend/payment-service/src/main/java/com/familyhobbies/paymentservice/enums/PaymentStatus.java`
- **Why**: Used by the `Payment` entity, `PaymentService`, and webhook handler to track payment state transitions
- **Content**:

```java
package com.familyhobbies.paymentservice.enums;

/**
 * Lifecycle states of a payment in the Family Hobbies Manager.
 *
 * <p>State transitions:
 * <pre>
 *   PENDING -> AUTHORIZED -> COMPLETED
 *   PENDING -> FAILED
 *   PENDING -> CANCELLED
 *   COMPLETED -> REFUNDED
 * </pre>
 */
public enum PaymentStatus {

    /** Payment created locally, awaiting HelloAsso checkout. */
    PENDING,

    /** HelloAsso authorized the payment (funds held, not yet captured). */
    AUTHORIZED,

    /** Payment successfully completed and funds captured. */
    COMPLETED,

    /** Payment failed (declined, insufficient funds, etc.). */
    FAILED,

    /** Payment was refunded after completion. */
    REFUNDED,

    /** Payment was cancelled before completion. */
    CANCELLED
}
```

- **Verify**: `mvn compile -pl backend/payment-service` -> compiles

---

## Task 2 Detail: PaymentMethod Enum

- **What**: Enum representing the payment methods supported via HelloAsso
- **Where**: `backend/payment-service/src/main/java/com/familyhobbies/paymentservice/enums/PaymentMethod.java`
- **Why**: Stored on the `Payment` entity after HelloAsso confirms the method used
- **Content**:

```java
package com.familyhobbies.paymentservice.enums;

/**
 * Payment methods available through the HelloAsso checkout.
 */
public enum PaymentMethod {

    /** Credit/debit card (Visa, Mastercard). */
    CARD,

    /** SEPA direct debit. */
    SEPA,

    /** 3-installment payment plan. */
    INSTALLMENT_3X,

    /** 10-installment payment plan. */
    INSTALLMENT_10X
}
```

- **Verify**: `mvn compile -pl backend/payment-service` -> compiles

---

## Task 3 Detail: InvoiceStatus Enum

- **What**: Enum representing the lifecycle states of an invoice
- **Where**: `backend/payment-service/src/main/java/com/familyhobbies/paymentservice/enums/InvoiceStatus.java`
- **Why**: Used by the `Invoice` entity to track invoice generation and payment confirmation
- **Content**:

```java
package com.familyhobbies.paymentservice.enums;

/**
 * Lifecycle states of an invoice.
 *
 * <p>State transitions:
 * <pre>
 *   DRAFT -> ISSUED -> PAID
 *   DRAFT -> CANCELLED
 *   ISSUED -> CANCELLED
 * </pre>
 */
public enum InvoiceStatus {

    /** Invoice created but not yet finalized. */
    DRAFT,

    /** Invoice issued and sent to the family. */
    ISSUED,

    /** Invoice fully paid. */
    PAID,

    /** Invoice cancelled. */
    CANCELLED
}
```

- **Verify**: `mvn compile -pl backend/payment-service` -> compiles

---

## Task 4 Detail: Liquibase 001 -- Create t_payment Table

- **What**: Liquibase changeset creating the `t_payment` table with identity PK, check constraint on amount, and 4 indexes
- **Where**: `backend/payment-service/src/main/resources/db/changesets/001-create-payment-table.xml`
- **Why**: Foundation table for all payment data. Must exist before any PaymentRepository operations.
- **Content**:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">

    <changeSet id="001-create-payment-table" author="family-hobbies-team">
        <preConditions onFail="MARK_RAN">
            <not>
                <tableExists tableName="t_payment"/>
            </not>
        </preConditions>

        <createTable tableName="t_payment">
            <column name="id" type="BIGINT" autoIncrement="true">
                <constraints primaryKey="true" primaryKeyName="pk_payment" nullable="false"/>
            </column>
            <column name="family_id" type="BIGINT">
                <constraints nullable="false"/>
            </column>
            <column name="subscription_id" type="BIGINT">
                <constraints nullable="false"/>
            </column>
            <column name="amount" type="DECIMAL(10,2)">
                <constraints nullable="false"/>
            </column>
            <column name="currency" type="VARCHAR(3)" defaultValue="EUR">
                <constraints nullable="false"/>
            </column>
            <column name="status" type="VARCHAR(20)" defaultValue="PENDING">
                <constraints nullable="false"/>
            </column>
            <column name="payment_method" type="VARCHAR(30)"/>
            <column name="helloasso_checkout_id" type="VARCHAR(100)"/>
            <column name="helloasso_payment_id" type="VARCHAR(100)"/>
            <column name="description" type="VARCHAR(255)"/>
            <column name="paid_at" type="TIMESTAMPTZ"/>
            <column name="failed_at" type="TIMESTAMPTZ"/>
            <column name="refunded_at" type="TIMESTAMPTZ"/>
            <column name="created_at" type="TIMESTAMPTZ" defaultValueComputed="NOW()">
                <constraints nullable="false"/>
            </column>
            <column name="updated_at" type="TIMESTAMPTZ" defaultValueComputed="NOW()">
                <constraints nullable="false"/>
            </column>
        </createTable>

        <addCheckConstraint tableName="t_payment"
                            constraintName="chk_payment_amount"
                            constraintBody="amount &gt; 0"/>

        <createIndex tableName="t_payment" indexName="idx_payment_family_id">
            <column name="family_id"/>
        </createIndex>
        <createIndex tableName="t_payment" indexName="idx_payment_status">
            <column name="status"/>
        </createIndex>
        <createIndex tableName="t_payment" indexName="idx_payment_helloasso_checkout_id">
            <column name="helloasso_checkout_id"/>
        </createIndex>
        <createIndex tableName="t_payment" indexName="idx_payment_subscription_id">
            <column name="subscription_id"/>
        </createIndex>
    </changeSet>

</databaseChangeLog>
```

- **Verify**: `mvn liquibase:update -pl backend/payment-service` -> table `t_payment` created with all columns, indexes, and check constraint

---

## Task 5 Detail: Liquibase 002 -- Create t_invoice Table

- **What**: Liquibase changeset creating the `t_invoice` table with FK to `t_payment`, unique invoice_number, and check constraint on amounts
- **Where**: `backend/payment-service/src/main/resources/db/changesets/002-create-invoice-table.xml`
- **Why**: Invoices are generated after successful payments. FK to t_payment enforces referential integrity.
- **Content**:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">

    <changeSet id="002-create-invoice-table" author="family-hobbies-team">
        <preConditions onFail="MARK_RAN">
            <not>
                <tableExists tableName="t_invoice"/>
            </not>
        </preConditions>

        <createTable tableName="t_invoice">
            <column name="id" type="BIGINT" autoIncrement="true">
                <constraints primaryKey="true" primaryKeyName="pk_invoice" nullable="false"/>
            </column>
            <column name="payment_id" type="BIGINT">
                <constraints nullable="false"/>
            </column>
            <column name="invoice_number" type="VARCHAR(20)">
                <constraints nullable="false" unique="true" uniqueConstraintName="uq_invoice_number"/>
            </column>
            <column name="family_id" type="BIGINT">
                <constraints nullable="false"/>
            </column>
            <column name="association_id" type="BIGINT">
                <constraints nullable="false"/>
            </column>
            <column name="amount_ht" type="DECIMAL(10,2)">
                <constraints nullable="false"/>
            </column>
            <column name="tax_amount" type="DECIMAL(10,2)" defaultValueNumeric="0">
                <constraints nullable="false"/>
            </column>
            <column name="amount_ttc" type="DECIMAL(10,2)">
                <constraints nullable="false"/>
            </column>
            <column name="status" type="VARCHAR(20)" defaultValue="DRAFT">
                <constraints nullable="false"/>
            </column>
            <column name="issued_at" type="TIMESTAMPTZ"/>
            <column name="created_at" type="TIMESTAMPTZ" defaultValueComputed="NOW()">
                <constraints nullable="false"/>
            </column>
            <column name="updated_at" type="TIMESTAMPTZ" defaultValueComputed="NOW()">
                <constraints nullable="false"/>
            </column>
        </createTable>

        <addForeignKeyConstraint baseTableName="t_invoice"
                                 baseColumnNames="payment_id"
                                 referencedTableName="t_payment"
                                 referencedColumnNames="id"
                                 constraintName="fk_invoice_payment"
                                 onDelete="RESTRICT"/>

        <addCheckConstraint tableName="t_invoice"
                            constraintName="chk_invoice_amounts"
                            constraintBody="amount_ht &gt;= 0 AND tax_amount &gt;= 0 AND amount_ttc = amount_ht + tax_amount"/>
    </changeSet>

</databaseChangeLog>
```

- **Verify**: `mvn liquibase:update -pl backend/payment-service` -> table `t_invoice` created with FK, unique constraint, and check constraint

---

## Task 6 Detail: Payment JPA Entity

- **What**: JPA entity mapping the `t_payment` table with all 15 columns, enum mappings, and audit timestamps
- **Where**: `backend/payment-service/src/main/java/com/familyhobbies/paymentservice/entity/Payment.java`
- **Why**: Core domain entity for all payment operations. Used by PaymentRepository, PaymentService, and webhook handler.
- **Content**:

```java
package com.familyhobbies.paymentservice.entity;

import com.familyhobbies.paymentservice.enums.PaymentMethod;
import com.familyhobbies.paymentservice.enums.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
 * JPA entity mapping {@code t_payment} table in the
 * {@code familyhobbies_payments} database.
 *
 * <p>Lifecycle: created in PENDING status when a checkout is initiated.
 * Updated to AUTHORIZED/COMPLETED/FAILED/REFUNDED by the webhook handler
 * (S5-005) based on HelloAsso callbacks.
 */
@Entity
@Table(name = "t_payment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "family_id", nullable = false)
    private Long familyId;

    @Column(name = "subscription_id", nullable = false)
    private Long subscriptionId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "EUR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 30)
    private PaymentMethod paymentMethod;

    @Column(name = "helloasso_checkout_id", length = 100)
    private String helloassoCheckoutId;

    @Column(name = "helloasso_payment_id", length = 100)
    private String helloassoPaymentId;

    @Column(length = 255)
    private String description;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "refunded_at")
    private Instant refundedAt;

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

- **Verify**: `mvn compile -pl backend/payment-service` -> compiles; Hibernate schema validation passes

---

## Task 7 Detail: Invoice JPA Entity

- **What**: JPA entity mapping the `t_invoice` table with FK to Payment and all 12 columns
- **Where**: `backend/payment-service/src/main/java/com/familyhobbies/paymentservice/entity/Invoice.java`
- **Why**: Invoices are generated after successful payments. The FK relationship ensures every invoice is linked to a payment.
- **Content**:

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
 * in the format {@code FHM-YYYYMMDD-NNNNN}.
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

- **Verify**: `mvn compile -pl backend/payment-service` -> compiles

---

## Task 8 Detail: PaymentRepository

- **What**: Spring Data JPA repository for `Payment` with custom query methods for family lookups and HelloAsso ID lookups
- **Where**: `backend/payment-service/src/main/java/com/familyhobbies/paymentservice/repository/PaymentRepository.java`
- **Why**: Used by PaymentServiceImpl for all payment persistence operations. The `findByHelloassoCheckoutId` method is critical for the webhook handler in S5-005.
- **Content**:

```java
package com.familyhobbies.paymentservice.repository;

import com.familyhobbies.paymentservice.entity.Payment;
import com.familyhobbies.paymentservice.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Payment}.
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /** Find payment by HelloAsso checkout ID (webhook correlation). */
    Optional<Payment> findByHelloassoCheckoutId(String helloassoCheckoutId);

    /** Find payment by HelloAsso payment ID. */
    Optional<Payment> findByHelloassoPaymentId(String helloassoPaymentId);

    /** Check if a completed payment already exists for a subscription. */
    boolean existsBySubscriptionIdAndStatus(Long subscriptionId,
                                             PaymentStatus status);

    /** Find all payments for a family with optional filters. */
    @Query("""
            SELECT p FROM Payment p
            WHERE p.familyId = :familyId
              AND (:status IS NULL OR p.status = :status)
              AND (:from IS NULL OR p.createdAt >= :from)
              AND (:to IS NULL OR p.createdAt <= :to)
            ORDER BY p.createdAt DESC
            """)
    Page<Payment> findByFamilyIdWithFilters(
            @Param("familyId") Long familyId,
            @Param("status") PaymentStatus status,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);

    /** Find all payments for a subscription. */
    Page<Payment> findBySubscriptionIdOrderByCreatedAtDesc(
            Long subscriptionId, Pageable pageable);
}
```

- **Verify**: `mvn compile -pl backend/payment-service` -> compiles

---

## Task 9 Detail: InvoiceRepository

- **What**: Spring Data JPA repository for `Invoice`
- **Where**: `backend/payment-service/src/main/java/com/familyhobbies/paymentservice/repository/InvoiceRepository.java`
- **Why**: Used by PaymentServiceImpl for invoice generation and lookups
- **Content**:

```java
package com.familyhobbies.paymentservice.repository;

import com.familyhobbies.paymentservice.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Invoice}.
 */
@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    /** Find invoice by unique invoice number. */
    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    /** Find invoice linked to a specific payment. */
    Optional<Invoice> findByPaymentId(Long paymentId);
}
```

- **Verify**: `mvn compile -pl backend/payment-service` -> compiles

---

## Task 10 Detail: CheckoutRequest DTO

- **What**: Validated request DTO for checkout initiation containing subscription reference, amount, and HelloAsso redirect URLs
- **Where**: `backend/payment-service/src/main/java/com/familyhobbies/paymentservice/dto/request/CheckoutRequest.java`
- **Why**: Received by `POST /api/v1/payments/checkout`. Validated at controller level via `@Valid`.
- **Content**:

```java
package com.familyhobbies.paymentservice.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Request DTO for initiating a HelloAsso checkout session.
 *
 * <p>Example JSON:
 * <pre>
 * {
 *   "subscriptionId": 42,
 *   "amount": 150.00,
 *   "description": "Cotisation annuelle - Club de Danse de Lyon",
 *   "paymentType": "FULL",
 *   "returnUrl": "https://familyhobbies.fr/payments/success",
 *   "cancelUrl": "https://familyhobbies.fr/payments/cancel"
 * }
 * </pre>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckoutRequest {

    @NotNull(message = "L'identifiant d'abonnement est requis")
    private Long subscriptionId;

    @NotNull(message = "Le montant est requis")
    @DecimalMin(value = "0.01", message = "Le montant doit etre superieur a 0")
    private BigDecimal amount;

    private String description;

    @NotBlank(message = "Le type de paiement est requis")
    @Pattern(regexp = "FULL|INSTALLMENT_3X|INSTALLMENT_10X",
             message = "Type de paiement invalide: FULL, INSTALLMENT_3X, ou INSTALLMENT_10X")
    private String paymentType;

    @NotBlank(message = "L'URL de retour est requise")
    private String returnUrl;

    @NotBlank(message = "L'URL d'annulation est requise")
    private String cancelUrl;
}
```

- **Verify**: `mvn compile -pl backend/payment-service` -> compiles

---

## Task 11 Detail: CheckoutResponse DTO

- **What**: Response DTO returned after successful checkout initiation, containing the HelloAsso checkout URL
- **Where**: `backend/payment-service/src/main/java/com/familyhobbies/paymentservice/dto/response/CheckoutResponse.java`
- **Why**: Returned by `POST /api/v1/payments/checkout` so the frontend can redirect to HelloAsso
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
 * Response DTO for checkout initiation.
 *
 * <p>Contains the HelloAsso checkout URL that the frontend uses to redirect
 * the user to the HelloAsso payment page.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckoutResponse {

    private Long paymentId;
    private Long subscriptionId;
    private BigDecimal amount;
    private String paymentType;
    private String status;
    private String checkoutUrl;
    private String helloassoCheckoutId;
    private Instant expiresAt;
    private Instant createdAt;
}
```

- **Verify**: `mvn compile -pl backend/payment-service` -> compiles

---

## Task 12 Detail: PaymentResponse DTO

- **What**: Full payment detail response DTO for `GET /api/v1/payments/{id}`
- **Where**: `backend/payment-service/src/main/java/com/familyhobbies/paymentservice/dto/response/PaymentResponse.java`
- **Why**: Returned by payment detail and listing endpoints
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
 * Full payment detail response DTO.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponse {

    private Long id;
    private Long subscriptionId;
    private Long familyId;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String paymentMethod;
    private String helloassoCheckoutId;
    private String helloassoPaymentId;
    private String description;
    private Instant paidAt;
    private Instant failedAt;
    private Instant refundedAt;
    private Long invoiceId;
    private Instant createdAt;
    private Instant updatedAt;
}
```

- **Verify**: `mvn compile -pl backend/payment-service` -> compiles

---

## Task 13 Detail: HelloAssoCheckoutClient Adapter

- **What**: WebClient-based adapter for initiating HelloAsso checkout sessions. Uses Bearer token from HelloAssoTokenManager-style config (reuses the same pattern from association-service but within payment-service's own config).
- **Where**: `backend/payment-service/src/main/java/com/familyhobbies/paymentservice/adapter/HelloAssoCheckoutClient.java`
- **Why**: Encapsulates all HelloAsso checkout API calls. Called by PaymentServiceImpl to create checkout sessions.
- **Content**:

```java
package com.familyhobbies.paymentservice.adapter;

import com.familyhobbies.errorhandling.exception.container.ExternalApiException;
import com.familyhobbies.paymentservice.config.HelloAssoProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Adapter for the HelloAsso Checkout API.
 *
 * <p>Initiates checkout sessions by calling the HelloAsso v5 checkout
 * endpoint. Returns a checkout URL and session ID that are stored on the
 * local {@code Payment} entity.
 *
 * <p>Authentication: uses OAuth2 client_credentials token via the
 * same token management pattern as association-service. The token is
 * obtained by calling the token endpoint and stored in memory.
 *
 * <p>Error handling: all non-2xx responses mapped to
 * {@link ExternalApiException}.
 */
@Component
public class HelloAssoCheckoutClient {

    private static final Logger log =
            LoggerFactory.getLogger(HelloAssoCheckoutClient.class);

    private final WebClient webClient;
    private final HelloAssoProperties properties;

    /** In-memory OAuth2 token state. */
    private String accessToken;
    private Instant tokenExpiresAt;
    private static final int REFRESH_BUFFER_SECONDS = 60;

    public HelloAssoCheckoutClient(WebClient.Builder webClientBuilder,
                                    HelloAssoProperties properties) {
        this.properties = properties;
        this.webClient = webClientBuilder
                .baseUrl(properties.getBaseUrl())
                .build();
    }

    /**
     * Initiates a HelloAsso checkout session for the given organization.
     *
     * @param orgSlug       HelloAsso organization slug
     * @param amount        payment amount in cents (HelloAsso uses centimes)
     * @param description   payment description
     * @param backUrl       URL to redirect after payment
     * @param errorUrl      URL to redirect on cancellation
     * @param returnUrl     URL to redirect on success
     * @return checkout response with redirect URL and session ID
     * @throws ExternalApiException on any HelloAsso API error
     */
    public HelloAssoCheckoutResponse initiateCheckout(
            String orgSlug,
            int amount,
            String description,
            String backUrl,
            String errorUrl,
            String returnUrl) {

        log.info("Initiating HelloAsso checkout: org={}, amount={} centimes",
                orgSlug, amount);

        HelloAssoCheckoutRequest request = new HelloAssoCheckoutRequest(
                amount, description, backUrl, errorUrl, returnUrl);

        HelloAssoCheckoutResponse response;
        try {
            response = webClient.post()
                    .uri("/organizations/{slug}/checkout-intents", orgSlug)
                    .header(HttpHeaders.AUTHORIZATION,
                            "Bearer " + getValidToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError,
                            this::handle4xxError)
                    .onStatus(HttpStatusCode::is5xxServerError,
                            this::handle5xxError)
                    .bodyToMono(HelloAssoCheckoutResponse.class)
                    .block();
        } catch (ExternalApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ExternalApiException(
                    "HelloAsso checkout request failed: " + ex.getMessage(),
                    "HelloAsso", 0, ex);
        }

        if (response == null) {
            throw new ExternalApiException(
                    "HelloAsso checkout response is null",
                    "HelloAsso", 0);
        }

        log.info("HelloAsso checkout created: id={}, redirectUrl={}",
                response.id(), response.redirectUrl());

        return response;
    }

    // ── OAuth2 Token Management ──────────────────────────────────────────

    private synchronized String getValidToken() {
        if (isTokenExpiredOrAboutToExpire()) {
            refreshToken();
        }
        return accessToken;
    }

    private boolean isTokenExpiredOrAboutToExpire() {
        return accessToken == null
                || tokenExpiresAt == null
                || Instant.now().isAfter(
                        tokenExpiresAt.minusSeconds(REFRESH_BUFFER_SECONDS));
    }

    private void refreshToken() {
        log.debug("Refreshing HelloAsso OAuth2 token for payment-service");

        TokenResponse tokenResponse;
        try {
            tokenResponse = WebClient.create(properties.getTokenUrl())
                    .post()
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters
                            .fromFormData("grant_type", "client_credentials")
                            .with("client_id", properties.getClientId())
                            .with("client_secret", properties.getClientSecret()))
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError()
                                    || status.is5xxServerError(),
                            resp -> resp.bodyToMono(String.class)
                                    .defaultIfEmpty("No response body")
                                    .flatMap(body -> Mono.error(
                                            new ExternalApiException(
                                                    "Token request failed: "
                                                            + body,
                                                    "HelloAsso",
                                                    resp.statusCode().value()
                                            ))))
                    .bodyToMono(TokenResponse.class)
                    .block();
        } catch (ExternalApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ExternalApiException(
                    "HelloAsso token request failed: " + ex.getMessage(),
                    "HelloAsso", 0, ex);
        }

        if (tokenResponse == null
                || tokenResponse.accessToken() == null) {
            throw new ExternalApiException(
                    "HelloAsso token response is null or missing access_token",
                    "HelloAsso", 0);
        }

        this.accessToken = tokenResponse.accessToken();
        this.tokenExpiresAt =
                Instant.now().plusSeconds(tokenResponse.expiresIn());
        log.info("HelloAsso token refreshed for payment-service, "
                + "expires at {}", tokenExpiresAt);
    }

    // ── Error Handling ───────────────────────────────────────────────────

    private Mono<? extends Throwable> handle4xxError(
            ClientResponse response) {
        return response.bodyToMono(String.class)
                .defaultIfEmpty("No response body")
                .flatMap(body -> {
                    int statusCode = response.statusCode().value();
                    log.warn("HelloAsso checkout 4xx: status={}, body={}",
                            statusCode, body);
                    return Mono.error(new ExternalApiException(
                            "HelloAsso checkout error: "
                                    + statusCode + " - " + body,
                            "HelloAsso", statusCode));
                });
    }

    private Mono<? extends Throwable> handle5xxError(
            ClientResponse response) {
        return response.bodyToMono(String.class)
                .defaultIfEmpty("No response body")
                .flatMap(body -> {
                    int statusCode = response.statusCode().value();
                    log.error("HelloAsso checkout 5xx: status={}, body={}",
                            statusCode, body);
                    return Mono.error(new ExternalApiException(
                            "HelloAsso checkout server error: "
                                    + statusCode + " - " + body,
                            "HelloAsso", statusCode));
                });
    }

    // ── Inner DTOs ───────────────────────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record HelloAssoCheckoutRequest(
            @JsonProperty("totalAmount") int totalAmount,
            @JsonProperty("itemName") String itemName,
            @JsonProperty("backUrl") String backUrl,
            @JsonProperty("errorUrl") String errorUrl,
            @JsonProperty("returnUrl") String returnUrl
    ) {}

    public record HelloAssoCheckoutResponse(
            @JsonProperty("id") String id,
            @JsonProperty("redirectUrl") String redirectUrl
    ) {}

    record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") long expiresIn
    ) {}
}
```

- **Verify**: `mvn compile -pl backend/payment-service` -> compiles; MockWebServer tests in companion file pass

---

## Task 14 Detail: PaymentMapper

- **What**: Mapper component converting between Payment entity and DTOs
- **Where**: `backend/payment-service/src/main/java/com/familyhobbies/paymentservice/mapper/PaymentMapper.java`
- **Why**: Isolates mapping logic from service layer. Used by PaymentServiceImpl for all entity-to-DTO conversions.
- **Content**:

```java
package com.familyhobbies.paymentservice.mapper;

import com.familyhobbies.paymentservice.dto.request.CheckoutRequest;
import com.familyhobbies.paymentservice.dto.response.CheckoutResponse;
import com.familyhobbies.paymentservice.dto.response.PaymentResponse;
import com.familyhobbies.paymentservice.entity.Invoice;
import com.familyhobbies.paymentservice.entity.Payment;
import com.familyhobbies.paymentservice.enums.PaymentStatus;
import org.springframework.stereotype.Component;

/**
 * Maps between {@link Payment} entity and request/response DTOs.
 */
@Component
public class PaymentMapper {

    /**
     * Creates a new {@link Payment} entity from a checkout request.
     * Sets initial status to PENDING and currency to EUR.
     *
     * @param request  the checkout request DTO
     * @param familyId the authenticated family's ID
     * @return a new Payment entity (not yet persisted)
     */
    public Payment fromCheckoutRequest(CheckoutRequest request,
                                        Long familyId) {
        return Payment.builder()
                .familyId(familyId)
                .subscriptionId(request.getSubscriptionId())
                .amount(request.getAmount())
                .currency("EUR")
                .status(PaymentStatus.PENDING)
                .description(request.getDescription())
                .build();
    }

    /**
     * Maps a {@link Payment} entity to a {@link CheckoutResponse} DTO
     * with the HelloAsso checkout URL.
     *
     * @param payment     the persisted payment entity
     * @param checkoutUrl the HelloAsso redirect URL
     * @return the checkout response DTO
     */
    public CheckoutResponse toCheckoutResponse(Payment payment,
                                                String checkoutUrl) {
        return CheckoutResponse.builder()
                .paymentId(payment.getId())
                .subscriptionId(payment.getSubscriptionId())
                .amount(payment.getAmount())
                .paymentType(payment.getDescription())
                .status(payment.getStatus().name())
                .checkoutUrl(checkoutUrl)
                .helloassoCheckoutId(payment.getHelloassoCheckoutId())
                .createdAt(payment.getCreatedAt())
                .build();
    }

    /**
     * Maps a {@link Payment} entity to a full {@link PaymentResponse} DTO.
     *
     * @param payment the payment entity
     * @param invoice optional associated invoice (may be null)
     * @return the payment response DTO
     */
    public PaymentResponse toPaymentResponse(Payment payment,
                                              Invoice invoice) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .subscriptionId(payment.getSubscriptionId())
                .familyId(payment.getFamilyId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus().name())
                .paymentMethod(payment.getPaymentMethod() != null
                        ? payment.getPaymentMethod().name() : null)
                .helloassoCheckoutId(payment.getHelloassoCheckoutId())
                .helloassoPaymentId(payment.getHelloassoPaymentId())
                .description(payment.getDescription())
                .paidAt(payment.getPaidAt())
                .failedAt(payment.getFailedAt())
                .refundedAt(payment.getRefundedAt())
                .invoiceId(invoice != null ? invoice.getId() : null)
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }
}
```

- **Verify**: `mvn compile -pl backend/payment-service` -> compiles

---

## Task 15 Detail: PaymentService Interface

- **What**: Service interface defining the payment operations contract
- **Where**: `backend/payment-service/src/main/java/com/familyhobbies/paymentservice/service/PaymentService.java`
- **Why**: Follows the interface/impl pattern. Allows mocking in controller tests.
- **Content**:

```java
package com.familyhobbies.paymentservice.service;

import com.familyhobbies.paymentservice.dto.request.CheckoutRequest;
import com.familyhobbies.paymentservice.dto.response.CheckoutResponse;
import com.familyhobbies.paymentservice.dto.response.PaymentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;

/**
 * Service interface for payment operations.
 */
public interface PaymentService {

    /**
     * Initiates a checkout session with HelloAsso and creates a local
     * Payment record in PENDING status.
     *
     * @param request  the checkout request
     * @param familyId the authenticated family's ID
     * @return the checkout response with HelloAsso redirect URL
     */
    CheckoutResponse initiateCheckout(CheckoutRequest request, Long familyId);

    /**
     * Retrieves a payment by ID with authorization check.
     *
     * @param paymentId the payment ID
     * @param familyId  the requesting family's ID (null for ADMIN)
     * @param isAdmin   whether the requester has ADMIN role
     * @return the payment response DTO
     */
    PaymentResponse getPayment(Long paymentId, Long familyId, boolean isAdmin);

    /**
     * Lists payments for a family with optional filters.
     *
     * @param familyId the family ID
     * @param status   optional status filter
     * @param from     optional date range start
     * @param to       optional date range end
     * @param pageable pagination parameters
     * @return paginated payment responses
     */
    Page<PaymentResponse> getPaymentsByFamily(Long familyId, String status,
                                               Instant from, Instant to,
                                               Pageable pageable);
}
```

- **Verify**: `mvn compile -pl backend/payment-service` -> compiles

---

## Task 16 Detail: PaymentServiceImpl

- **What**: Full implementation of PaymentService orchestrating checkout creation, HelloAsso API calls, and payment queries
- **Where**: `backend/payment-service/src/main/java/com/familyhobbies/paymentservice/service/impl/PaymentServiceImpl.java`
- **Why**: Core business logic for payment operations. Validates business rules, calls HelloAssoCheckoutClient, persists payment records.
- **Content**:

```java
package com.familyhobbies.paymentservice.service.impl;

import com.familyhobbies.errorhandling.exception.web.BadRequestException;
import com.familyhobbies.errorhandling.exception.web.ConflictException;
import com.familyhobbies.errorhandling.exception.web.ForbiddenException;
import com.familyhobbies.errorhandling.exception.web.ResourceNotFoundException;
import com.familyhobbies.paymentservice.adapter.HelloAssoCheckoutClient;
import com.familyhobbies.paymentservice.adapter.HelloAssoCheckoutClient
        .HelloAssoCheckoutResponse;
import com.familyhobbies.paymentservice.dto.request.CheckoutRequest;
import com.familyhobbies.paymentservice.dto.response.CheckoutResponse;
import com.familyhobbies.paymentservice.dto.response.PaymentResponse;
import com.familyhobbies.paymentservice.entity.Invoice;
import com.familyhobbies.paymentservice.entity.Payment;
import com.familyhobbies.paymentservice.enums.PaymentStatus;
import com.familyhobbies.paymentservice.mapper.PaymentMapper;
import com.familyhobbies.paymentservice.repository.InvoiceRepository;
import com.familyhobbies.paymentservice.repository.PaymentRepository;
import com.familyhobbies.paymentservice.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Implementation of {@link PaymentService}.
 *
 * <p>Orchestrates:
 * <ol>
 *   <li>Business rule validation (duplicate check, amount validation)</li>
 *   <li>Local Payment entity creation (PENDING status)</li>
 *   <li>HelloAsso checkout session initiation via
 *       {@link HelloAssoCheckoutClient}</li>
 *   <li>Storing the HelloAsso checkout ID on the Payment entity</li>
 *   <li>Returning the checkout URL to the caller</li>
 * </ol>
 *
 * <p>The payment lifecycle is completed by the webhook handler (S5-005)
 * which updates the payment status based on HelloAsso callbacks.
 */
@Service
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log =
            LoggerFactory.getLogger(PaymentServiceImpl.class);

    /** Default HelloAsso org slug for the platform. */
    private static final String DEFAULT_ORG_SLUG = "family-hobbies-manager";

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final HelloAssoCheckoutClient checkoutClient;
    private final PaymentMapper paymentMapper;

    public PaymentServiceImpl(PaymentRepository paymentRepository,
                               InvoiceRepository invoiceRepository,
                               HelloAssoCheckoutClient checkoutClient,
                               PaymentMapper paymentMapper) {
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.checkoutClient = checkoutClient;
        this.paymentMapper = paymentMapper;
    }

    @Override
    @Transactional
    public CheckoutResponse initiateCheckout(CheckoutRequest request,
                                              Long familyId) {
        log.info("Initiating checkout: familyId={}, subscriptionId={}, "
                        + "amount={}",
                familyId, request.getSubscriptionId(), request.getAmount());

        validateCheckoutRequest(request);

        // Check for duplicate payment
        boolean alreadyPaid = paymentRepository
                .existsBySubscriptionIdAndStatus(
                        request.getSubscriptionId(),
                        PaymentStatus.COMPLETED);
        if (alreadyPaid) {
            throw new ConflictException(
                    "Subscription " + request.getSubscriptionId()
                            + " has already been paid");
        }

        // Create local payment in PENDING status
        Payment payment = paymentMapper.fromCheckoutRequest(request, familyId);
        payment = paymentRepository.save(payment);
        log.debug("Payment created: id={}, status={}",
                payment.getId(), payment.getStatus());

        // Initiate HelloAsso checkout
        int amountInCentimes = request.getAmount()
                .multiply(java.math.BigDecimal.valueOf(100))
                .intValue();

        HelloAssoCheckoutResponse helloAssoResponse =
                checkoutClient.initiateCheckout(
                        DEFAULT_ORG_SLUG,
                        amountInCentimes,
                        request.getDescription(),
                        request.getCancelUrl(),
                        request.getCancelUrl(),
                        request.getReturnUrl());

        // Store HelloAsso checkout ID
        payment.setHelloassoCheckoutId(helloAssoResponse.id());
        payment = paymentRepository.save(payment);
        log.info("Payment {} linked to HelloAsso checkout {}",
                payment.getId(), helloAssoResponse.id());

        return paymentMapper.toCheckoutResponse(
                payment, helloAssoResponse.redirectUrl());
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPayment(Long paymentId, Long familyId,
                                       boolean isAdmin) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> ResourceNotFoundException.of(
                        "Payment", paymentId));

        if (!isAdmin && !payment.getFamilyId().equals(familyId)) {
            throw new ForbiddenException(
                    "Access denied to payment " + paymentId
                            + " for family " + familyId);
        }

        Invoice invoice = invoiceRepository.findByPaymentId(paymentId)
                .orElse(null);

        return paymentMapper.toPaymentResponse(payment, invoice);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentResponse> getPaymentsByFamily(
            Long familyId, String status, Instant from, Instant to,
            Pageable pageable) {

        PaymentStatus statusEnum = null;
        if (status != null && !status.isBlank()) {
            try {
                statusEnum = PaymentStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException(
                        "Invalid payment status: " + status);
            }
        }

        Page<Payment> payments = paymentRepository
                .findByFamilyIdWithFilters(familyId, statusEnum, from, to,
                        pageable);

        return payments.map(payment -> {
            Invoice invoice = invoiceRepository.findByPaymentId(payment.getId())
                    .orElse(null);
            return paymentMapper.toPaymentResponse(payment, invoice);
        });
    }

    // ── Private helpers ──────────────────────────────────────────────────

    private void validateCheckoutRequest(CheckoutRequest request) {
        if (request.getAmount() == null
                || request.getAmount().compareTo(
                        java.math.BigDecimal.ZERO) <= 0) {
            throw new BadRequestException(
                    "Payment amount must be greater than 0");
        }
    }
}
```

- **Verify**: `mvn compile -pl backend/payment-service` -> compiles; unit tests in companion file pass

---

## Task 17 Detail: HelloAssoProperties Config

- **What**: `@ConfigurationProperties` class binding `helloasso.*` YAML keys for payment-service
- **Where**: `backend/payment-service/src/main/java/com/familyhobbies/paymentservice/config/HelloAssoProperties.java`
- **Why**: Same pattern as association-service (S5-001) but within payment-service's own config namespace. Provides base URL, credentials, and webhook secret.
- **Content**:

```java
package com.familyhobbies.paymentservice.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Binds all {@code helloasso.*} configuration properties for payment-service.
 */
@Component
@ConfigurationProperties(prefix = "helloasso")
@Validated
@Getter
@Setter
public class HelloAssoProperties {

    @NotBlank
    private String baseUrl;

    @NotBlank
    private String clientId;

    @NotBlank
    private String clientSecret;

    @NotBlank
    private String tokenUrl;

    /** Webhook HMAC signature secret. */
    private String webhookSecret;

    @Positive
    private int connectTimeout = 5000;

    @Positive
    private int readTimeout = 10000;
}
```

- **Verify**: `mvn compile -pl backend/payment-service` -> compiles

---

## Task 18 Detail: WebClientConfig

- **What**: Spring Configuration producing a `WebClient.Builder` bean with Netty timeouts
- **Where**: `backend/payment-service/src/main/java/com/familyhobbies/paymentservice/config/WebClientConfig.java`
- **Why**: Shared WebClient.Builder for HelloAssoCheckoutClient
- **Content**:

```java
package com.familyhobbies.paymentservice.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Configures {@link WebClient.Builder} for payment-service HTTP calls.
 */
@Configuration
public class WebClientConfig {

    private static final Logger httpLog =
            LoggerFactory.getLogger("PaymentHttpClient");

    private final HelloAssoProperties properties;

    public WebClientConfig(HelloAssoProperties properties) {
        this.properties = properties;
    }

    @Bean
    public WebClient.Builder webClientBuilder() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        properties.getConnectTimeout())
                .responseTimeout(Duration.ofMillis(
                        properties.getReadTimeout()))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(
                                properties.getReadTimeout(),
                                TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(
                                properties.getReadTimeout(),
                                TimeUnit.MILLISECONDS)));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .filter(logRequest())
                .filter(logResponse());
    }

    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            httpLog.debug("Request: {} {}", request.method(), request.url());
            return Mono.just(request);
        });
    }

    private ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(response -> {
            httpLog.debug("Response: status={}", response.statusCode());
            return Mono.just(response);
        });
    }
}
```

- **Verify**: `mvn compile -pl backend/payment-service` -> compiles

---

## Task 19 Detail: SecurityConfig

- **What**: Spring Security configuration allowing public access to the webhook endpoint and requiring JWT for all other payment endpoints
- **Where**: `backend/payment-service/src/main/java/com/familyhobbies/paymentservice/config/SecurityConfig.java`
- **Why**: The webhook endpoint must be publicly accessible (authenticated by HMAC, not JWT). All other endpoints require a valid JWT with FAMILY or ADMIN role.
- **Content**:

```java
package com.familyhobbies.paymentservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration for payment-service.
 *
 * <p>Key decisions:
 * <ul>
 *   <li>Webhook endpoint ({@code /api/v1/payments/webhook/**}) is public
 *       -- authenticated via HMAC signature, not JWT</li>
 *   <li>Actuator endpoints are public (health checks)</li>
 *   <li>All other endpoints require authentication via JWT</li>
 *   <li>CSRF disabled (stateless API)</li>
 *   <li>Sessions disabled (JWT-based auth)</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/payments/webhook/**")
                                .permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .anyRequest().authenticated());

        return http.build();
    }
}
```

- **Verify**: `mvn compile -pl backend/payment-service` -> compiles; webhook endpoint accessible without JWT

---

## Task 20 Detail: PaymentController

- **What**: REST controller exposing payment checkout, detail, and listing endpoints
- **Where**: `backend/payment-service/src/main/java/com/familyhobbies/paymentservice/controller/PaymentController.java`
- **Why**: Public API surface for payment operations. Extracts familyId from JWT headers (forwarded by API Gateway).
- **Content**:

```java
package com.familyhobbies.paymentservice.controller;

import com.familyhobbies.paymentservice.dto.request.CheckoutRequest;
import com.familyhobbies.paymentservice.dto.response.CheckoutResponse;
import com.familyhobbies.paymentservice.dto.response.PaymentResponse;
import com.familyhobbies.paymentservice.service.PaymentService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * REST controller for payment operations.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /api/v1/payments/checkout} -- initiate checkout
 *       (FAMILY role)</li>
 *   <li>{@code GET /api/v1/payments/{id}} -- payment detail
 *       (FAMILY own, ADMIN)</li>
 *   <li>{@code GET /api/v1/payments/family/{familyId}} -- list family payments
 *       (FAMILY own, ADMIN)</li>
 * </ul>
 *
 * <p>Family ID is extracted from the {@code X-User-Id} header forwarded
 * by the API Gateway after JWT validation.
 */
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private static final Logger log =
            LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * Initiates a HelloAsso checkout session.
     *
     * @param request  the checkout request
     * @param userId   family ID from JWT (forwarded by gateway)
     * @return checkout response with redirect URL
     */
    @PostMapping("/checkout")
    @PreAuthorize("hasRole('FAMILY')")
    public ResponseEntity<CheckoutResponse> initiateCheckout(
            @Valid @RequestBody CheckoutRequest request,
            @RequestHeader("X-User-Id") Long userId) {

        log.info("POST /api/v1/payments/checkout: userId={}, "
                + "subscriptionId={}", userId, request.getSubscriptionId());

        CheckoutResponse response =
                paymentService.initiateCheckout(request, userId);

        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves a payment by ID.
     *
     * @param id      the payment ID
     * @param userId  the requesting user's ID
     * @param roles   the user's roles (from gateway)
     * @return payment detail response
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('FAMILY', 'ADMIN')")
    public ResponseEntity<PaymentResponse> getPayment(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Roles") String roles) {

        boolean isAdmin = roles != null
                && roles.toUpperCase().contains("ADMIN");

        PaymentResponse response =
                paymentService.getPayment(id, userId, isAdmin);

        return ResponseEntity.ok(response);
    }

    /**
     * Lists payments for a family with optional filters.
     *
     * @param familyId the family ID
     * @param status   optional status filter
     * @param from     optional date range start
     * @param to       optional date range end
     * @param pageable pagination parameters
     * @param userId   the requesting user's ID
     * @param roles    the user's roles
     * @return paginated payment list
     */
    @GetMapping("/family/{familyId}")
    @PreAuthorize("hasAnyRole('FAMILY', 'ADMIN')")
    public ResponseEntity<Page<PaymentResponse>> getPaymentsByFamily(
            @PathVariable Long familyId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            Pageable pageable,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Roles") String roles) {

        boolean isAdmin = roles != null
                && roles.toUpperCase().contains("ADMIN");

        if (!isAdmin && !familyId.equals(userId)) {
            throw new com.familyhobbies.errorhandling.exception
                    .web.ForbiddenException(
                    "Access denied to family " + familyId
                            + " payments for user " + userId);
        }

        Page<PaymentResponse> payments =
                paymentService.getPaymentsByFamily(
                        familyId, status, from, to, pageable);

        return ResponseEntity.ok(payments);
    }
}
```

- **Verify**: `curl -X POST http://localhost:8083/api/v1/payments/checkout -H "Authorization: Bearer {jwt}" -H "X-User-Id: 1"` -> 200 with checkout URL

---

## Task 21 Detail: application.yml

- **What**: Full Spring Boot configuration for payment-service with HelloAsso, PostgreSQL, Kafka, and Eureka settings
- **Where**: `backend/payment-service/src/main/resources/application.yml`
- **Why**: Externalizes all configuration. All secrets via env vars.
- **Content**:

```yaml
server:
  port: 8083

spring:
  application:
    name: payment-service
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/familyhobbies_payments
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
  liquibase:
    change-log: classpath:db/changelog/db.changelog-master.xml
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer

# HelloAsso API v5 Configuration
helloasso:
  base-url: ${HELLOASSO_BASE_URL:https://api.helloasso-sandbox.com/v5}
  client-id: ${HELLOASSO_CLIENT_ID}
  client-secret: ${HELLOASSO_CLIENT_SECRET}
  token-url: ${HELLOASSO_TOKEN_URL:https://api.helloasso-sandbox.com/oauth2/token}
  webhook-secret: ${HELLOASSO_WEBHOOK_SECRET:}
  connect-timeout: 5000
  read-timeout: 10000

# Eureka Service Discovery
eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_URL:http://localhost:8761/eureka/}
  instance:
    prefer-ip-address: true

# Actuator
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
```

- **Verify**: `mvn spring-boot:run -pl backend/payment-service` with required env vars -> application starts

---

## Failing Tests (TDD Contract)

> **File split**: The full test source code (20+ tests, ~700 lines) is in the companion file
> **[S5-004-payment-entity-checkout-tests.md](./S5-004-payment-entity-checkout-tests.md)** to stay
> under the 1000-line file limit.

**Test files**:
- `backend/payment-service/src/test/java/com/familyhobbies/paymentservice/service/PaymentServiceImplTest.java`
- `backend/payment-service/src/test/java/com/familyhobbies/paymentservice/adapter/HelloAssoCheckoutClientTest.java`
- `backend/payment-service/src/test/java/com/familyhobbies/paymentservice/mapper/PaymentMapperTest.java`

**Test categories (22 tests total)**:

| Category | Tests | What They Verify |
|----------|-------|------------------|
| Checkout Initiation | 4 | Payment created in PENDING, HelloAsso called, checkoutId stored, response correct |
| Duplicate Check | 2 | ConflictException on already-paid subscription, allows retry after failure |
| Payment Query | 3 | Get by ID, family authorization check, ForbiddenException for other family |
| Family Listing | 3 | Paginated results, status filter, date range filter |
| HelloAssoCheckoutClient | 5 | Successful checkout, 4xx/5xx error handling, token refresh, null response |
| PaymentMapper | 5 | fromCheckoutRequest, toCheckoutResponse, toPaymentResponse with/without invoice |

### Required Test Dependencies (pom.xml)

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>com.squareup.okhttp3</groupId>
    <artifactId>mockwebserver</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>com.squareup.okhttp3</groupId>
    <artifactId>okhttp</artifactId>
    <scope>test</scope>
</dependency>
```

### Required Production Dependencies (pom.xml)

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>org.liquibase</groupId>
    <artifactId>liquibase-core</artifactId>
</dependency>
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
</dependency>
<dependency>
    <groupId>com.familyhobbies</groupId>
    <artifactId>common</artifactId>
    <version>${project.version}</version>
</dependency>
```

---

## Acceptance Criteria Checklist

- [ ] Liquibase migrations create `t_payment` and `t_invoice` tables with all constraints and indexes
- [ ] `Payment` entity maps all 15 columns with correct types and enum mappings
- [ ] `Invoice` entity maps all 12 columns with FK to Payment
- [ ] `POST /api/v1/payments/checkout` creates PENDING payment and returns HelloAsso checkout URL
- [ ] Duplicate payment check: ConflictException if subscription already paid
- [ ] `GET /api/v1/payments/{id}` returns full payment detail with authorization check
- [ ] `GET /api/v1/payments/family/{familyId}` returns paginated results with status/date filters
- [ ] ForbiddenException thrown when accessing another family's payment
- [ ] HelloAssoCheckoutClient initiates checkout sessions via WebClient
- [ ] OAuth2 token managed in memory with 60s pre-expiry refresh
- [ ] ExternalApiException thrown on HelloAsso 4xx/5xx errors
- [ ] Webhook endpoint path (`/api/v1/payments/webhook/**`) is public in SecurityConfig
- [ ] All secrets externalized via env vars (never hardcoded)
- [ ] All 22 JUnit 5 tests pass green
