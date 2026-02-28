// frontend/src/app/features/settings/components/consent-management/consent-management.component.ts
import { Component, ChangeDetectionStrategy, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatChipsModule } from '@angular/material/chips';

import { RgpdService } from '../../services/rgpd.service';
import {
  ConsentType,
  ConsentStatus,
  CONSENT_LABELS,
} from '@shared/models/rgpd.model';

/**
 * Consent management component with toggle switches per consent type.
 *
 * Features:
 * - MatSlideToggle per consent type
 * - French descriptions and labels from CONSENT_LABELS
 * - Required consents clearly marked
 * - Shows version and consent date
 */
@Component({
  selector: 'app-consent-management',
  standalone: true,
  imports: [
    CommonModule,
    MatSlideToggleModule,
    MatCardModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatChipsModule,
  ],
  templateUrl: './consent-management.component.html',
  styleUrl: './consent-management.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConsentManagementComponent implements OnInit {
  private readonly rgpdService = inject(RgpdService);
  private readonly snackBar = inject(MatSnackBar);

  /** Consent configuration labels. */
  consentLabels = CONSENT_LABELS;

  /** All consent types. */
  consentTypes: ConsentType[] = [
    'TERMS_OF_SERVICE',
    'DATA_PROCESSING',
    'MARKETING_EMAIL',
    'THIRD_PARTY_SHARING',
  ];

  /** Current consent statuses. */
  consents = signal<ConsentStatus[]>([]);

  /** Loading state. */
  loading = signal<boolean>(true);

  /** Error state. */
  error = signal<string | null>(null);

  ngOnInit(): void {
    this.loadConsents();
  }

  /**
   * Load current consent status from the API.
   */
  private loadConsents(): void {
    this.loading.set(true);
    this.rgpdService.getConsentStatus().subscribe({
      next: (consents) => {
        this.consents.set(consents);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(err?.error?.message || 'Erreur lors du chargement des consentements');
        this.loading.set(false);
      },
    });
  }

  /**
   * Check if a consent type is currently granted.
   */
  isGranted(consentType: ConsentType): boolean {
    const consent = this.consents().find((c) => c.consentType === consentType);
    return consent?.granted ?? false;
  }

  /**
   * Get the consent date for a type.
   */
  getConsentDate(consentType: ConsentType): string | null {
    const consent = this.consents().find((c) => c.consentType === consentType);
    return consent?.consentedAt ?? null;
  }

  /**
   * Get the consent version for a type.
   */
  getConsentVersion(consentType: ConsentType): string | null {
    const consent = this.consents().find((c) => c.consentType === consentType);
    return consent?.version ?? null;
  }

  /**
   * Toggle a consent.
   */
  onToggleConsent(consentType: ConsentType, granted: boolean): void {
    this.rgpdService.recordConsent({ consentType, granted }).subscribe({
      next: (updated) => {
        this.consents.update((consents) =>
          consents.map((c) => (c.consentType === consentType ? updated : c))
        );
        this.snackBar.open(
          granted ? 'Consentement accord\u00e9' : 'Consentement retir\u00e9',
          'Fermer',
          { duration: 3000 }
        );
      },
      error: (err) => {
        this.snackBar.open(
          err?.error?.message || 'Erreur lors de la mise \u00e0 jour du consentement',
          'Fermer',
          { duration: 5000 }
        );
      },
    });
  }
}
