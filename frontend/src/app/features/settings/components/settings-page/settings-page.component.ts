// frontend/src/app/features/settings/components/settings-page/settings-page.component.ts
import { Component, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTabsModule } from '@angular/material/tabs';
import { MatIconModule } from '@angular/material/icon';

import { ConsentManagementComponent } from '../consent-management/consent-management.component';
import { DataExportComponent } from '../data-export/data-export.component';
import { AccountDeletionComponent } from '../account-deletion/account-deletion.component';

/**
 * Settings page with Material tabs.
 *
 * Tab 1: "Consentements" - ConsentManagementComponent
 * Tab 2: "Mes donn\u00e9es" - DataExportComponent
 * Tab 3: "Supprimer mon compte" - AccountDeletionComponent
 *
 * French labels throughout.
 */
@Component({
  selector: 'app-settings-page',
  standalone: true,
  imports: [
    CommonModule,
    MatTabsModule,
    MatIconModule,
    ConsentManagementComponent,
    DataExportComponent,
    AccountDeletionComponent,
  ],
  templateUrl: './settings-page.component.html',
  styleUrl: './settings-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SettingsPageComponent {}
