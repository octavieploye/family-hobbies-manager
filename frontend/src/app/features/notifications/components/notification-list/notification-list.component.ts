// frontend/src/app/features/notifications/components/notification-list/notification-list.component.ts
import {
  Component,
  ChangeDetectionStrategy,
  OnInit,
  OnDestroy,
  inject,
  signal,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { Router } from '@angular/router';
import { Store } from '@ngrx/store';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { NotificationActions } from '../../store/notification.actions';
import {
  selectNotifications,
  selectLoading,
  selectError,
  selectPagination,
} from '../../store/notification.selectors';
import {
  Notification,
  NotificationCategory,
  CATEGORY_LABELS,
} from '@shared/models/notification.model';

/**
 * Full-page notification list with pagination.
 *
 * Features:
 * - Material table with category badge, title, message, time, read/unread dot
 * - Paginator for navigation
 * - Click to mark as read and navigate to related entity
 * - Loading spinner and error/empty states
 * - French labels
 */
@Component({
  selector: 'app-notification-list',
  standalone: true,
  imports: [
    DatePipe,
    MatTableModule,
    MatPaginatorModule,
    MatCardModule,
    MatProgressSpinnerModule,
    MatChipsModule,
    MatIconModule,
    MatButtonModule,
  ],
  templateUrl: './notification-list.component.html',
  styleUrl: './notification-list.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotificationListComponent implements OnInit, OnDestroy {
  private readonly store = inject(Store);
  private readonly router = inject(Router);
  private readonly destroy$ = new Subject<void>();

  /** Signal of notifications from the store. */
  notifications = signal<Notification[]>([]);

  /** Signal of the loading flag. */
  loading = signal<boolean>(false);

  /** Signal of the current error message. */
  error = signal<string | null>(null);

  /** Signal of the pagination state. */
  pagination = signal<{ totalElements: number; currentPage: number; pageSize: number }>({
    totalElements: 0,
    currentPage: 0,
    pageSize: 10,
  });

  /** Columns displayed in the table. */
  displayedColumns: string[] = ['status', 'category', 'title', 'message', 'createdAt'];

  /** Category label map. */
  categoryLabels = CATEGORY_LABELS;

  ngOnInit(): void {
    // Subscribe to store selectors and update signals
    this.store.select(selectNotifications)
      .pipe(takeUntil(this.destroy$))
      .subscribe((notifications) => this.notifications.set(notifications));

    this.store.select(selectLoading)
      .pipe(takeUntil(this.destroy$))
      .subscribe((loading) => this.loading.set(loading));

    this.store.select(selectError)
      .pipe(takeUntil(this.destroy$))
      .subscribe((error) => this.error.set(error));

    this.store.select(selectPagination)
      .pipe(takeUntil(this.destroy$))
      .subscribe((pagination) => this.pagination.set(pagination));

    this.loadNotifications();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  /**
   * Handle page change from MatPaginator.
   */
  onPageChange(event: PageEvent): void {
    this.loadNotifications(event.pageIndex, event.pageSize);
  }

  /**
   * Handle click on a notification row.
   * Marks the notification as read and navigates to the related entity if applicable.
   */
  onNotificationClick(notification: Notification): void {
    if (!notification.read) {
      this.store.dispatch(NotificationActions.markAsRead({ id: notification.id }));
    }
    if (notification.referenceId && notification.referenceType) {
      this.navigateToReference(notification.referenceType, notification.referenceId);
    }
  }

  /**
   * Get the French label for a notification category.
   */
  getCategoryLabel(category: NotificationCategory): string {
    return this.categoryLabels[category] ?? category;
  }

  /**
   * Dispatch loadNotifications action with pagination.
   */
  private loadNotifications(page: number = 0, size: number = 10): void {
    this.store.dispatch(
      NotificationActions.loadNotifications({ page, size })
    );
  }

  /**
   * Navigate to a related entity based on reference type.
   */
  private navigateToReference(referenceType: string, referenceId: string): void {
    switch (referenceType) {
      case 'PAYMENT':
        this.router.navigate(['/payments', referenceId]);
        break;
      case 'SUBSCRIPTION':
        this.router.navigate(['/family', 'subscriptions', referenceId]);
        break;
      default:
        break;
    }
  }
}
