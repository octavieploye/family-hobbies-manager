// frontend/src/app/features/settings/components/data-export/data-export.component.spec.ts
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { DataExportComponent } from './data-export.component';
import { RgpdService } from '../../services/rgpd.service';
import { of } from 'rxjs';
import { UserDataExport } from '@shared/models/rgpd.model';

describe('DataExportComponent', () => {
  let component: DataExportComponent;
  let fixture: ComponentFixture<DataExportComponent>;
  let rgpdServiceMock: jest.Mocked<RgpdService>;

  const mockExport: UserDataExport = {
    userId: 1,
    email: 'test@example.com',
    firstName: 'Jean',
    lastName: 'Dupont',
    phone: null,
    role: 'FAMILY',
    status: 'ACTIVE',
    createdAt: '2024-01-01T10:00:00',
    lastLoginAt: '2024-09-15T10:00:00',
    family: null,
    consentHistory: [],
    exportedAt: '2024-09-15T10:00:00',
  };

  beforeEach(async () => {
    rgpdServiceMock = {
      getConsentStatus: jest.fn().mockReturnValue(of([])),
      recordConsent: jest.fn().mockReturnValue(of({})),
      getConsentHistory: jest.fn().mockReturnValue(of([])),
      exportData: jest.fn().mockReturnValue(of(mockExport)),
      deleteAccount: jest.fn().mockReturnValue(of(undefined)),
    } as any;

    await TestBed.configureTestingModule({
      imports: [DataExportComponent, NoopAnimationsModule],
      providers: [
        { provide: RgpdService, useValue: rgpdServiceMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DataExportComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should display export button', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Exporter mes donn\u00e9es');
  });

  it('should return empty string when no export data', () => {
    expect(component.getFormattedJson()).toBe('');
  });
});
