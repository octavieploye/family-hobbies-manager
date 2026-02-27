// frontend/src/app/features/family/components/family-member-list/family-member-list.component.ts
import {
  Component,
  ChangeDetectionStrategy,
  Input,
  Output,
  EventEmitter,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { FamilyMember } from '../../models/family.model';

/**
 * Family member list component.
 *
 * Displays a Material table of family members with:
 * - Columns: name, age, relationship, actions
 * - Edit button (pencil icon)
 * - Delete button (trash icon) with window.confirm dialog
 *
 * Parent component provides the members array and listens for edit/delete events.
 */
@Component({
  selector: 'app-family-member-list',
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
  ],
  templateUrl: './family-member-list.component.html',
  styleUrl: './family-member-list.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FamilyMemberListComponent {
  @Input() members: FamilyMember[] = [];
  @Output() edit = new EventEmitter<FamilyMember>();
  @Output() delete = new EventEmitter<number>();

  readonly displayedColumns = ['name', 'age', 'relationship', 'actions'];

  /** Map relationship codes to French labels for display. */
  readonly relationshipLabels: Record<string, string> = {
    PARENT: 'Parent',
    CHILD: 'Enfant',
    SPOUSE: 'Conjoint(e)',
    SIBLING: 'Frere/Soeur',
    OTHER: 'Autre',
  };

  onEdit(member: FamilyMember): void {
    this.edit.emit(member);
  }

  onDelete(member: FamilyMember): void {
    const confirmed = window.confirm(
      `Voulez-vous vraiment supprimer ${member.firstName} ${member.lastName} de la famille ?`
    );
    if (confirmed) {
      this.delete.emit(member.id);
    }
  }

  getRelationshipLabel(relationship: string): string {
    return this.relationshipLabels[relationship] ?? relationship;
  }
}
