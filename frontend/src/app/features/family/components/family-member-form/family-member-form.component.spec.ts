// frontend/src/app/features/family/components/family-member-form/family-member-form.component.spec.ts
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { FamilyMemberFormComponent } from './family-member-form.component';
import { FamilyMember } from '../../models/family.model';

/**
 * Unit tests for FamilyMemberFormComponent.
 *
 * Story: S2-002 — Angular Family Feature
 * Tests: 4 test methods
 *
 * These tests verify:
 * 1. Component creates successfully
 * 2. Form is invalid when empty (required fields)
 * 3. Form emits save event with valid data
 * 4. Form is pre-populated when member is provided for editing
 */
describe('FamilyMemberFormComponent', () => {
  let component: FamilyMemberFormComponent;
  let fixture: ComponentFixture<FamilyMemberFormComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FamilyMemberFormComponent],
      providers: [provideNoopAnimations()],
    }).compileComponents();

    fixture = TestBed.createComponent(FamilyMemberFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should have invalid form when fields are empty', () => {
    expect(component.memberForm.invalid).toBe(true);
  });

  it('should emit save event when form is valid and submitted', () => {
    const saveSpy = jest.spyOn(component.save, 'emit');

    component.memberForm.setValue({
      firstName: 'Jean',
      lastName: 'Dupont',
      dateOfBirth: '2010-05-15',
      relationship: 'CHILD',
      medicalNote: '',
    });

    component.onSubmit();

    expect(saveSpy).toHaveBeenCalledWith({
      firstName: 'Jean',
      lastName: 'Dupont',
      dateOfBirth: '2010-05-15',
      relationship: 'CHILD',
      medicalNote: undefined,
    });
  });

  it('should pre-populate form when member is provided', () => {
    const member: FamilyMember = {
      id: 1,
      familyId: 10,
      firstName: 'Marie',
      lastName: 'Dupont',
      dateOfBirth: '2008-03-22',
      age: 17,
      relationship: 'CHILD',
      medicalNote: 'Allergie arachides',
      createdAt: '2025-01-01T00:00:00',
    };

    component.member = member;
    component.ngOnChanges({
      member: {
        currentValue: member,
        previousValue: null,
        firstChange: true,
        isFirstChange: () => true,
      },
    });

    expect(component.memberForm.get('firstName')?.value).toBe('Marie');
    expect(component.memberForm.get('lastName')?.value).toBe('Dupont');
    expect(component.memberForm.get('relationship')?.value).toBe('CHILD');
    expect(component.memberForm.get('medicalNote')?.value).toBe('Allergie arachides');
  });
});
