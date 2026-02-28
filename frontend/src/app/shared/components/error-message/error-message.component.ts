// frontend/src/app/shared/components/error-message/error-message.component.ts

import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';

@Component({
  selector: 'app-error-message',
  standalone: true,
  imports: [CommonModule, MatIconModule, MatButtonModule],
  template: `
    <div class="error-message"
         role="alert"
         aria-live="assertive">
      <mat-icon class="error-message__icon" aria-hidden="true">error_outline</mat-icon>
      <div class="error-message__content">
        <p class="error-message__title">{{ title }}</p>
        <p class="error-message__detail">{{ message }}</p>
      </div>
      @if (retryable) {
        <button mat-button color="primary" (click)="onRetry()" aria-label="Reessayer le chargement">
          Reessayer
        </button>
      }
    </div>
  `,
  styles: [`
    .error-message {
      display: flex;
      align-items: flex-start;
      gap: 12px;
      padding: 16px;
      border: 1px solid var(--color-error-border, #c62828);
      border-radius: 4px;
      background-color: var(--color-error-bg, #fce4ec);

      &__icon {
        color: var(--color-error-text, #b71c1c);
      }

      &__title {
        font-weight: 600;
        color: var(--color-error-text, #b71c1c);
        margin: 0;
      }

      &__detail {
        color: var(--color-text-primary, #212121);
        margin: 4px 0 0;
      }
    }
  `]
})
export class ErrorMessageComponent {
  @Input() title = 'Une erreur est survenue';
  @Input() message = 'Veuillez reessayer ulterieurement.';
  @Input() retryable = false;

  onRetry(): void {
    // Emit retry event -- parent handles the logic
    window.dispatchEvent(new CustomEvent('app:retry'));
  }
}
