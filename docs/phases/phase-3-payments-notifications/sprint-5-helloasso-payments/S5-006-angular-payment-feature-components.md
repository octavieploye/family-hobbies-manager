# Story S5-006: Angular Payment Feature -- Components + SCSS

> Companion file to [S5-006-angular-payment-feature.md](./S5-006-angular-payment-feature.md)
> Contains the full source code for PaymentListComponent, PaymentDetailComponent, CheckoutRedirectComponent, and all SCSS styles.

---

## Task 8 Detail: PaymentListComponent

- **What**: Angular Material table displaying the family's payments with status chip filters and pagination
- **Where**: `frontend/src/app/features/payments/components/payment-list/`
- **Why**: Primary payment view -- families see all their payments across all associations in one place
- **Content**:

### payment-list.component.ts

```typescript
// frontend/src/app/features/payments/components/payment-list/payment-list.component.ts

import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { Store } from '@ngrx/store';
import { Subject, takeUntil } from 'rxjs';
import { MatTableModule } from '@angular/material/table';
import { MatChipsModule } from '@angular/material/chips';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import {
  PaymentSummary,
  PaymentStatus,
  PAYMENT_STATUS_CONFIG,
} from '../../../../shared/models/payment.model';
import { PaymentActions } from '../../store/payment.actions';
import {
  selectPayments,
  selectTotalElements,
  selectCurrentPage,
  selectPageSize,
  selectListLoading,
  selectStatusFilter,
  selectError,
} from '../../store/payment.selectors';

@Component({
  selector: 'app-payment-list',
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatChipsModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatFormFieldModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
  ],
  templateUrl: './payment-list.component.html',
  styleUrls: ['./payment-list.component.scss'],
})
export class PaymentListComponent implements OnInit, OnDestroy {
  readonly displayedColumns = [
    'createdAt',
    'familyMemberName',
    'associationName',
    'activityName',
    'amount',
    'status',
    'actions',
  ];

  readonly statusOptions = Object.values(PaymentStatus);
  readonly statusConfig = PAYMENT_STATUS_CONFIG;

  payments$ = this.store.select(selectPayments);
  totalElements$ = this.store.select(selectTotalElements);
  currentPage$ = this.store.select(selectCurrentPage);
  pageSize$ = this.store.select(selectPageSize);
  loading$ = this.store.select(selectListLoading);
  statusFilter$ = this.store.select(selectStatusFilter);
  error$ = this.store.select(selectError);

  /** Family ID — in production, read from auth state. Hardcoded for demo. */
  private readonly familyId = 'famille-dupont-001';
  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly store: Store,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    this.store.dispatch(
      PaymentActions.loadPayments({ familyId: this.familyId })
    );
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  onStatusFilterChange(status: PaymentStatus | null): void {
    this.store.dispatch(PaymentActions.setStatusFilter({ status }));
    this.store.dispatch(
      PaymentActions.loadPayments({ familyId: this.familyId, page: 0 })
    );
  }

  onPageChange(event: PageEvent): void {
    this.store.dispatch(
      PaymentActions.loadPayments({
        familyId: this.familyId,
        page: event.pageIndex,
        size: event.pageSize,
      })
    );
  }

  viewDetail(payment: PaymentSummary): void {
    this.router.navigate(['/payments', payment.id]);
  }

  getStatusClass(status: PaymentStatus): string {
    const classMap: Record<PaymentStatus, string> = {
      [PaymentStatus.PENDING]: 'payment-list__chip--pending',
      [PaymentStatus.AUTHORIZED]: 'payment-list__chip--authorized',
      [PaymentStatus.COMPLETED]: 'payment-list__chip--completed',
      [PaymentStatus.FAILED]: 'payment-list__chip--failed',
      [PaymentStatus.REFUNDED]: 'payment-list__chip--refunded',
      [PaymentStatus.CANCELLED]: 'payment-list__chip--cancelled',
    };
    return classMap[status] || '';
  }

  formatAmount(amount: number): string {
    return new Intl.NumberFormat('fr-FR', {
      style: 'currency',
      currency: 'EUR',
    }).format(amount / 100);
  }

  formatDate(dateStr: string): string {
    return new Date(dateStr).toLocaleDateString('fr-FR', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    });
  }
}
```

### payment-list.component.html

```html
<!-- frontend/src/app/features/payments/components/payment-list/payment-list.component.html -->

<div class="payment-list">
  <div class="payment-list__header">
    <h1 class="payment-list__title">Mes paiements</h1>

    <mat-form-field class="payment-list__filter" appearance="outline">
      <mat-label>Filtrer par statut</mat-label>
      <mat-select
        [value]="statusFilter$ | async"
        (selectionChange)="onStatusFilterChange($event.value)"
      >
        <mat-option [value]="null">Tous les statuts</mat-option>
        @for (status of statusOptions; track status) {
          <mat-option [value]="status">
            {{ statusConfig[status].label }}
          </mat-option>
        }
      </mat-select>
    </mat-form-field>
  </div>

  @if (loading$ | async) {
    <div class="payment-list__spinner">
      <mat-spinner diameter="48"></mat-spinner>
      <p>Chargement des paiements...</p>
    </div>
  }

  @if (error$ | async; as errorMsg) {
    <div class="payment-list__error" role="alert">
      <mat-icon>error_outline</mat-icon>
      <span>{{ errorMsg }}</span>
    </div>
  }

  @if ((payments$ | async); as payments) {
    @if (!((loading$ | async)) && payments.length === 0) {
      <div class="payment-list__empty">
        <mat-icon>payment</mat-icon>
        <p>Aucun paiement trouvé.</p>
      </div>
    }

    @if (payments.length > 0) {
      <table mat-table [dataSource]="payments" class="payment-list__table">
        <!-- Date Column -->
        <ng-container matColumnDef="createdAt">
          <th mat-header-cell *matHeaderCellDef>Date</th>
          <td mat-cell *matCellDef="let payment">
            {{ formatDate(payment.createdAt) }}
          </td>
        </ng-container>

        <!-- Member Column -->
        <ng-container matColumnDef="familyMemberName">
          <th mat-header-cell *matHeaderCellDef>Membre</th>
          <td mat-cell *matCellDef="let payment">
            {{ payment.familyMemberName }}
          </td>
        </ng-container>

        <!-- Association Column -->
        <ng-container matColumnDef="associationName">
          <th mat-header-cell *matHeaderCellDef>Association</th>
          <td mat-cell *matCellDef="let payment">
            {{ payment.associationName }}
          </td>
        </ng-container>

        <!-- Activity Column -->
        <ng-container matColumnDef="activityName">
          <th mat-header-cell *matHeaderCellDef>Activité</th>
          <td mat-cell *matCellDef="let payment">
            {{ payment.activityName }}
          </td>
        </ng-container>

        <!-- Amount Column -->
        <ng-container matColumnDef="amount">
          <th mat-header-cell *matHeaderCellDef>Montant</th>
          <td mat-cell *matCellDef="let payment">
            {{ formatAmount(payment.amount) }}
          </td>
        </ng-container>

        <!-- Status Column -->
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Statut</th>
          <td mat-cell *matCellDef="let payment">
            <mat-chip [ngClass]="getStatusClass(payment.status)">
              {{ statusConfig[payment.status].label }}
            </mat-chip>
          </td>
        </ng-container>

        <!-- Actions Column -->
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef>Actions</th>
          <td mat-cell *matCellDef="let payment">
            <button
              mat-icon-button
              matTooltip="Voir le détail"
              (click)="viewDetail(payment)"
              aria-label="Voir le détail du paiement"
            >
              <mat-icon>visibility</mat-icon>
            </button>
          </td>
        </ng-container>

        <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
        <tr
          mat-row
          *matRowDef="let row; columns: displayedColumns"
          class="payment-list__row"
        ></tr>
      </table>

      <mat-paginator
        [length]="totalElements$ | async"
        [pageIndex]="(currentPage$ | async) ?? 0"
        [pageSize]="(pageSize$ | async) ?? 20"
        [pageSizeOptions]="[10, 20, 50]"
        (page)="onPageChange($event)"
        aria-label="Pagination des paiements"
      >
      </mat-paginator>
    }
  }
</div>
```

- **Verify**: `cd frontend && npx ng test --include='**/payment-list.component.spec.ts'` -> tests run (fail TDD)

---

## Task 9 Detail: PaymentDetailComponent

- **What**: Full payment information view with status timeline, subscription link, and invoice link
- **Where**: `frontend/src/app/features/payments/components/payment-detail/`
- **Why**: Allows family members to see all details of a specific payment including HelloAsso references
- **Content**:

### payment-detail.component.ts

```typescript
// frontend/src/app/features/payments/components/payment-detail/payment-detail.component.ts

import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { Subject, takeUntil } from 'rxjs';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDividerModule } from '@angular/material/divider';
import {
  PaymentDetail,
  PaymentStatus,
  PAYMENT_STATUS_CONFIG,
} from '../../../../shared/models/payment.model';
import { PaymentActions } from '../../store/payment.actions';
import {
  selectSelectedPayment,
  selectDetailLoading,
  selectError,
} from '../../store/payment.selectors';

@Component({
  selector: 'app-payment-detail',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatCardModule,
    MatChipsModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatDividerModule,
  ],
  templateUrl: './payment-detail.component.html',
  styleUrls: ['./payment-detail.component.scss'],
})
export class PaymentDetailComponent implements OnInit, OnDestroy {
  readonly statusConfig = PAYMENT_STATUS_CONFIG;

  payment$ = this.store.select(selectSelectedPayment);
  loading$ = this.store.select(selectDetailLoading);
  error$ = this.store.select(selectError);

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly store: Store,
    private readonly route: ActivatedRoute,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    this.route.paramMap
      .pipe(takeUntil(this.destroy$))
      .subscribe((params) => {
        const paymentId = params.get('id');
        if (paymentId) {
          this.store.dispatch(
            PaymentActions.loadPaymentDetail({ paymentId })
          );
        }
      });
  }

  ngOnDestroy(): void {
    this.store.dispatch(PaymentActions.clearSelectedPayment());
    this.destroy$.next();
    this.destroy$.complete();
  }

  getStatusClass(status: PaymentStatus): string {
    const classMap: Record<string, string> = {
      PENDING: 'payment-detail__chip--pending',
      AUTHORIZED: 'payment-detail__chip--authorized',
      COMPLETED: 'payment-detail__chip--completed',
      FAILED: 'payment-detail__chip--failed',
      REFUNDED: 'payment-detail__chip--refunded',
      CANCELLED: 'payment-detail__chip--cancelled',
    };
    return classMap[status] || '';
  }

  formatAmount(amount: number): string {
    return new Intl.NumberFormat('fr-FR', {
      style: 'currency',
      currency: 'EUR',
    }).format(amount / 100);
  }

  formatDateTime(dateStr: string | null): string {
    if (!dateStr) return '--';
    return new Date(dateStr).toLocaleString('fr-FR', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  }

  goBack(): void {
    this.router.navigate(['/payments']);
  }

  /**
   * Returns the ordered status timeline steps for display.
   * Completed statuses appear as filled, current status is highlighted.
   */
  getStatusTimeline(payment: PaymentDetail): { status: string; label: string; reached: boolean }[] {
    const steps = [
      { status: 'PENDING', label: 'Initié', reached: true },
      { status: 'AUTHORIZED', label: 'Autorisé', reached: false },
      { status: 'COMPLETED', label: 'Terminé', reached: false },
    ];

    const statusOrder = ['PENDING', 'AUTHORIZED', 'COMPLETED'];
    const currentIndex = statusOrder.indexOf(payment.status);

    if (payment.status === 'FAILED') {
      return [
        { status: 'PENDING', label: 'Initié', reached: true },
        { status: 'FAILED', label: 'Échoué', reached: true },
      ];
    }

    if (payment.status === 'REFUNDED') {
      return [
        { status: 'PENDING', label: 'Initié', reached: true },
        { status: 'COMPLETED', label: 'Terminé', reached: true },
        { status: 'REFUNDED', label: 'Remboursé', reached: true },
      ];
    }

    return steps.map((step, index) => ({
      ...step,
      reached: index <= currentIndex,
    }));
  }
}
```

### payment-detail.component.html

```html
<!-- frontend/src/app/features/payments/components/payment-detail/payment-detail.component.html -->

<div class="payment-detail">
  <button mat-button (click)="goBack()" class="payment-detail__back">
    <mat-icon>arrow_back</mat-icon>
    Retour aux paiements
  </button>

  @if (loading$ | async) {
    <div class="payment-detail__spinner">
      <mat-spinner diameter="48"></mat-spinner>
      <p>Chargement du paiement...</p>
    </div>
  }

  @if (error$ | async; as errorMsg) {
    <div class="payment-detail__error" role="alert">
      <mat-icon>error_outline</mat-icon>
      <span>{{ errorMsg }}</span>
    </div>
  }

  @if (payment$ | async; as payment) {
    <!-- Status Timeline -->
    <div class="payment-detail__timeline">
      @for (step of getStatusTimeline(payment); track step.status) {
        <div
          class="payment-detail__timeline-step"
          [class.payment-detail__timeline-step--reached]="step.reached"
          [class.payment-detail__timeline-step--failed]="step.status === 'FAILED'"
        >
          <div class="payment-detail__timeline-dot"></div>
          <span class="payment-detail__timeline-label">{{ step.label }}</span>
        </div>
      }
    </div>

    <mat-card class="payment-detail__card">
      <mat-card-header>
        <mat-card-title>
          {{ payment.activityName }} -- {{ payment.associationName }}
        </mat-card-title>
        <mat-card-subtitle>
          Paiement du {{ formatDateTime(payment.createdAt) }}
        </mat-card-subtitle>
      </mat-card-header>

      <mat-card-content>
        <div class="payment-detail__grid">
          <div class="payment-detail__field">
            <span class="payment-detail__label">Montant</span>
            <span class="payment-detail__value payment-detail__value--amount">
              {{ formatAmount(payment.amount) }}
            </span>
          </div>

          <div class="payment-detail__field">
            <span class="payment-detail__label">Statut</span>
            <mat-chip [ngClass]="getStatusClass(payment.status)">
              {{ statusConfig[payment.status].label }}
            </mat-chip>
          </div>

          <div class="payment-detail__field">
            <span class="payment-detail__label">Membre</span>
            <span class="payment-detail__value">{{ payment.familyMemberName }}</span>
          </div>

          <div class="payment-detail__field">
            <span class="payment-detail__label">Mode de paiement</span>
            <span class="payment-detail__value">
              {{ payment.paymentMethod || '--' }}
            </span>
          </div>

          <div class="payment-detail__field">
            <span class="payment-detail__label">Date de paiement</span>
            <span class="payment-detail__value">
              {{ formatDateTime(payment.paidAt) }}
            </span>
          </div>

          <div class="payment-detail__field">
            <span class="payment-detail__label">Dernière mise à jour</span>
            <span class="payment-detail__value">
              {{ formatDateTime(payment.updatedAt) }}
            </span>
          </div>
        </div>

        <mat-divider></mat-divider>

        <div class="payment-detail__references">
          <h3>Références</h3>

          <div class="payment-detail__field">
            <span class="payment-detail__label">ID paiement</span>
            <span class="payment-detail__value payment-detail__value--mono">
              {{ payment.id }}
            </span>
          </div>

          @if (payment.helloassoCheckoutId) {
            <div class="payment-detail__field">
              <span class="payment-detail__label">Référence HelloAsso (checkout)</span>
              <span class="payment-detail__value payment-detail__value--mono">
                {{ payment.helloassoCheckoutId }}
              </span>
            </div>
          }

          @if (payment.helloassoPaymentId) {
            <div class="payment-detail__field">
              <span class="payment-detail__label">Référence HelloAsso (paiement)</span>
              <span class="payment-detail__value payment-detail__value--mono">
                {{ payment.helloassoPaymentId }}
              </span>
            </div>
          }

          @if (payment.subscriptionId) {
            <div class="payment-detail__field">
              <span class="payment-detail__label">Abonnement</span>
              <a
                [routerLink]="['/subscriptions', payment.subscriptionId]"
                class="payment-detail__link"
              >
                Voir l'abonnement
              </a>
            </div>
          }

          @if (payment.invoiceId) {
            <div class="payment-detail__field">
              <span class="payment-detail__label">Facture</span>
              <a
                [routerLink]="['/invoices', payment.invoiceId]"
                class="payment-detail__link"
              >
                Télécharger la facture
              </a>
            </div>
          }
        </div>
      </mat-card-content>
    </mat-card>
  }
</div>
```

- **Verify**: `cd frontend && npx ng test --include='**/payment-detail.component.spec.ts'` -> tests run (fail TDD)

---

## Task 10 Detail: CheckoutRedirectComponent

- **What**: Intermediate page handling the HelloAsso checkout return flow
- **Where**: `frontend/src/app/features/payments/components/checkout-redirect/`
- **Why**: After the user pays on HelloAsso, they are redirected back to our app. This component reads the query parameters (status=success/error, paymentId) and displays the appropriate result message with a link to the payment detail or back to the list.
- **Content**:

### checkout-redirect.component.ts

```typescript
// frontend/src/app/features/payments/components/checkout-redirect/checkout-redirect.component.ts

import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

export type CheckoutResult = 'loading' | 'success' | 'error' | 'cancelled';

@Component({
  selector: 'app-checkout-redirect',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './checkout-redirect.component.html',
  styleUrls: ['./checkout-redirect.component.scss'],
})
export class CheckoutRedirectComponent implements OnInit {
  result: CheckoutResult = 'loading';
  paymentId: string | null = null;

  readonly messages: Record<Exclude<CheckoutResult, 'loading'>, { title: string; body: string; icon: string }> = {
    success: {
      title: 'Paiement réussi',
      body: 'Votre paiement a été effectué avec succès. Vous recevrez une confirmation par e-mail.',
      icon: 'check_circle',
    },
    error: {
      title: 'Paiement échoué',
      body: 'Une erreur est survenue lors du paiement. Aucun montant n\'a été débité. Vous pouvez réessayer.',
      icon: 'error',
    },
    cancelled: {
      title: 'Paiement annulé',
      body: 'Vous avez annulé le paiement. Aucun montant n\'a été débité.',
      icon: 'cancel',
    },
  };

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    const queryParams = this.route.snapshot.queryParams;
    const status = queryParams['status'];
    this.paymentId = queryParams['paymentId'] || null;

    // Small delay to show the loading spinner (UX feedback)
    setTimeout(() => {
      if (status === 'success') {
        this.result = 'success';
      } else if (status === 'error') {
        this.result = 'error';
      } else if (status === 'cancelled') {
        this.result = 'cancelled';
      } else {
        // Unknown status — treat as error
        this.result = 'error';
      }
    }, 800);
  }

  viewPaymentDetail(): void {
    if (this.paymentId) {
      this.router.navigate(['/payments', this.paymentId]);
    }
  }

  goToPayments(): void {
    this.router.navigate(['/payments']);
  }
}
```

### checkout-redirect.component.html

```html
<!-- frontend/src/app/features/payments/components/checkout-redirect/checkout-redirect.component.html -->

<div class="checkout-redirect">
  @if (result === 'loading') {
    <div class="checkout-redirect__loading">
      <mat-spinner diameter="64"></mat-spinner>
      <h2>Traitement du paiement en cours...</h2>
      <p>Veuillez patienter, nous vérifions votre paiement auprès de HelloAsso.</p>
    </div>
  } @else {
    <mat-card class="checkout-redirect__card" [ngClass]="'checkout-redirect__card--' + result">
      <mat-card-content>
        <div class="checkout-redirect__icon">
          <mat-icon
            [ngClass]="{
              'checkout-redirect__icon--success': result === 'success',
              'checkout-redirect__icon--error': result === 'error',
              'checkout-redirect__icon--cancelled': result === 'cancelled'
            }"
          >
            {{ messages[result].icon }}
          </mat-icon>
        </div>

        <h2 class="checkout-redirect__title">{{ messages[result].title }}</h2>
        <p class="checkout-redirect__body">{{ messages[result].body }}</p>

        <div class="checkout-redirect__actions">
          @if (result === 'success' && paymentId) {
            <button
              mat-raised-button
              color="primary"
              (click)="viewPaymentDetail()"
            >
              <mat-icon>receipt</mat-icon>
              Voir le détail du paiement
            </button>
          }

          <button mat-stroked-button (click)="goToPayments()">
            <mat-icon>list</mat-icon>
            Retour aux paiements
          </button>
        </div>
      </mat-card-content>
    </mat-card>
  }
</div>
```

- **Verify**: `cd frontend && npx ng test --include='**/checkout-redirect.component.spec.ts'` -> tests run (fail TDD)

---

## Task 12 Detail: SCSS Styles

- **What**: BEM-methodology SCSS for all three payment components
- **Where**: Component-specific `.scss` files
- **Why**: Consistent visual styling matching the Angular Material theme with custom status chip colors

### payment-list.component.scss

```scss
// frontend/src/app/features/payments/components/payment-list/payment-list.component.scss

.payment-list {
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px;

  &__header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 24px;
    flex-wrap: wrap;
    gap: 16px;
  }

  &__title {
    font-size: 24px;
    font-weight: 500;
    margin: 0;
    color: rgba(0, 0, 0, 0.87);
  }

  &__filter {
    min-width: 220px;
  }

  &__table {
    width: 100%;
    margin-bottom: 16px;
  }

  &__row {
    cursor: pointer;

    &:hover {
      background-color: rgba(0, 0, 0, 0.04);
    }
  }

  &__spinner {
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 48px 0;
    color: rgba(0, 0, 0, 0.54);
  }

  &__error {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 16px;
    background-color: #fdecea;
    border-radius: 4px;
    color: #611a15;
    margin-bottom: 16px;
  }

  &__empty {
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 48px 0;
    color: rgba(0, 0, 0, 0.54);

    mat-icon {
      font-size: 48px;
      width: 48px;
      height: 48px;
      margin-bottom: 16px;
    }
  }

  // Status chip colors (BEM modifiers)
  &__chip {
    &--pending {
      background-color: #fff3e0 !important;
      color: #e65100 !important;
    }

    &--authorized {
      background-color: #e3f2fd !important;
      color: #1565c0 !important;
    }

    &--completed {
      background-color: #e8f5e9 !important;
      color: #2e7d32 !important;
    }

    &--failed {
      background-color: #fdecea !important;
      color: #c62828 !important;
    }

    &--refunded {
      background-color: #f5f5f5 !important;
      color: #616161 !important;
    }

    &--cancelled {
      background-color: #f5f5f5 !important;
      color: #9e9e9e !important;
    }
  }
}
```

### payment-detail.component.scss

```scss
// frontend/src/app/features/payments/components/payment-detail/payment-detail.component.scss

.payment-detail {
  max-width: 800px;
  margin: 0 auto;
  padding: 24px;

  &__back {
    margin-bottom: 16px;
  }

  &__spinner {
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 48px 0;
    color: rgba(0, 0, 0, 0.54);
  }

  &__error {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 16px;
    background-color: #fdecea;
    border-radius: 4px;
    color: #611a15;
    margin-bottom: 16px;
  }

  &__timeline {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 0;
    margin-bottom: 32px;
    padding: 24px 0;
  }

  &__timeline-step {
    display: flex;
    flex-direction: column;
    align-items: center;
    position: relative;
    min-width: 100px;

    &:not(:last-child)::after {
      content: '';
      position: absolute;
      top: 12px;
      left: calc(50% + 16px);
      width: calc(100% - 32px);
      height: 2px;
      background-color: #e0e0e0;
    }

    &--reached:not(:last-child)::after {
      background-color: #4caf50;
    }

    &--failed &__timeline-dot {
      background-color: #f44336;
    }
  }

  &__timeline-dot {
    width: 24px;
    height: 24px;
    border-radius: 50%;
    background-color: #e0e0e0;
    margin-bottom: 8px;
    z-index: 1;

    .payment-detail__timeline-step--reached & {
      background-color: #4caf50;
    }

    .payment-detail__timeline-step--failed & {
      background-color: #f44336;
    }
  }

  &__timeline-label {
    font-size: 12px;
    color: rgba(0, 0, 0, 0.54);
    text-align: center;

    .payment-detail__timeline-step--reached & {
      color: rgba(0, 0, 0, 0.87);
      font-weight: 500;
    }
  }

  &__card {
    margin-bottom: 24px;
  }

  &__grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
    gap: 24px;
    padding: 16px 0;
  }

  &__field {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  &__label {
    font-size: 12px;
    font-weight: 500;
    color: rgba(0, 0, 0, 0.54);
    text-transform: uppercase;
    letter-spacing: 0.5px;
  }

  &__value {
    font-size: 16px;
    color: rgba(0, 0, 0, 0.87);

    &--amount {
      font-size: 24px;
      font-weight: 600;
      color: #1565c0;
    }

    &--mono {
      font-family: 'Roboto Mono', monospace;
      font-size: 13px;
      background-color: #f5f5f5;
      padding: 4px 8px;
      border-radius: 4px;
      word-break: break-all;
    }
  }

  &__references {
    padding: 16px 0;

    h3 {
      font-size: 16px;
      font-weight: 500;
      margin-bottom: 16px;
      color: rgba(0, 0, 0, 0.87);
    }
  }

  &__link {
    color: #1565c0;
    text-decoration: none;
    font-weight: 500;

    &:hover {
      text-decoration: underline;
    }
  }

  // Status chip colors (same as list)
  &__chip {
    &--pending { background-color: #fff3e0 !important; color: #e65100 !important; }
    &--authorized { background-color: #e3f2fd !important; color: #1565c0 !important; }
    &--completed { background-color: #e8f5e9 !important; color: #2e7d32 !important; }
    &--failed { background-color: #fdecea !important; color: #c62828 !important; }
    &--refunded { background-color: #f5f5f5 !important; color: #616161 !important; }
    &--cancelled { background-color: #f5f5f5 !important; color: #9e9e9e !important; }
  }
}
```

### checkout-redirect.component.scss

```scss
// frontend/src/app/features/payments/components/checkout-redirect/checkout-redirect.component.scss

.checkout-redirect {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: calc(100vh - 128px);
  padding: 24px;

  &__loading {
    display: flex;
    flex-direction: column;
    align-items: center;
    text-align: center;

    h2 {
      margin-top: 24px;
      font-size: 20px;
      font-weight: 500;
      color: rgba(0, 0, 0, 0.87);
    }

    p {
      color: rgba(0, 0, 0, 0.54);
      max-width: 400px;
    }
  }

  &__card {
    max-width: 500px;
    width: 100%;
    text-align: center;
    padding: 32px;

    &--success {
      border-top: 4px solid #4caf50;
    }

    &--error {
      border-top: 4px solid #f44336;
    }

    &--cancelled {
      border-top: 4px solid #ff9800;
    }
  }

  &__icon {
    margin-bottom: 16px;

    mat-icon {
      font-size: 64px;
      width: 64px;
      height: 64px;
    }

    &--success mat-icon {
      color: #4caf50;
    }

    &--error mat-icon {
      color: #f44336;
    }

    &--cancelled mat-icon {
      color: #ff9800;
    }
  }

  &__title {
    font-size: 24px;
    font-weight: 500;
    margin-bottom: 8px;
    color: rgba(0, 0, 0, 0.87);
  }

  &__body {
    font-size: 16px;
    color: rgba(0, 0, 0, 0.54);
    margin-bottom: 32px;
    line-height: 1.5;
  }

  &__actions {
    display: flex;
    flex-direction: column;
    gap: 12px;
    align-items: center;
  }
}
```

- **Verify**: Visual inspection -- status chips display correct colors, layout is responsive
