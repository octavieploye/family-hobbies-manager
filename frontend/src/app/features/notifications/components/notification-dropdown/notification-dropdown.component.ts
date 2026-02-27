// frontend/src/app/features/notifications/components/notification-dropdown/notification-dropdown.component.ts
import {
  Component,
  ChangeDetectionStrategy,
  OnInit,
  OnDestroy,
  inject,
  signal,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterModule } from '@angular/router';
import { Store } from '@ngrx/store';
import { MatListModule } from '@angular/material/list';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDividerModule } from '@angular/material/divider';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { NotificationActions } from '../../store/notification.actions';
import {
  selectRecentNotifications,
  selectDropdownOpen,
} from '../../store/notification.selectors';
import { Notification } from '@shared/models/notification.model';

/**
 * Dropdown panel showing the 5 most recent notifications.
 *
 * Features:
 * - Title, message preview (truncated), and relative time
 * - Read/unread styling
 * - "Marquer tout comme lu" button
 * - "Voir toutes les notifications" link
 */
@Component({
  selector: 'app-notification-dropdown',
  standalone: true,
  imports: [
    DatePipe,
    RouterModule,
    MatListModule,
    MatButtonModule,
    MatIconModule,
    MatDividerModule,
  ],
  templateUrl: './notification-dropdown.component.html',
  styleUrl: './notification-dropdown.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotificationDropdownComponent implements OnInit, OnDestroy {
  private readonly store = inject(Store);
  private readonly destroy$ = new Subject<void>();

  /** Signal of the last 5 notifications. */
  recentNotifications = signal<Notification[]>([]);

  /** Signal of whether the dropdown is open. */
  dropdownOpen = signal<boolean>(false);

  ngOnInit(): void {
    // Subscribe to store selectors and update signals
    this.store.select(selectRecentNotifications)
      .pipe(takeUntil(this.destroy$))
      .subscribe((notifications) => this.recentNotifications.set(notifications));

    this.store.select(selectDropdownOpen)
      .pipe(takeUntil(this.destroy$))
      .subscribe((open) => this.dropdownOpen.set(open));

    this.store.dispatch(NotificationActions.loadNotifications({ page: 0, size: 5 }));
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  /**
   * Mark all notifications as read.
   */
  markAllAsRead(): void {
    this.store.dispatch(NotificationActions.markAllAsRead());
  }

  /**
   * Mark a single notification as read.
   */
  markAsRead(id: number): void {
    this.store.dispatch(NotificationActions.markAsRead({ id }));
  }

  /**
   * Truncate a message for preview display.
   */
  truncateMessage(message: string, maxLength: number = 80): string {
    if (message.length <= maxLength) {
      return message;
    }
    return message.substring(0, maxLength) + '...';
  }
}
