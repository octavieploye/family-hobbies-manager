// frontend/src/app/features/association/components/association-card/association-card.component.ts
import { Component, ChangeDetectionStrategy, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { Association } from '../../models/association.model';

/**
 * Standalone card component displaying an association summary.
 *
 * Displays: name, city, category (as chip), optional logo.
 * Click navigates to the association detail route.
 */
@Component({
  selector: 'app-association-card',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatCardModule,
    MatChipsModule,
  ],
  templateUrl: './association-card.component.html',
  styleUrl: './association-card.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AssociationCardComponent {
  @Input({ required: true }) association!: Association;
}
