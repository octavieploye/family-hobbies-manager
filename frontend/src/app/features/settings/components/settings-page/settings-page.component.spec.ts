// frontend/src/app/features/settings/components/settings-page/settings-page.component.spec.ts
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { SettingsPageComponent } from './settings-page.component';
import { RgpdService } from '../../services/rgpd.service';
import { of } from 'rxjs';

describe('SettingsPageComponent', () => {
  let component: SettingsPageComponent;
  let fixture: ComponentFixture<SettingsPageComponent>;

  beforeEach(async () => {
    const rgpdServiceMock = {
      getConsentStatus: jest.fn().mockReturnValue(of([])),
      recordConsent: jest.fn().mockReturnValue(of({})),
      getConsentHistory: jest.fn().mockReturnValue(of([])),
      exportData: jest.fn().mockReturnValue(of({})),
      deleteAccount: jest.fn().mockReturnValue(of(undefined)),
    };

    await TestBed.configureTestingModule({
      imports: [SettingsPageComponent, NoopAnimationsModule],
      providers: [
        { provide: RgpdService, useValue: rgpdServiceMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SettingsPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should display page title', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Param\u00e8tres');
  });

  it('should have three tabs', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Consentements');
    expect(compiled.textContent).toContain('Mes donn\u00e9es');
    expect(compiled.textContent).toContain('Supprimer mon compte');
  });
});
