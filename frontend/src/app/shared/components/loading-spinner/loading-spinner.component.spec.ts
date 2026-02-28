// frontend/src/app/shared/components/loading-spinner/loading-spinner.component.spec.ts

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { LoadingSpinnerComponent } from './loading-spinner.component';

describe('LoadingSpinnerComponent', () => {
  let component: LoadingSpinnerComponent;
  let fixture: ComponentFixture<LoadingSpinnerComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LoadingSpinnerComponent, NoopAnimationsModule],
    }).compileComponents();

    fixture = TestBed.createComponent(LoadingSpinnerComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should render the spinner container', () => {
    const container: HTMLElement = fixture.nativeElement.querySelector(
      '.loading-spinner'
    );
    expect(container).toBeTruthy();
  });

  it('should have aria-live="polite" for screen reader announcements', () => {
    const container: HTMLElement = fixture.nativeElement.querySelector(
      '.loading-spinner'
    );
    expect(container.getAttribute('aria-live')).toBe('polite');
  });

  it('should display the default loading message in French', () => {
    const messageEl: HTMLElement = fixture.nativeElement.querySelector(
      '.loading-spinner__message'
    );
    expect(messageEl).toBeTruthy();
    expect(messageEl.textContent?.trim()).toBe('Chargement en cours...');
  });

  it('should display a custom message when provided', () => {
    component.message = 'Chargement des familles...';
    fixture.detectChanges();

    const messageEl: HTMLElement = fixture.nativeElement.querySelector(
      '.loading-spinner__message'
    );
    expect(messageEl.textContent?.trim()).toBe('Chargement des familles...');
  });

  it('should have role="status" for accessibility', () => {
    const container: HTMLElement = fixture.nativeElement.querySelector(
      '.loading-spinner'
    );
    expect(container.getAttribute('role')).toBe('status');
  });
});
