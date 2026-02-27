// frontend/src/app/features/family/components/family-member-list/family-member-list.component.spec.ts
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { FamilyMemberListComponent } from './family-member-list.component';
import { FamilyMember } from '../../models/family.model';

/**
 * Unit tests for FamilyMemberListComponent.
 *
 * Story: S2-002 — Angular Family Feature
 * Tests: 3 test methods
 *
 * These tests verify:
 * 1. Component creates successfully
 * 2. Edit event emits the correct member
 * 3. Delete event emits member id after confirmation
 */
describe('FamilyMemberListComponent', () => {
  let component: FamilyMemberListComponent;
  let fixture: ComponentFixture<FamilyMemberListComponent>;

  const mockMembers: FamilyMember[] = [
    {
      id: 1,
      familyId: 10,
      firstName: 'Jean',
      lastName: 'Dupont',
      dateOfBirth: '1985-06-15',
      age: 40,
      relationship: 'PARENT',
      createdAt: '2025-01-01T00:00:00',
    },
    {
      id: 2,
      familyId: 10,
      firstName: 'Marie',
      lastName: 'Dupont',
      dateOfBirth: '2010-03-22',
      age: 15,
      relationship: 'CHILD',
      createdAt: '2025-01-01T00:00:00',
    },
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FamilyMemberListComponent],
      providers: [provideNoopAnimations()],
    }).compileComponents();

    fixture = TestBed.createComponent(FamilyMemberListComponent);
    component = fixture.componentInstance;
    component.members = mockMembers;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should emit edit event with the selected member', () => {
    const editSpy = jest.spyOn(component.edit, 'emit');

    component.onEdit(mockMembers[0]);

    expect(editSpy).toHaveBeenCalledWith(mockMembers[0]);
  });

  it('should emit delete event with member id when confirmed', () => {
    const deleteSpy = jest.spyOn(component.delete, 'emit');
    jest.spyOn(window, 'confirm').mockReturnValue(true);

    component.onDelete(mockMembers[0]);

    expect(deleteSpy).toHaveBeenCalledWith(1);
  });
});
