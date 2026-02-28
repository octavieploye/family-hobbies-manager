// frontend/src/app/features/settings/components/account-deletion/account-deletion.component.spec.ts
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { RouterTestingModule } from '@angular/router/testing';
import { AccountDeletionComponent } from './account-deletion.component';
import { RgpdService } from '../../services/rgpd.service';
import { of } from 'rxjs';

describe('AccountDeletionComponent', () => {
  let component: AccountDeletionComponent;
  let fixture: ComponentFixture<AccountDeletionComponent>;

  beforeEach(async () => {
    const rgpdServiceMock = {
      getConsentStatus: jest.fn().mockReturnValue(of([])),
      recordConsent: jest.fn().mockReturnValue(of({})),
      getConsentHistory: jest.fn().mockReturnValue(of([])),
      exportData: jest.fn().mockReturnValue(of({})),
      deleteAccount: jest.fn().mockReturnValue(of(undefined)),
    };

    await TestBed.configureTestingModule({
      imports: [AccountDeletionComponent, NoopAnimationsModule, RouterTestingModule],
      providers: [
        { provide: RgpdService, useValue: rgpdServiceMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AccountDeletionComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should display warning text', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('irr\u00e9versible');
  });

  it('should show confirmation form when deletion requested', () => {
    component.onRequestDeletion();
    fixture.detectChanges();
    expect(component.showConfirmation()).toBe(true);
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Mot de passe');
  });
});
