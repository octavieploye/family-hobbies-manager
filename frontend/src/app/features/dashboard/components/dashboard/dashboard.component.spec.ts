// frontend/src/app/features/dashboard/components/dashboard/dashboard.component.spec.ts
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { DashboardComponent } from './dashboard.component';
import { DashboardService } from '../../services/dashboard.service';
import { of, throwError } from 'rxjs';
import { Family } from '../../../family/models/family.model';

describe('DashboardComponent', () => {
  let component: DashboardComponent;
  let fixture: ComponentFixture<DashboardComponent>;
  let dashboardServiceMock: jest.Mocked<DashboardService>;

  const mockFamily: Family = {
    id: 1,
    name: 'Famille Dupont',
    createdBy: 1,
    members: [
      {
        id: 5,
        familyId: 1,
        firstName: 'Marie',
        lastName: 'Dupont',
        dateOfBirth: '2015-03-15',
        age: 9,
        relationship: 'CHILD',
        createdAt: '2024-01-01T10:00:00',
      },
    ],
    createdAt: '2024-01-01T10:00:00',
    updatedAt: '2024-01-01T10:00:00',
  };

  beforeEach(async () => {
    dashboardServiceMock = {
      getFamily: jest.fn().mockReturnValue(of(mockFamily)),
      getFamilySubscriptions: jest.fn().mockReturnValue(of([])),
      getMemberAttendanceSummaries: jest.fn().mockReturnValue(of([])),
      getMemberAttendanceSummary: jest.fn().mockReturnValue(of({})),
      mapToSubscriptionSummaries: jest.fn().mockReturnValue([]),
      mapToMemberAttendance: jest.fn().mockReturnValue([]),
    } as any;

    await TestBed.configureTestingModule({
      imports: [DashboardComponent, NoopAnimationsModule],
      providers: [
        { provide: DashboardService, useValue: dashboardServiceMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should display page title', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Tableau de bord');
  });

  it('should load family data on init', () => {
    expect(dashboardServiceMock.getFamily).toHaveBeenCalled();
  });

  it('should set family name and member count after loading', () => {
    expect(component.familyName()).toBe('Famille Dupont');
    expect(component.memberCount()).toBe(1);
  });
});
