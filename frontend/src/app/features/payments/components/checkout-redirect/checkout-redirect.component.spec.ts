// frontend/src/app/features/payments/components/checkout-redirect/checkout-redirect.component.spec.ts
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { CheckoutRedirectComponent } from './checkout-redirect.component';

describe('CheckoutRedirectComponent', () => {
  let component: CheckoutRedirectComponent;
  let fixture: ComponentFixture<CheckoutRedirectComponent>;
  let router: Router;

  beforeEach(() => {
    TestBed.resetTestingModule();
  });

  function createComponent(queryParams: Record<string, string>): void {
    TestBed.configureTestingModule({
      imports: [CheckoutRedirectComponent, NoopAnimationsModule],
      providers: [
        provideRouter([
          { path: 'payments', children: [] },
          { path: 'payments/:id', children: [] },
        ]),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: convertToParamMap(queryParams),
            },
          },
        },
      ],
    }).compileComponents();

    router = TestBed.inject(Router);
    jest.spyOn(router, 'navigate').mockResolvedValue(true);

    fixture = TestBed.createComponent(CheckoutRedirectComponent);
    component = fixture.componentInstance;
  }

  it('should show loading spinner when initializing', () => {
    createComponent({ status: 'success', paymentId: '42' });
    fixture.detectChanges();

    // Before timeout, should show loading
    expect(component.result()).toBe('loading');
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('mat-spinner')).toBeTruthy();
  });

  it('should show success when status is success', fakeAsync(() => {
    createComponent({ status: 'success', paymentId: '42' });
    fixture.detectChanges();

    tick(800);
    fixture.detectChanges();

    expect(component.result()).toBe('success');
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Paiement reussi');
  }));

  it('should show error when status is error', fakeAsync(() => {
    createComponent({ status: 'error', paymentId: '42' });
    fixture.detectChanges();

    tick(800);
    fixture.detectChanges();

    expect(component.result()).toBe('error');
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Erreur de paiement');
  }));

  it('should show cancelled when status is cancelled', fakeAsync(() => {
    createComponent({ status: 'cancelled', paymentId: '42' });
    fixture.detectChanges();

    tick(800);
    fixture.detectChanges();

    expect(component.result()).toBe('cancelled');
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Paiement annule');
  }));

  it('should navigate to payment detail when view payment is clicked', fakeAsync(() => {
    createComponent({ status: 'success', paymentId: '42' });
    fixture.detectChanges();

    tick(800);
    fixture.detectChanges();

    component.viewPayment();

    expect(router.navigate).toHaveBeenCalledWith(['/payments', '42']);
  }));
});
