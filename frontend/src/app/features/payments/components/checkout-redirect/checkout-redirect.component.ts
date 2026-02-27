// frontend/src/app/features/payments/components/checkout-redirect/checkout-redirect.component.ts
import { Component, ChangeDetectionStrategy, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

/**
 * Possible checkout result states.
 */
type CheckoutResult = 'loading' | 'success' | 'error' | 'cancelled';

/**
 * Standalone component for handling HelloAsso checkout redirect.
 *
 * After the user completes (or cancels) the HelloAsso checkout,
 * they are redirected back to this component with query parameters
 * indicating the result.
 *
 * Features:
 * - Loading spinner for 800ms transition
 * - French messages for each result state
 * - Action buttons: view payment or return to list
 */
@Component({
  selector: 'app-checkout-redirect',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './checkout-redirect.component.html',
  styleUrl: './checkout-redirect.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CheckoutRedirectComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  /** Current result state of the checkout redirect. */
  result = signal<CheckoutResult>('loading');

  /** Payment ID from query params. */
  paymentId = signal<string | null>(null);

  /** Transition delay in ms before showing the result. */
  private readonly transitionDelay = 800;

  ngOnInit(): void {
    const status = this.route.snapshot.queryParamMap.get('status');
    const paymentId = this.route.snapshot.queryParamMap.get('paymentId');

    this.paymentId.set(paymentId);

    // Show loading spinner briefly, then transition to the result
    setTimeout(() => {
      this.resolveResult(status);
    }, this.transitionDelay);
  }

  /**
   * Navigate to payment detail page.
   */
  viewPayment(): void {
    const id = this.paymentId();
    if (id) {
      this.router.navigate(['/payments', id]);
    }
  }

  /**
   * Navigate back to the payment list.
   */
  goToPayments(): void {
    this.router.navigate(['/payments']);
  }

  /**
   * Resolve the checkout result from the query parameter status.
   */
  private resolveResult(status: string | null): void {
    switch (status) {
      case 'success':
        this.result.set('success');
        break;
      case 'error':
        this.result.set('error');
        break;
      case 'cancelled':
        this.result.set('cancelled');
        break;
      default:
        this.result.set('error');
        break;
    }
  }
}
