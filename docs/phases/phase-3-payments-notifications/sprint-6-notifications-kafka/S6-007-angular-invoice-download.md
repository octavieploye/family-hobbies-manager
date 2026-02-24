# Story S6-007: Implement Angular Invoice Download

> 3 points | Priority: P2 | Service: frontend
> Sprint file: [Back to Sprint Index](./_index.md)

---

## Context

The invoice generation backend (S6-006) creates PDF invoices when payments complete and exposes REST endpoints for listing and downloading them. This story builds the Angular frontend that lets families view their invoices in a dedicated list and download PDFs directly from the browser. The feature also adds an invoice section to the existing payment detail view so users see invoice information inline. Blob download handling ensures the browser save dialog opens cleanly for PDF files. All UI text is in French.

## Cross-References

- **S6-006** (Invoice Generation) defines the InvoiceController endpoints consumed here
- **S5-006** (Angular Payment Feature, Sprint 5) provides the existing `PaymentDetailComponent` to extend
- **S5-004** (Payment API, Sprint 5) defines the payment-service base URL and `PaymentService`

---

## Tasks

| # | Task | File Path | What To Create | How To Verify |
|---|------|-----------|----------------|---------------|
| 1 | Create invoice model | `frontend/src/app/shared/models/invoice.model.ts` | TypeScript interfaces and enums for invoice data | File compiles, types match API contract |
| 2 | Create InvoiceService | `frontend/src/app/features/invoices/services/invoice.service.ts` | HTTP service for invoice endpoints with blob download | Jest test: service methods call correct URLs |
| 3 | Create InvoiceListComponent | `frontend/src/app/features/invoices/pages/invoice-list/` | Full page with Material table of invoices | Jest test: table renders rows |
| 4 | Create InvoiceSectionComponent | `frontend/src/app/features/invoices/components/invoice-section/` | Embedded section for payment detail page | Jest test: renders when invoice exists |
| 5 | Create invoice routes | `frontend/src/app/features/invoices/invoices.routes.ts` | Route configuration for `/invoices` | Route navigates to list page |
| 6 | Integrate into app | App routing + payment detail template | Wire routes and embed invoice section | Invoice list accessible, section visible in payment detail |

---

## Task 1 Detail: Create Invoice Model

- **What**: TypeScript interfaces and enums matching the invoice API contract from S6-006
- **Where**: `frontend/src/app/shared/models/invoice.model.ts`
- **Why**: Shared types used by InvoiceService and all invoice components
- **Content**:

```typescript
// frontend/src/app/shared/models/invoice.model.ts

export enum InvoiceStatus {
  DRAFT = 'DRAFT',
  ISSUED = 'ISSUED',
  PAID = 'PAID',
  CANCELLED = 'CANCELLED'
}

export interface LineItemResponse {
  description: string;
  quantity: number;
  unitPrice: number;
  total: number;
}

export interface InvoiceResponse {
  id: number;
  invoiceNumber: string;
  paymentId: number;
  subscriptionId: number;
  familyId: number;
  familyName: string;
  associationName: string;
  activityName: string;
  familyMemberName: string;
  season: string;
  lineItems: LineItemResponse[];
  subtotal: number;
  tax: number;
  total: number;
  currency: string;
  status: InvoiceStatus;
  issuedAt: string;
  paidAt: string | null;
  payerEmail: string;
  payerName: string;
  downloadUrl: string;
  createdAt: string;
}

export interface InvoiceSummaryResponse {
  id: number;
  invoiceNumber: string;
  associationName: string;
  activityName: string;
  familyMemberName: string;
  season: string;
  total: number;
  currency: string;
  status: InvoiceStatus;
  issuedAt: string;
  paidAt: string | null;
  downloadUrl: string;
}

export interface InvoiceQueryParams {
  season?: string;
  status?: InvoiceStatus;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
}

/**
 * Maps InvoiceStatus to French labels and colors.
 */
export const INVOICE_STATUS_CONFIG: Record<
  InvoiceStatus,
  { label: string; color: string; cssClass: string }
> = {
  [InvoiceStatus.DRAFT]: {
    label: 'Brouillon',
    color: '#9E9E9E',
    cssClass: 'invoice-status--draft'
  },
  [InvoiceStatus.ISSUED]: {
    label: 'Emise',
    color: '#1565C0',
    cssClass: 'invoice-status--issued'
  },
  [InvoiceStatus.PAID]: {
    label: 'Payee',
    color: '#2E7D32',
    cssClass: 'invoice-status--paid'
  },
  [InvoiceStatus.CANCELLED]: {
    label: 'Annulee',
    color: '#C62828',
    cssClass: 'invoice-status--cancelled'
  }
};
```

- **Verify**: `npx tsc --noEmit` passes with no errors on this file

---

## Task 2 Detail: Create InvoiceService

- **What**: Angular HTTP service for listing invoices and downloading PDFs as blobs
- **Where**: `frontend/src/app/features/invoices/services/invoice.service.ts`
- **Why**: Centralized API access for invoice list and binary download
- **Content**:

```typescript
// frontend/src/app/features/invoices/services/invoice.service.ts

import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import {
  InvoiceResponse,
  InvoiceSummaryResponse,
  InvoiceQueryParams,
  PageResponse
} from '../../../shared/models/invoice.model';
import { environment } from '../../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class InvoiceService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/api/v1/invoices`;

  /**
   * Fetches a single invoice by ID.
   */
  getInvoice(invoiceId: number): Observable<InvoiceResponse> {
    return this.http.get<InvoiceResponse>(`${this.baseUrl}/${invoiceId}`);
  }

  /**
   * Fetches paginated invoices for a family.
   */
  getMyInvoices(
    familyId: number,
    params: InvoiceQueryParams = {}
  ): Observable<PageResponse<InvoiceSummaryResponse>> {
    let httpParams = new HttpParams();

    if (params.season) {
      httpParams = httpParams.set('season', params.season);
    }
    if (params.status) {
      httpParams = httpParams.set('status', params.status);
    }
    if (params.from) {
      httpParams = httpParams.set('from', params.from);
    }
    if (params.to) {
      httpParams = httpParams.set('to', params.to);
    }
    if (params.page !== undefined) {
      httpParams = httpParams.set('page', String(params.page));
    }
    if (params.size !== undefined) {
      httpParams = httpParams.set('size', String(params.size));
    }
    if (params.sort) {
      httpParams = httpParams.set('sort', params.sort);
    }

    return this.http.get<PageResponse<InvoiceSummaryResponse>>(
      `${this.baseUrl}/family/${familyId}`,
      { params: httpParams }
    );
  }

  /**
   * Downloads an invoice PDF as a blob and triggers the browser save dialog.
   */
  downloadInvoice(invoiceId: number): Observable<Blob> {
    return this.http
      .get(`${this.baseUrl}/${invoiceId}/download`, {
        responseType: 'blob',
        observe: 'body'
      })
      .pipe(
        tap((blob) => {
          this.triggerBlobDownload(blob, `facture-${invoiceId}.pdf`);
        })
      );
  }

  /**
   * Creates a temporary anchor element to trigger the browser's save dialog.
   */
  private triggerBlobDownload(blob: Blob, filename: string): void {
    const url = window.URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = filename;
    anchor.style.display = 'none';
    document.body.appendChild(anchor);
    anchor.click();
    document.body.removeChild(anchor);
    window.URL.revokeObjectURL(url);
  }
}
```

- **Verify**: `npx jest --testPathPattern=invoice.service.spec` passes

---

## Task 3 Detail: Create InvoiceListComponent

- **What**: Full page with Material table listing family invoices, status chips, download buttons, pagination
- **Where**: `frontend/src/app/features/invoices/pages/invoice-list/`
- **Why**: Dedicated view for families to browse and download all their invoices
- **Content**:

**invoice-list.component.ts**:
```typescript
// frontend/src/app/features/invoices/pages/invoice-list/invoice-list.component.ts

import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatChipsModule } from '@angular/material/chips';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatTooltipModule } from '@angular/material/tooltip';
import { InvoiceService } from '../../services/invoice.service';
import {
  InvoiceSummaryResponse,
  InvoiceStatus,
  InvoiceQueryParams,
  PageResponse,
  INVOICE_STATUS_CONFIG
} from '../../../../shared/models/invoice.model';

@Component({
  selector: 'app-invoice-list',
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatPaginatorModule,
    MatChipsModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatFormFieldModule,
    MatTooltipModule
  ],
  templateUrl: './invoice-list.component.html',
  styleUrls: ['./invoice-list.component.scss']
})
export class InvoiceListComponent implements OnInit {
  private readonly invoiceService = inject(InvoiceService);

  readonly displayedColumns = [
    'invoiceNumber',
    'associationName',
    'activityName',
    'familyMemberName',
    'season',
    'total',
    'status',
    'issuedAt',
    'actions'
  ];

  readonly statuses = Object.values(InvoiceStatus);
  readonly statusConfig = INVOICE_STATUS_CONFIG;

  invoices: InvoiceSummaryResponse[] = [];
  totalElements = 0;
  currentPage = 0;
  pageSize = 20;
  selectedStatus: InvoiceStatus | null = null;
  loading = false;
  downloading: Record<number, boolean> = {};

  // TODO: Obtain familyId from auth state / user profile store
  private readonly familyId = 1;

  ngOnInit(): void {
    this.loadInvoices();
  }

  loadInvoices(): void {
    this.loading = true;
    const params: InvoiceQueryParams = {
      page: this.currentPage,
      size: this.pageSize,
      sort: 'issuedAt,desc'
    };
    if (this.selectedStatus) {
      params.status = this.selectedStatus;
    }

    this.invoiceService.getMyInvoices(this.familyId, params).subscribe({
      next: (page: PageResponse<InvoiceSummaryResponse>) => {
        this.invoices = page.content;
        this.totalElements = page.totalElements;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      }
    });
  }

  onPageChange(event: PageEvent): void {
    this.currentPage = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadInvoices();
  }

  onStatusFilterChange(status: InvoiceStatus | null): void {
    this.selectedStatus = status;
    this.currentPage = 0;
    this.loadInvoices();
  }

  onDownload(invoice: InvoiceSummaryResponse): void {
    this.downloading[invoice.id] = true;
    this.invoiceService.downloadInvoice(invoice.id).subscribe({
      next: () => {
        this.downloading[invoice.id] = false;
      },
      error: () => {
        this.downloading[invoice.id] = false;
      }
    });
  }

  getStatusClass(invoice: InvoiceSummaryResponse): string {
    return this.statusConfig[invoice.status]?.cssClass || '';
  }

  getStatusLabel(invoice: InvoiceSummaryResponse): string {
    return this.statusConfig[invoice.status]?.label || invoice.status;
  }

  isDownloading(invoice: InvoiceSummaryResponse): boolean {
    return !!this.downloading[invoice.id];
  }
}
```

**invoice-list.component.html**:
```html
<!-- frontend/src/app/features/invoices/pages/invoice-list/invoice-list.component.html -->

<div class="invoice-list">
  <div class="invoice-list__header">
    <h1 class="invoice-list__title">Factures</h1>
  </div>

  <div class="invoice-list__filters">
    <mat-form-field appearance="outline" class="invoice-list__filter">
      <mat-label>Statut</mat-label>
      <mat-select
        [value]="selectedStatus"
        (selectionChange)="onStatusFilterChange($event.value)">
        <mat-option [value]="null">Tous les statuts</mat-option>
        @for (status of statuses; track status) {
          <mat-option [value]="status">
            {{ statusConfig[status].label }}
          </mat-option>
        }
      </mat-select>
    </mat-form-field>
  </div>

  @if (loading) {
    <div class="invoice-list__loading">
      <mat-spinner diameter="48" />
    </div>
  } @else {
    @if (invoices.length > 0) {
      <table mat-table [dataSource]="invoices" class="invoice-list__table">

        <!-- Invoice Number Column -->
        <ng-container matColumnDef="invoiceNumber">
          <th mat-header-cell *matHeaderCellDef>Numero de facture</th>
          <td mat-cell *matCellDef="let invoice">
            {{ invoice.invoiceNumber }}
          </td>
        </ng-container>

        <!-- Association Column -->
        <ng-container matColumnDef="associationName">
          <th mat-header-cell *matHeaderCellDef>Association</th>
          <td mat-cell *matCellDef="let invoice">
            {{ invoice.associationName }}
          </td>
        </ng-container>

        <!-- Activity Column -->
        <ng-container matColumnDef="activityName">
          <th mat-header-cell *matHeaderCellDef>Activite</th>
          <td mat-cell *matCellDef="let invoice">
            {{ invoice.activityName }}
          </td>
        </ng-container>

        <!-- Member Column -->
        <ng-container matColumnDef="familyMemberName">
          <th mat-header-cell *matHeaderCellDef>Membre</th>
          <td mat-cell *matCellDef="let invoice">
            {{ invoice.familyMemberName }}
          </td>
        </ng-container>

        <!-- Season Column -->
        <ng-container matColumnDef="season">
          <th mat-header-cell *matHeaderCellDef>Saison</th>
          <td mat-cell *matCellDef="let invoice">
            {{ invoice.season }}
          </td>
        </ng-container>

        <!-- Total Column -->
        <ng-container matColumnDef="total">
          <th mat-header-cell *matHeaderCellDef>Montant</th>
          <td mat-cell *matCellDef="let invoice">
            {{ invoice.total | currency:invoice.currency:'symbol':'1.2-2':'fr' }}
          </td>
        </ng-container>

        <!-- Status Column -->
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Statut</th>
          <td mat-cell *matCellDef="let invoice">
            <mat-chip [class]="getStatusClass(invoice)">
              {{ getStatusLabel(invoice) }}
            </mat-chip>
          </td>
        </ng-container>

        <!-- Issued Date Column -->
        <ng-container matColumnDef="issuedAt">
          <th mat-header-cell *matHeaderCellDef>Date d'emission</th>
          <td mat-cell *matCellDef="let invoice">
            {{ invoice.issuedAt | date:'dd/MM/yyyy' }}
          </td>
        </ng-container>

        <!-- Actions Column -->
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let invoice">
            <button
              mat-icon-button
              color="primary"
              matTooltip="Telecharger"
              [disabled]="isDownloading(invoice)"
              (click)="onDownload(invoice)">
              @if (isDownloading(invoice)) {
                <mat-spinner diameter="20" />
              } @else {
                <mat-icon>download</mat-icon>
              }
            </button>
          </td>
        </ng-container>

        <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
        <tr mat-row *matRowDef="let row; columns: displayedColumns"></tr>
      </table>

      <mat-paginator
        [length]="totalElements"
        [pageIndex]="currentPage"
        [pageSize]="pageSize"
        [pageSizeOptions]="[10, 20, 50]"
        (page)="onPageChange($event)"
        showFirstLastButtons>
      </mat-paginator>
    } @else {
      <div class="invoice-list__empty">
        <mat-icon>receipt_long</mat-icon>
        <p>Aucune facture</p>
      </div>
    }
  }
</div>
```

**invoice-list.component.scss**:
```scss
// frontend/src/app/features/invoices/pages/invoice-list/invoice-list.component.scss

.invoice-list {
  padding: 24px;
  max-width: 1400px;
  margin: 0 auto;

  &__header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 24px;
  }

  &__title {
    margin: 0;
    font-size: 24px;
    font-weight: 500;
  }

  &__filters {
    display: flex;
    gap: 16px;
    margin-bottom: 16px;
  }

  &__filter {
    min-width: 200px;
  }

  &__loading {
    display: flex;
    justify-content: center;
    padding: 48px;
  }

  &__table {
    width: 100%;
  }

  &__empty {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 16px;
    padding: 64px;
    color: rgba(0, 0, 0, 0.4);

    mat-icon {
      font-size: 48px;
      width: 48px;
      height: 48px;
    }
  }
}

// Invoice status chip colors (BEM modifiers)
.invoice-status {
  &--draft {
    background-color: #f5f5f5 !important;
    color: #9e9e9e !important;
  }

  &--issued {
    background-color: #e3f2fd !important;
    color: #1565c0 !important;
  }

  &--paid {
    background-color: #e8f5e9 !important;
    color: #2e7d32 !important;
  }

  &--cancelled {
    background-color: #ffebee !important;
    color: #c62828 !important;
  }
}
```

- **Verify**: `npx jest --testPathPattern=invoice-list` passes

---

## Task 4 Detail: Create InvoiceSectionComponent

- **What**: Embedded section for the payment detail page showing invoice info and download button
- **Where**: `frontend/src/app/features/invoices/components/invoice-section/`
- **Why**: Inline invoice access within the payment detail view
- **Content**:

**invoice-section.component.ts**:
```typescript
// frontend/src/app/features/invoices/components/invoice-section/invoice-section.component.ts

import { Component, Input, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { InvoiceService } from '../../services/invoice.service';
import {
  InvoiceResponse,
  INVOICE_STATUS_CONFIG
} from '../../../../shared/models/invoice.model';

@Component({
  selector: 'app-invoice-section',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatProgressSpinnerModule
  ],
  templateUrl: './invoice-section.component.html',
  styleUrls: ['./invoice-section.component.scss']
})
export class InvoiceSectionComponent {
  private readonly invoiceService = inject(InvoiceService);

  @Input({ required: true }) invoice!: InvoiceResponse;

  readonly statusConfig = INVOICE_STATUS_CONFIG;
  downloading = false;

  get statusLabel(): string {
    return this.statusConfig[this.invoice.status]?.label || this.invoice.status;
  }

  get statusClass(): string {
    return this.statusConfig[this.invoice.status]?.cssClass || '';
  }

  onDownload(): void {
    this.downloading = true;
    this.invoiceService.downloadInvoice(this.invoice.id).subscribe({
      next: () => {
        this.downloading = false;
      },
      error: () => {
        this.downloading = false;
      }
    });
  }
}
```

**invoice-section.component.html**:
```html
<!-- frontend/src/app/features/invoices/components/invoice-section/invoice-section.component.html -->

<mat-card class="invoice-section">
  <mat-card-header>
    <mat-card-title>Facture</mat-card-title>
  </mat-card-header>

  <mat-card-content>
    <div class="invoice-section__details">
      <div class="invoice-section__row">
        <span class="invoice-section__label">Numero de facture</span>
        <span class="invoice-section__value">{{ invoice.invoiceNumber }}</span>
      </div>

      <div class="invoice-section__row">
        <span class="invoice-section__label">Date d'emission</span>
        <span class="invoice-section__value">
          {{ invoice.issuedAt | date:'dd/MM/yyyy' }}
        </span>
      </div>

      <div class="invoice-section__row">
        <span class="invoice-section__label">Montant</span>
        <span class="invoice-section__value">
          {{ invoice.total | currency:invoice.currency:'symbol':'1.2-2':'fr' }}
        </span>
      </div>

      <div class="invoice-section__row">
        <span class="invoice-section__label">Statut</span>
        <mat-chip [class]="statusClass">{{ statusLabel }}</mat-chip>
      </div>

      @if (invoice.paidAt) {
        <div class="invoice-section__row">
          <span class="invoice-section__label">Date de paiement</span>
          <span class="invoice-section__value">
            {{ invoice.paidAt | date:'dd/MM/yyyy' }}
          </span>
        </div>
      }
    </div>
  </mat-card-content>

  <mat-card-actions align="end">
    <button
      mat-raised-button
      color="primary"
      [disabled]="downloading"
      (click)="onDownload()">
      @if (downloading) {
        <mat-spinner diameter="18" />
      } @else {
        <mat-icon>download</mat-icon>
      }
      Telecharger
    </button>
  </mat-card-actions>
</mat-card>
```

**invoice-section.component.scss**:
```scss
// frontend/src/app/features/invoices/components/invoice-section/invoice-section.component.scss

.invoice-section {
  margin-top: 16px;

  &__details {
    display: flex;
    flex-direction: column;
    gap: 12px;
    padding: 8px 0;
  }

  &__row {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  &__label {
    font-size: 14px;
    color: rgba(0, 0, 0, 0.6);
  }

  &__value {
    font-size: 14px;
    font-weight: 500;
  }
}
```

- **Verify**: `npx jest --testPathPattern=invoice-section` passes

---

## Task 5 Detail: Create Invoice Routes

- **What**: Route configuration for the invoices feature
- **Where**: `frontend/src/app/features/invoices/invoices.routes.ts`
- **Why**: Enables lazy-loaded routing to `/invoices`
- **Content**:

```typescript
// frontend/src/app/features/invoices/invoices.routes.ts

import { Routes } from '@angular/router';

export const INVOICE_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./pages/invoice-list/invoice-list.component').then(
        (m) => m.InvoiceListComponent
      ),
    title: 'Factures'
  }
];
```

- **Verify**: Navigate to `/invoices` in the browser; list page renders

---

## Task 6 Detail: Integrate Into App

- **What**: Register invoice routes in app router and embed InvoiceSectionComponent in payment detail
- **Where**: `frontend/src/app/app.routes.ts` and payment detail template
- **Why**: Connects the invoice feature to the application navigation and payment flow
- **Content**:

**Add to app.routes.ts**:
```typescript
// Add this route to the existing app.routes.ts routes array:
{
  path: 'invoices',
  loadChildren: () =>
    import('./features/invoices/invoices.routes').then(
      (m) => m.INVOICE_ROUTES
    )
}
```

**Add to PaymentDetailComponent template** (e.g., `payment-detail.component.html`):
```html
<!-- Add after the existing payment detail card, conditionally shown when invoice exists -->
@if (payment?.invoice) {
  <app-invoice-section [invoice]="payment.invoice" />
}
```

**Add import to PaymentDetailComponent**:
```typescript
// Add to the PaymentDetailComponent imports array:
import { InvoiceSectionComponent } from '../../invoices/components/invoice-section/invoice-section.component';

// In @Component imports:
imports: [
  // ... existing imports
  InvoiceSectionComponent
]
```

**Add navigation link** (e.g., in sidebar or payments section):
```html
<!-- Add in the navigation/sidebar alongside Payments -->
<a mat-list-item routerLink="/invoices" routerLinkActive="active">
  <mat-icon matListItemIcon>receipt_long</mat-icon>
  <span matListItemTitle>Factures</span>
</a>
```

- **Verify**: `/invoices` route accessible. Invoice section visible in payment detail when invoice exists.

---

## Failing Tests (TDD Contract)

Tests are defined in the companion file: [S6-007-angular-invoice-download-tests.md](./S6-007-angular-invoice-download-tests.md)

The companion file contains the complete Jest test source code for:
1. `invoice.service.spec.ts` -- HTTP service tests (4 tests)
2. `invoice-list.component.spec.ts` -- List page table and download tests (5 tests)
3. `invoice-section.component.spec.ts` -- Embedded section rendering and download tests (5 tests)

---

## Acceptance Criteria

- [ ] Invoice list page accessible at `/invoices`
- [ ] Table shows invoice number, association, activity, member, season, amount, status, date
- [ ] Status chips display correct French labels and colors
- [ ] Download button triggers browser save dialog for PDF
- [ ] Loading spinner shown during download
- [ ] Status filter works (Brouillon, Emise, Payee, Annulee)
- [ ] Pagination works with configurable page size
- [ ] Invoice section shows in payment detail when invoice exists
- [ ] Invoice section displays invoice number, date, amount, status
- [ ] Invoice section download button works
- [ ] All French UI text is correct
- [ ] All Jest tests pass
