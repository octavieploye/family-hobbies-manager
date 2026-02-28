// frontend/src/app/features/settings/components/account-deletion/account-deletion.component.ts
import { Component, ChangeDetectionStrategy, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';

import { RgpdService } from '../../services/rgpd.service';

/**
 * Account deletion component with confirmation and password re-entry.
 *
 * Features:
 * - Warning text about irreversibility
 * - Password re-entry for security
 * - Optional reason field
 * - Confirmation step before deletion
 * - French labels throughout
 */
@Component({
  selector: 'app-account-deletion',
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
    MatDialogModule,
    MatSnackBarModule,
  ],
  templateUrl: './account-deletion.component.html',
  styleUrl: './account-deletion.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccountDeletionComponent {
  private readonly rgpdService = inject(RgpdService);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);
  private readonly fb = inject(FormBuilder);

  /** Form for password re-entry and optional reason. */
  deletionForm = this.fb.group({
    password: ['', [Validators.required, Validators.minLength(6)]],
    reason: [''],
  });

  /** Whether the confirmation step is shown. */
  showConfirmation = signal<boolean>(false);

  /** Loading state. */
  loading = signal<boolean>(false);

  /** Error state. */
  error = signal<string | null>(null);

  /**
   * Show the confirmation step.
   */
  onRequestDeletion(): void {
    this.showConfirmation.set(true);
  }

  /**
   * Cancel the deletion request.
   */
  onCancelDeletion(): void {
    this.showConfirmation.set(false);
    this.deletionForm.reset();
    this.error.set(null);
  }

  /**
   * Confirm and execute account deletion.
   */
  onConfirmDeletion(): void {
    if (this.deletionForm.invalid) {
      return;
    }

    const { password, reason } = this.deletionForm.value;
    this.loading.set(true);
    this.error.set(null);

    this.rgpdService.deleteAccount(password!, reason || undefined).subscribe({
      next: () => {
        this.loading.set(false);
        this.snackBar.open('Votre compte a \u00e9t\u00e9 supprim\u00e9 avec succ\u00e8s.', 'Fermer', {
          duration: 5000,
        });
        this.router.navigate(['/auth/login']);
      },
      error: (err) => {
        this.error.set(
          err?.error?.message || 'Erreur lors de la suppression du compte'
        );
        this.loading.set(false);
      },
    });
  }
}
