// frontend/src/app/shared/components/error-message/error-message.component.spec.ts

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ErrorMessageComponent } from './error-message.component';

describe('ErrorMessageComponent', () => {
  let component: ErrorMessageComponent;
  let fixture: ComponentFixture<ErrorMessageComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ErrorMessageComponent, NoopAnimationsModule],
    }).compileComponents();

    fixture = TestBed.createComponent(ErrorMessageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should have role="alert" on the container', () => {
    const container: HTMLElement = fixture.nativeElement.querySelector(
      '.error-message'
    );
    expect(container.getAttribute('role')).toBe('alert');
  });

  it('should have aria-live="assertive"', () => {
    const container: HTMLElement = fixture.nativeElement.querySelector(
      '.error-message'
    );
    expect(container.getAttribute('aria-live')).toBe('assertive');
  });

  it('should display the default title', () => {
    const titleEl: HTMLElement = fixture.nativeElement.querySelector(
      '.error-message__title'
    );
    expect(titleEl).toBeTruthy();
    expect(titleEl.textContent?.trim()).toBe('Une erreur est survenue');
  });

  it('should display the default message', () => {
    const detailEl: HTMLElement = fixture.nativeElement.querySelector(
      '.error-message__detail'
    );
    expect(detailEl).toBeTruthy();
    expect(detailEl.textContent?.trim()).toBe(
      'Veuillez réessayer ultérieurement.'
    );
  });

  it('should display custom title and message', () => {
    component.title = 'Erreur de connexion';
    component.message = 'Le serveur ne repond pas. Veuillez verifier votre connexion.';
    fixture.detectChanges();

    const titleEl: HTMLElement = fixture.nativeElement.querySelector(
      '.error-message__title'
    );
    const detailEl: HTMLElement = fixture.nativeElement.querySelector(
      '.error-message__detail'
    );

    expect(titleEl.textContent?.trim()).toBe('Erreur de connexion');
    expect(detailEl.textContent?.trim()).toContain('Le serveur ne repond pas');
  });

  it('should not display retry button by default', () => {
    const retryBtn = fixture.nativeElement.querySelector(
      'button[aria-label="Réessayer le chargement"]'
    );
    expect(retryBtn).toBeFalsy();
  });

  it('should display retry button when retryable is true', () => {
    component.retryable = true;
    fixture.detectChanges();

    const retryBtn: HTMLButtonElement = fixture.nativeElement.querySelector(
      'button[aria-label="Réessayer le chargement"]'
    );
    expect(retryBtn).toBeTruthy();
    expect(retryBtn.textContent?.trim()).toContain('Réessayer');
  });

  it('should emit retry event when retry button is clicked', () => {
    component.retryable = true;
    fixture.detectChanges();

    const retrySpy = jest.spyOn(component.retry, 'emit');

    const retryBtn: HTMLButtonElement = fixture.nativeElement.querySelector(
      'button[aria-label="Réessayer le chargement"]'
    );
    retryBtn.click();

    expect(retrySpy).toHaveBeenCalledTimes(1);
  });

  it('should have an error icon with aria-hidden', () => {
    const icon = fixture.nativeElement.querySelector(
      '.error-message__icon'
    );
    expect(icon).toBeTruthy();
    expect(icon.getAttribute('aria-hidden')).toBe('true');
  });
});
