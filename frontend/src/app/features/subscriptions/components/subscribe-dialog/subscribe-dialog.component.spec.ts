// frontend/src/app/features/subscriptions/components/subscribe-dialog/subscribe-dialog.component.spec.ts
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MockStore, provideMockStore } from '@ngrx/store/testing';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { SubscribeDialogComponent, SubscribeDialogData } from './subscribe-dialog.component';
import { SubscriptionState, initialSubscriptionState } from '../../store/subscription.reducer';

describe('SubscribeDialogComponent', () => {
  let component: SubscribeDialogComponent;
  let fixture: ComponentFixture<SubscribeDialogComponent>;
  let store: MockStore;
  let dialogRef: jest.Mocked<MatDialogRef<SubscribeDialogComponent>>;

  const mockDialogData: SubscribeDialogData = {
    activityId: 5,
    activityName: 'Natation enfants',
    associationName: 'Lyon Natation Metropole',
    familyMembers: [
      {
        id: 10,
        familyId: 3,
        firstName: 'Marie',
        lastName: 'Dupont',
        dateOfBirth: '2016-05-15',
        age: 8,
        relationship: 'CHILD',
        createdAt: '2024-01-01T10:00:00',
      },
    ],
    familyId: 3,
  };

  const initialState: { subscriptions: SubscriptionState } = {
    subscriptions: { ...initialSubscriptionState },
  };

  beforeEach(async () => {
    dialogRef = {
      close: jest.fn(),
    } as any;

    await TestBed.configureTestingModule({
      imports: [SubscribeDialogComponent, NoopAnimationsModule],
      providers: [
        provideMockStore({ initialState }),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: mockDialogData },
      ],
    }).compileComponents();

    store = TestBed.inject(MockStore);
    jest.spyOn(store, 'dispatch');

    fixture = TestBed.createComponent(SubscribeDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should display activity name in title', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Natation enfants');
  });

  it('should display association name', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Lyon Natation Metropole');
  });

  it('should have submit button with S\'inscrire text', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain("S'inscrire");
  });

  it('should have cancel button', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Annuler');
  });

  it('should close dialog on cancel', () => {
    component.onCancel();
    expect(dialogRef.close).toHaveBeenCalledWith(false);
  });

  it('should not submit when form is invalid', () => {
    component.onSubmit();
    expect(store.dispatch).not.toHaveBeenCalled();
  });

  it('should dispatch and close when form is valid', () => {
    component.subscribeForm.patchValue({
      familyMemberId: 10,
      subscriptionType: 'ADHESION',
      startDate: '2024-09-01',
    });

    component.onSubmit();
    expect(store.dispatch).toHaveBeenCalled();
    expect(dialogRef.close).toHaveBeenCalledWith(true);
  });
});
