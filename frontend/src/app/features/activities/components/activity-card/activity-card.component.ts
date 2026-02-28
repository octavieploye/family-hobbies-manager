// frontend/src/app/features/activities/components/activity-card/activity-card.component.ts
import { Component, ChangeDetectionStrategy, Input } from '@angular/core';
import { CommonModule, CurrencyPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { Activity } from '@shared/models/activity.model';

/**
 * Standalone card component displaying an activity summary.
 *
 * Displays: name, category, level, price (in euros), session count, age range.
 * Click navigates to the activity detail route.
 */
@Component({
  selector: 'app-activity-card',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatCardModule,
    MatChipsModule,
    MatIconModule,
    CurrencyPipe,
  ],
  templateUrl: './activity-card.component.html',
  styleUrl: './activity-card.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ActivityCardComponent {
  @Input({ required: true }) activity!: Activity;
  @Input({ required: true }) associationId!: number;

  /**
   * Format price from cents to euros string.
   */
  get priceInEuros(): number {
    return this.activity.priceCents / 100;
  }

  /**
   * Build age range display string.
   */
  get ageRange(): string | null {
    if (this.activity.minAge !== null && this.activity.maxAge !== null) {
      return `${this.activity.minAge} - ${this.activity.maxAge} ans`;
    }
    if (this.activity.minAge !== null) {
      return `A partir de ${this.activity.minAge} ans`;
    }
    if (this.activity.maxAge !== null) {
      return `Jusqu'a ${this.activity.maxAge} ans`;
    }
    return null;
  }
}
