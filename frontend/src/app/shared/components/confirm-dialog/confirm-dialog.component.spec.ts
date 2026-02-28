// frontend/src/app/shared/components/confirm-dialog/confirm-dialog.component.spec.ts

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import {
  ConfirmDialogComponent,
  ConfirmDialogData,
} from './confirm-dialog.component';

describe('ConfirmDialogComponent', () => {
  let component: ConfirmDialogComponent;
  let fixture: ComponentFixture<ConfirmDialogComponent>;
  let dialogRefSpy: jest.Mocked<MatDialogRef<ConfirmDialogComponent>>;

  const mockDialogData: ConfirmDialogData = {
    title: 'Confirmer la suppression',
    message: 'Voulez-vous vraiment supprimer ce membre ?',
    confirmLabel: 'Supprimer',
    cancelLabel: 'Annuler',
    confirmColor: 'warn',
  };

  beforeEach(async () => {
    dialogRefSpy = {
      close: jest.fn(),
      addPanelClass: jest.fn(),
    } as unknown as jest.Mocked<MatDialogRef<ConfirmDialogComponent>>;

    await TestBed.configureTestingModule({
      imports: [ConfirmDialogComponent, NoopAnimationsModule],
      providers: [
        { provide: MatDialogRef, useValue: dialogRefSpy },
        { provide: MAT_DIALOG_DATA, useValue: mockDialogData },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ConfirmDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should display the dialog title', () => {
    const titleEl = fixture.nativeElement.querySelector('[mat-dialog-title]');
    expect(titleEl).toBeTruthy();
    expect(titleEl.textContent?.trim()).toContain('Confirmer la suppression');
  });

  it('should display the dialog message', () => {
    const contentEl = fixture.nativeElement.querySelector('mat-dialog-content p');
    expect(contentEl).toBeTruthy();
    expect(contentEl.textContent?.trim()).toContain(
      'Voulez-vous vraiment supprimer ce membre ?'
    );
  });

  it('should close with false when cancel is clicked', () => {
    component.onCancel();
    expect(dialogRefSpy.close).toHaveBeenCalledWith(false);
  });

  it('should close with true when confirm is clicked', () => {
    component.onConfirm();
    expect(dialogRefSpy.close).toHaveBeenCalledWith(true);
  });

  it('should have the confirm-dialog-title ID for aria-labelledby', () => {
    const titleEl = fixture.nativeElement.querySelector('#confirm-dialog-title');
    expect(titleEl).toBeTruthy();
  });
});
