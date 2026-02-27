// frontend/src/app/features/association/components/association-list/association-list.component.ts
import { Component, ChangeDetectionStrategy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder } from '@angular/forms';
import { Store } from '@ngrx/store';
import { PageEvent, MatPaginatorModule } from '@angular/material/paginator';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { AssociationCardComponent } from '../association-card/association-card.component';
import { AssociationActions } from '../../store/association.actions';
import {
  selectAssociations,
  selectAssociationLoading,
  selectAssociationError,
  selectPagination,
} from '../../store/association.selectors';
import { AssociationSearchRequest } from '../../models/association.model';

/**
 * Standalone list component for searching and browsing associations.
 *
 * Features:
 * - Search form with city, category, and keyword inputs
 * - Results displayed as a grid of AssociationCard components
 * - Material Paginator for page navigation
 * - Loading spinner while fetching
 * - Error display on failure
 *
 * All data flows through the NgRx store. This component never calls
 * the AssociationService directly.
 */
@Component({
  selector: 'app-association-list',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    AssociationCardComponent,
  ],
  templateUrl: './association-list.component.html',
  styleUrl: './association-list.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AssociationListComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly fb = inject(FormBuilder);

  /** Observable of the association list from the store. */
  associations$ = this.store.select(selectAssociations);

  /** Observable of the loading flag. */
  loading$ = this.store.select(selectAssociationLoading);

  /** Observable of the current error message. */
  error$ = this.store.select(selectAssociationError);

  /** Observable of the pagination state for MatPaginator. */
  pagination$ = this.store.select(selectPagination);

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
    'Social',
    'Environnement',
  ];

  /** Reactive form for search filters. */
  searchForm = this.fb.group({
    city: [''],
    category: [''],
    keyword: [''],
  });

  ngOnInit(): void {
    this.onSearch();
  }

  /**
   * Dispatch a search action with the current form values.
   * Resets to page 0 on new search.
   */
  onSearch(): void {
    const formValue = this.searchForm.value;
    const request: AssociationSearchRequest = {
      city: formValue.city || undefined,
      category: formValue.category || undefined,
      keyword: formValue.keyword || undefined,
      page: 0,
      size: 20,
    };
    this.store.dispatch(AssociationActions.searchAssociations({ request }));
  }

  /**
   * Handle page change from MatPaginator.
   * Dispatches a new search with updated page/size.
   */
  onPageChange(event: PageEvent): void {
    const formValue = this.searchForm.value;
    const request: AssociationSearchRequest = {
      city: formValue.city || undefined,
      category: formValue.category || undefined,
      keyword: formValue.keyword || undefined,
      page: event.pageIndex,
      size: event.pageSize,
    };
    this.store.dispatch(AssociationActions.searchAssociations({ request }));
  }

  /**
   * Reset search form and reload all associations.
   */
  onClear(): void {
    this.searchForm.reset();
    this.store.dispatch(AssociationActions.clearSearch());
    this.onSearch();
  }
}
