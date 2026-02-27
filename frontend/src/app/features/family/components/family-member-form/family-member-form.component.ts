// frontend/src/app/features/family/components/family-member-form/family-member-form.component.ts
import {
  Component,
  ChangeDetectionStrategy,
  Input,
  Output,
  EventEmitter,
  OnInit,
  OnChanges,
  SimpleChanges,
  inject,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  ReactiveFormsModule,
  FormBuilder,
  Validators,
  AbstractControl,
  ValidationErrors,
} from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { FamilyMember, FamilyMemberRequest } from '../../models/family.model';

/**
 * Relationship options for a family member.
 * Matches the backend Relationship enum.
 */
export const RELATIONSHIP_OPTIONS = [
  { value: 'PARENT', label: 'Parent' },
  { value: 'CHILD', label: 'Enfant' },
  { value: 'SPOUSE', label: 'Conjoint(e)' },
  { value: 'SIBLING', label: 'Frere/Soeur' },
  { value: 'OTHER', label: 'Autre' },
] as const;

/**
 * Custom validator: ensures the date of birth is in the past.
 */
function pastDateValidator(control: AbstractControl): ValidationErrors | null {
  if (!control.value) {
    return null;
  }
  const date = new Date(control.value);
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return date < today ? null : { futureDate: true };
}

/**
 * Family member form component.
 *
 * Standalone reactive form for adding or editing a family member.
 * When a member is provided via @Input(), the form is pre-populated for editing.
 *
 * Emits:
 * - save: FamilyMemberRequest when the form is valid and submitted
 * - cancel: void when the user cancels the form
 */
@Component({
  selector: 'app-family-member-form',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatDatepickerModule,
    MatNativeDateModule,
  ],
  templateUrl: './family-member-form.component.html',
  styleUrl: './family-member-form.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FamilyMemberFormComponent implements OnInit, OnChanges {
  private readonly fb = inject(FormBuilder);

  @Input() member: FamilyMember | null = null;
  @Output() save = new EventEmitter<FamilyMemberRequest>();
  @Output() cancel = new EventEmitter<void>();

  readonly relationshipOptions = RELATIONSHIP_OPTIONS;

  memberForm = this.fb.group({
    firstName: ['', [Validators.required, Validators.minLength(2)]],
    lastName: ['', [Validators.required, Validators.minLength(2)]],
    dateOfBirth: ['', [Validators.required, pastDateValidator]],
    relationship: ['', [Validators.required]],
    medicalNote: [''],
  });

  ngOnInit(): void {
    this.populateForm();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['member']) {
      this.populateForm();
    }
  }

  onSubmit(): void {
    if (this.memberForm.invalid) {
      this.memberForm.markAllAsTouched();
      return;
    }

    const formValue = this.memberForm.getRawValue();
    const request: FamilyMemberRequest = {
      firstName: formValue.firstName!,
      lastName: formValue.lastName!,
      dateOfBirth: formValue.dateOfBirth!,
      relationship: formValue.relationship!,
      medicalNote: formValue.medicalNote || undefined,
    };

    this.save.emit(request);
  }

  onCancel(): void {
    this.cancel.emit();
  }

  private populateForm(): void {
    if (this.member) {
      this.memberForm.patchValue({
        firstName: this.member.firstName,
        lastName: this.member.lastName,
        dateOfBirth: this.member.dateOfBirth,
        relationship: this.member.relationship,
        medicalNote: this.member.medicalNote ?? '',
      });
    } else {
      this.memberForm.reset();
    }
  }
}
