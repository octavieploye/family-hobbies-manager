// frontend/src/app/shared/components/empty-state/empty-state.component.ts

import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';

@Component({
  selector: 'app-empty-state',
  standalone: true,
  imports: [CommonModule, MatIconModule, MatButtonModule],
  template: `
    <div class="empty-state" role="status" aria-live="polite">
      <mat-icon class="empty-state__icon" aria-hidden="true">{{ icon }}</mat-icon>
      <p class="empty-state__message">{{ message }}</p>
      @if (actionLabel) {
        <button mat-raised-button color="primary" (click)="action.emit()">
          {{ actionLabel }}
        </button>
      }
    </div>
  `,
  styles: [`
    .empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 48px 24px;
      gap: 16px;
      text-align: center;

      &__icon {
        font-size: 48px;
        width: 48px;
        height: 48px;
        color: var(--color-text-disabled, #757575);
      }

      &__message {
        color: var(--color-text-secondary, #424242);
        font-size: 1rem;
      }
    }
  `]
})
export class EmptyStateComponent {
  @Input() message = 'Aucun resultat.';
  @Input() icon = 'inbox';
  @Input() actionLabel?: string;
  @Output() action = new EventEmitter<void>();
}
