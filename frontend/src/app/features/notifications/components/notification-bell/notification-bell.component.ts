// frontend/src/app/features/notifications/components/notification-bell/notification-bell.component.ts
import {
  Component,
  ChangeDetectionStrategy,
  OnInit,
  OnDestroy,
  inject,
  signal,
} from '@angular/core';
import { Store } from '@ngrx/store';
import { MatIconModule } from '@angular/material/icon';
import { MatBadgeModule } from '@angular/material/badge';
import { MatButtonModule } from '@angular/material/button';
import { Subject, interval } from 'rxjs';
import { switchMap, takeUntil } from 'rxjs/operators';

import { NotificationActions } from '../../store/notification.actions';
import {
  selectUnreadCount,
  selectDropdownOpen,
} from '../../store/notification.selectors';

/**
 * Bell icon button that displays the unread notification count as a badge.
 *
 * Clicking toggles the notification dropdown open/close.
 * Polls unread count every 30 seconds.
 */
@Component({
  selector: 'app-notification-bell',
  standalone: true,
  imports: [MatIconModule, MatBadgeModule, MatButtonModule],
  templateUrl: './notification-bell.component.html',
  styleUrl: './notification-bell.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotificationBellComponent implements OnInit, OnDestroy {
  private readonly store = inject(Store);
  private readonly destroy$ = new Subject<void>();

  /** Signal of the unread count from the store. */
  unreadCount = signal<number>(0);

  /** Signal of whether the dropdown is open. */
  dropdownOpen = signal<boolean>(false);

  /** Polling interval in milliseconds. */
  private readonly POLL_INTERVAL = 30_000;

  ngOnInit(): void {
    // Subscribe to store selectors and update signals
    this.store.select(selectUnreadCount)
      .pipe(takeUntil(this.destroy$))
      .subscribe((count) => this.unreadCount.set(count));

    this.store.select(selectDropdownOpen)
      .pipe(takeUntil(this.destroy$))
      .subscribe((open) => this.dropdownOpen.set(open));

    // Initial load
    this.store.dispatch(NotificationActions.loadUnreadCount());

    // Poll every 30 seconds
    interval(this.POLL_INTERVAL)
      .pipe(
        switchMap(() => {
          this.store.dispatch(NotificationActions.loadUnreadCount());
          return [];
        }),
        takeUntil(this.destroy$)
      )
      .subscribe();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  /**
   * Toggle the notification dropdown.
   */
  toggleDropdown(): void {
    this.store.dispatch(NotificationActions.toggleDropdown());
  }
}
