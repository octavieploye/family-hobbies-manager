// frontend/src/app/features/activities/components/activity-list/activity-list.component.ts
import { Component, ChangeDetectionStrategy, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { PageEvent, MatPaginatorModule } from '@angular/material/paginator';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { ActivityCardComponent } from '../activity-card/activity-card.component';
import { ActivityActions } from '../../store/activity.actions';
import {
  selectActivities,
  selectActivityLoading,
  selectActivityError,
  selectActivityPagination,
} from '../../store/activity.selectors';
import { ActivitySearchRequest } from '@shared/models/activity.model';

/**
 * Standalone list component for browsing activities within an association.
 *
 * Features:
 * - Receives associationId from route params
 * - Filter form for category and level
 * - Results displayed as a grid of ActivityCard components
 * - Material Paginator for page navigation
 * - Loading spinner while fetching
 * - Error display on failure
 *
 * All data flows through the NgRx store.
 */
@Component({
  selector: 'app-activity-list',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    MatFormFieldModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    ActivityCardComponent,
  ],
  templateUrl: './activity-list.component.html',
  styleUrl: './activity-list.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ActivityListComponent implements OnInit, OnDestroy {
  private readonly store = inject(Store);
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);

  /** The association ID from route params. */
  associationId = 0;

  /** Observable of the activities list from the store. */
  activities$ = this.store.select(selectActivities);

  /** Observable of the loading flag. */
  loading$ = this.store.select(selectActivityLoading);

  /** Observable of the current error message. */
  error$ = this.store.select(selectActivityError);

  /** Observable of the pagination state. */
  pagination$ = this.store.select(selectActivityPagination);

  /** Categories available for filtering. */
  categories: string[] = [
    'Sport',
    'Danse',
    'Musique',
    'Theatre',
    'Arts',
    'Culture',
    'Loisirs',
    'Education',
  ];

  /** Levels available for filtering. */
  levels: string[] = [
    'ALL_LEVELS',
    'BEGINNER',
    'INTERMEDIATE',
    'ADVANCED',
  ];

  /** French labels for activity levels. */
  levelLabels: Record<string, string> = {
    ALL_LEVELS: 'Tous niveaux',
    BEGINNER: 'D\u00e9butant',
    INTERMEDIATE: 'Interm\u00e9diaire',
    ADVANCED: 'Avanc\u00e9',
  };

  /** Reactive form for filters. */
  filterForm = this.fb.group({
    category: [''],
    level: [''],
  });

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('associationId');
    if (idParam) {
      this.associationId = Number(idParam);
    }
    this.loadActivities();
  }

  ngOnDestroy(): void {
    this.store.dispatch(ActivityActions.clearActivities());
  }

  /**
   * Dispatch load activities action with current filters.
   * Resets to page 0 on new filter.
   */
  loadActivities(): void {
    const formValue = this.filterForm.value;
    const request: ActivitySearchRequest = {
      associationId: this.associationId,
      category: formValue.category || undefined,
      level: formValue.level || undefined,
      page: 0,
      size: 20,
    };
    this.store.dispatch(ActivityActions.loadActivities({ request }));
  }

  /**
   * Handle page change from MatPaginator.
   */
  onPageChange(event: PageEvent): void {
    const formValue = this.filterForm.value;
    const request: ActivitySearchRequest = {
      associationId: this.associationId,
      category: formValue.category || undefined,
      level: formValue.level || undefined,
      page: event.pageIndex,
      size: event.pageSize,
    };
    this.store.dispatch(ActivityActions.loadActivities({ request }));
  }

  /**
   * Reset filter form and reload activities.
   */
  onClearFilters(): void {
    this.filterForm.reset();
    this.loadActivities();
  }
}
