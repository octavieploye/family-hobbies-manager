// frontend/src/app/features/family/components/family-dashboard/family-dashboard.component.ts
import { Component, ChangeDetectionStrategy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Store } from '@ngrx/store';
import { FamilyActions } from '../../store/family.actions';
import {
  selectFamily,
  selectFamilyMembers,
  selectFamilyLoading,
  selectFamilyError,
} from '../../store/family.selectors';
import { FamilyMemberListComponent } from '../family-member-list/family-member-list.component';
import { FamilyMemberFormComponent } from '../family-member-form/family-member-form.component';
import { Family, FamilyMember, FamilyMemberRequest } from '../../models/family.model';

/**
 * Family dashboard component.
 *
 * Displays the user's family with member count, lists members,
 * and provides actions to create a family, add/edit/remove members.
 *
 * Uses NgRx store for all data operations — never calls FamilyService directly.
 */
@Component({
  selector: 'app-family-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    FamilyMemberListComponent,
    FamilyMemberFormComponent,
  ],
  templateUrl: './family-dashboard.component.html',
  styleUrl: './family-dashboard.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FamilyDashboardComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly fb = inject(FormBuilder);

  family$ = this.store.select(selectFamily);
  members$ = this.store.select(selectFamilyMembers);
  loading$ = this.store.select(selectFamilyLoading);
  error$ = this.store.select(selectFamilyError);

  /** Form for creating a new family */
  familyNameForm = this.fb.group({
    name: ['', [Validators.required, Validators.minLength(2)]],
  });

  /** Controls visibility of the member form */
  showMemberForm = false;

  /** Member being edited (null = adding new member) */
  editingMember: FamilyMember | null = null;

  ngOnInit(): void {
    this.store.dispatch(FamilyActions.loadFamily());
  }

  onCreateFamily(): void {
    if (this.familyNameForm.invalid) {
      return;
    }
    const name = this.familyNameForm.value.name as string;
    this.store.dispatch(FamilyActions.createFamily({ request: { name } }));
  }

  onAddMember(): void {
    this.editingMember = null;
    this.showMemberForm = true;
  }

  onEditMember(member: FamilyMember): void {
    this.editingMember = member;
    this.showMemberForm = true;
  }

  onDeleteMember(memberId: number): void {
    this.store.dispatch(FamilyActions.removeMember({ memberId }));
  }

  onSaveMember(request: FamilyMemberRequest, family: Family): void {
    if (this.editingMember) {
      this.store.dispatch(
        FamilyActions.updateMember({ memberId: this.editingMember.id, request })
      );
    } else {
      this.store.dispatch(
        FamilyActions.addMember({ familyId: family.id, request })
      );
    }
    this.showMemberForm = false;
    this.editingMember = null;
  }

  onCancelMemberForm(): void {
    this.showMemberForm = false;
    this.editingMember = null;
  }

  onClearError(): void {
    this.store.dispatch(FamilyActions.clearError());
  }
}
