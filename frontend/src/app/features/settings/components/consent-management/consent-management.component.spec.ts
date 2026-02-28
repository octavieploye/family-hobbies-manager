// frontend/src/app/features/settings/components/consent-management/consent-management.component.spec.ts
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ConsentManagementComponent } from './consent-management.component';
import { RgpdService } from '../../services/rgpd.service';
import { of } from 'rxjs';
import { ConsentStatus } from '@shared/models/rgpd.model';

describe('ConsentManagementComponent', () => {
  let component: ConsentManagementComponent;
  let fixture: ComponentFixture<ConsentManagementComponent>;
  let rgpdServiceMock: jest.Mocked<RgpdService>;

  const mockConsents: ConsentStatus[] = [
    {
      id: 1,
      userId: 1,
      consentType: 'TERMS_OF_SERVICE',
      granted: true,
      version: '1.0',
      consentedAt: '2024-09-15T10:00:00',
    },
    {
      id: 2,
      userId: 1,
      consentType: 'DATA_PROCESSING',
      granted: true,
      version: '1.0',
      consentedAt: '2024-09-15T10:00:00',
    },
    {
      id: 3,
      userId: 1,
      consentType: 'MARKETING_EMAIL',
      granted: false,
      version: '1.0',
      consentedAt: '2024-09-15T10:00:00',
    },
    {
      id: 4,
      userId: 1,
      consentType: 'THIRD_PARTY_SHARING',
      granted: false,
      version: '1.0',
      consentedAt: '2024-09-15T10:00:00',
    },
  ];

  beforeEach(async () => {
    rgpdServiceMock = {
      getConsentStatus: jest.fn().mockReturnValue(of(mockConsents)),
      recordConsent: jest.fn().mockReturnValue(of(mockConsents[0])),
      getConsentHistory: jest.fn().mockReturnValue(of([])),
      exportData: jest.fn().mockReturnValue(of({})),
      deleteAccount: jest.fn().mockReturnValue(of(undefined)),
    } as any;

    await TestBed.configureTestingModule({
      imports: [ConsentManagementComponent, NoopAnimationsModule],
      providers: [
        { provide: RgpdService, useValue: rgpdServiceMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ConsentManagementComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should load consents on init', () => {
    expect(rgpdServiceMock.getConsentStatus).toHaveBeenCalled();
  });

  it('should correctly identify granted consents', () => {
    expect(component.isGranted('TERMS_OF_SERVICE')).toBe(true);
    expect(component.isGranted('MARKETING_EMAIL')).toBe(false);
  });

  it('should have all consent types listed', () => {
    expect(component.consentTypes.length).toBe(4);
    expect(component.consentTypes).toContain('TERMS_OF_SERVICE');
    expect(component.consentTypes).toContain('MARKETING_EMAIL');
  });
});
