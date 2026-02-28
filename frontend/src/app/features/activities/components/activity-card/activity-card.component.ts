// frontend/src/app/features/activities/components/activity-card/activity-card.component.ts
import { Component, ChangeDetectionStrategy, Input } from '@angular/core';
import { CommonModule, CurrencyPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { Activity } from '@shared/models/activity.model';
import { formatAgeRange, formatPriceInEuros } from '@shared/utils/activity-format.utils';

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
   * Format price from cents to euros.
   */
  get priceInEuros(): number {
    return formatPriceInEuros(this.activity.priceCents);
  }

  /**
   * Build age range display string.
   */
  get ageRange(): string | null {
    return formatAgeRange(this.activity.minAge, this.activity.maxAge);
  }
}
