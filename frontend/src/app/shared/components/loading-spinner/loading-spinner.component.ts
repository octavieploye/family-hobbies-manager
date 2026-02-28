// frontend/src/app/shared/components/loading-spinner/loading-spinner.component.ts

import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

@Component({
  selector: 'app-loading-spinner',
  standalone: true,
  imports: [CommonModule, MatProgressSpinnerModule],
  template: `
    <div class="loading-spinner"
         role="status"
         aria-live="polite"
         [attr.aria-label]="message">
      <mat-spinner [diameter]="diameter" aria-hidden="true"></mat-spinner>
      <p class="loading-spinner__message">{{ message }}</p>
    </div>
  `,
  styles: [`
    .loading-spinner {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 24px;
      gap: 16px;

      &__message {
        color: var(--color-text-secondary, #424242);
        font-size: 0.875rem;
      }
    }
  `]
})
export class LoadingSpinnerComponent {
  @Input() message = 'Chargement en cours...';
  @Input() diameter = 40;
}
