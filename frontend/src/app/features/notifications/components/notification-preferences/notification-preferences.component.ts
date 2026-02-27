// frontend/src/app/features/notifications/components/notification-preferences/notification-preferences.component.ts
import {
  Component,
  ChangeDetectionStrategy,
  OnInit,
  OnDestroy,
  inject,
  signal,
} from '@angular/core';
import { Store } from '@ngrx/store';
import { MatTableModule } from '@angular/material/table';
import { MatSlideToggleModule, MatSlideToggleChange } from '@angular/material/slide-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { NotificationActions } from '../../store/notification.actions';
import {
  selectPreferences,
  selectLoading,
  selectError,
} from '../../store/notification.selectors';
import {
  NotificationCategory,
  NotificationPreference,
  CATEGORY_LABELS,
} from '@shared/models/notification.model';

/**
 * Preferences page for notification settings.
 *
 * Displays a table of notification categories with email/in-app toggle switches.
 * Updates preferences via API on toggle change.
 */
@Component({
  selector: 'app-notification-preferences',
  standalone: true,
  imports: [
    MatTableModule,
    MatSlideToggleModule,
    MatCardModule,
    MatProgressSpinnerModule,
    MatIconModule,
  ],
  templateUrl: './notification-preferences.component.html',
  styleUrl: './notification-preferences.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotificationPreferencesComponent implements OnInit, OnDestroy {
  private readonly store = inject(Store);
  private readonly destroy$ = new Subject<void>();

  /** Signal of preferences from the store. */
  preferences = signal<NotificationPreference[]>([]);

  /** Signal of the loading flag. */
  loading = signal<boolean>(false);

  /** Signal of the current error message. */
  error = signal<string | null>(null);

  /** Columns displayed in the preferences table. */
  displayedColumns: string[] = ['category', 'emailEnabled', 'inAppEnabled'];

  /** Category label map. */
  categoryLabels = CATEGORY_LABELS;

  ngOnInit(): void {
    this.store.select(selectPreferences)
      .pipe(takeUntil(this.destroy$))
      .subscribe((preferences) => this.preferences.set(preferences));

    this.store.select(selectLoading)
      .pipe(takeUntil(this.destroy$))
      .subscribe((loading) => this.loading.set(loading));

    this.store.select(selectError)
      .pipe(takeUntil(this.destroy$))
      .subscribe((error) => this.error.set(error));

    this.store.dispatch(NotificationActions.loadPreferences());
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  /**
   * Get the French label for a notification category.
   */
  getCategoryLabel(category: NotificationCategory): string {
    return this.categoryLabels[category] ?? category;
  }

  /**
   * Handle email toggle change.
   */
  onEmailToggle(preference: NotificationPreference, event: MatSlideToggleChange): void {
    this.store.dispatch(
      NotificationActions.updatePreference({
        request: {
          category: preference.category,
          emailEnabled: event.checked,
          inAppEnabled: preference.inAppEnabled,
        },
      })
    );
  }

  /**
   * Handle in-app toggle change.
   */
  onInAppToggle(preference: NotificationPreference, event: MatSlideToggleChange): void {
    this.store.dispatch(
      NotificationActions.updatePreference({
        request: {
          category: preference.category,
          emailEnabled: preference.emailEnabled,
          inAppEnabled: event.checked,
        },
      })
    );
  }
}
