// frontend/src/app/features/subscriptions/components/subscribe-dialog/subscribe-dialog.component.ts
import { Component, ChangeDetectionStrategy, inject, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Store } from '@ngrx/store';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { SubscriptionActions } from '../../store/subscription.actions';
import { selectSubscriptionLoading } from '../../store/subscription.selectors';
import { SubscriptionRequest, SubscriptionType } from '@shared/models/subscription.model';
import { FamilyMember } from '../../../family/models/family.model';

/**
 * Dialog data passed when opening the subscribe dialog.
 */
export interface SubscribeDialogData {
  activityId: number;
  activityName: string;
  associationName: string;
  familyMembers?: FamilyMember[];
  familyId?: number;
}

/**
 * Material dialog for subscribing a family member to an activity.
 *
 * Form fields:
 * - Member selector (dropdown of family members)
 * - Subscription type (ADHESION or COTISATION)
 * - Start date
 */
@Component({
  selector: 'app-subscribe-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatDatepickerModule,
    MatNativeDateModule,
  ],
  templateUrl: './subscribe-dialog.component.html',
  styleUrl: './subscribe-dialog.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SubscribeDialogComponent implements OnInit, OnDestroy {
  private readonly store = inject(Store);
  private readonly fb = inject(FormBuilder);
  private readonly dialogRef = inject(MatDialogRef<SubscribeDialogComponent>);
  readonly data: SubscribeDialogData = inject(MAT_DIALOG_DATA);
  private readonly destroy$ = new Subject<void>();

  /** Observable of loading state. */
  loading$ = this.store.select(selectSubscriptionLoading);

  /** Subscription type options. */
  subscriptionTypes: { value: SubscriptionType; label: string }[] = [
    { value: 'ADHESION', label: 'Adh\u00e9sion' },
    { value: 'COTISATION', label: 'Cotisation' },
  ];

  /** Reactive form for subscription. */
  subscribeForm = this.fb.group({
    familyMemberId: [null as number | null, [Validators.required]],
    subscriptionType: ['ADHESION' as SubscriptionType, [Validators.required]],
    startDate: ['', [Validators.required]],
  });

  ngOnInit(): void {
    // Dialog data is injected via MAT_DIALOG_DATA
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  /**
   * Submit the subscription form.
   */
  onSubmit(): void {
    if (this.subscribeForm.invalid) {
      return;
    }

    const formValue = this.subscribeForm.value;
    const request: SubscriptionRequest = {
      activityId: this.data.activityId,
      familyMemberId: formValue.familyMemberId!,
      familyId: this.data.familyId || 0,
      subscriptionType: formValue.subscriptionType as SubscriptionType,
      startDate: formValue.startDate as string,
    };

    this.store.dispatch(SubscriptionActions.createSubscription({ request }));
    this.dialogRef.close(true);
  }

  /**
   * Close the dialog without action.
   */
  onCancel(): void {
    this.dialogRef.close(false);
  }
}
