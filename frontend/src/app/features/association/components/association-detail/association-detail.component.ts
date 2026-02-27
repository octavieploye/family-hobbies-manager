// frontend/src/app/features/association/components/association-detail/association-detail.component.ts
import { Component, ChangeDetectionStrategy, OnInit, inject, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatDividerModule } from '@angular/material/divider';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { AssociationActions } from '../../store/association.actions';
import {
  selectSelectedAssociation,
  selectAssociationLoading,
  selectAssociationError,
} from '../../store/association.selectors';

/**
 * Standalone detail component showing full information about an association.
 *
 * Loads the association detail by ID from route params.
 * Dispatches loadAssociationDetail action on init.
 * Displays all fields: name, description, address, contact info, website, category, status.
 */
@Component({
  selector: 'app-association-detail',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatDividerModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './association-detail.component.html',
  styleUrl: './association-detail.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AssociationDetailComponent implements OnInit, OnDestroy {
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);

  /** Observable of the selected association detail. */
  association$ = this.store.select(selectSelectedAssociation);

  /** Observable of loading state. */
  loading$ = this.store.select(selectAssociationLoading);

  /** Observable of error state. */
  error$ = this.store.select(selectAssociationError);

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      const id = Number(idParam);
      if (!isNaN(id)) {
        this.store.dispatch(AssociationActions.loadAssociationDetail({ id }));
      }
    }
  }

  ngOnDestroy(): void {
    this.store.dispatch(AssociationActions.clearError());
  }
}
