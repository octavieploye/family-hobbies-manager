// frontend/src/app/features/settings/components/data-export/data-export.component.ts
import { Component, ChangeDetectionStrategy, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';

import { RgpdService } from '../../services/rgpd.service';
import { UserDataExport } from '@shared/models/rgpd.model';

/**
 * Data export component for RGPD portability.
 *
 * Features:
 * - Export button to fetch user data
 * - JSON preview in MatExpansionPanel
 * - Download as file option
 * - French labels throughout
 */
@Component({
  selector: 'app-data-export',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatExpansionModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
  ],
  templateUrl: './data-export.component.html',
  styleUrl: './data-export.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DataExportComponent {
  private readonly rgpdService = inject(RgpdService);
  private readonly snackBar = inject(MatSnackBar);

  /** Exported data. */
  exportData = signal<UserDataExport | null>(null);

  /** Loading state. */
  loading = signal<boolean>(false);

  /** Error state. */
  error = signal<string | null>(null);

  /**
   * Fetch user data export from the API.
   */
  onExport(): void {
    this.loading.set(true);
    this.error.set(null);

    this.rgpdService.exportData().subscribe({
      next: (data) => {
        this.exportData.set(data);
        this.loading.set(false);
        this.snackBar.open('Donn\u00e9es export\u00e9es avec succ\u00e8s', 'Fermer', { duration: 3000 });
      },
      error: (err) => {
        this.error.set(err?.error?.message || 'Erreur lors de l\'exportation des donn\u00e9es');
        this.loading.set(false);
      },
    });
  }

  /**
   * Get formatted JSON string of the export data.
   */
  getFormattedJson(): string {
    const data = this.exportData();
    return data ? JSON.stringify(data, null, 2) : '';
  }

  /**
   * Download the export data as a JSON file.
   */
  onDownload(): void {
    const data = this.exportData();
    if (!data) {
      return;
    }

    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `mes-donnees-${new Date().toISOString().split('T')[0]}.json`;
    link.click();
    window.URL.revokeObjectURL(url);
  }
}
