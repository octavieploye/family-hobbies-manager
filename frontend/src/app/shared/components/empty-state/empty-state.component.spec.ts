// frontend/src/app/shared/components/empty-state/empty-state.component.spec.ts

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { EmptyStateComponent } from './empty-state.component';

describe('EmptyStateComponent', () => {
  let component: EmptyStateComponent;
  let fixture: ComponentFixture<EmptyStateComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EmptyStateComponent, NoopAnimationsModule],
    }).compileComponents();

    fixture = TestBed.createComponent(EmptyStateComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should render the default empty message', () => {
    const messageEl: HTMLElement = fixture.nativeElement.querySelector(
      '.empty-state__message'
    );
    expect(messageEl).toBeTruthy();
    expect(messageEl.textContent?.trim()).toBe('Aucun resultat.');
  });

  it('should render a custom message when provided', () => {
    component.message = 'Aucun membre dans cette famille.';
    fixture.detectChanges();

    const messageEl: HTMLElement = fixture.nativeElement.querySelector(
      '.empty-state__message'
    );
    expect(messageEl.textContent?.trim()).toBe(
      'Aucun membre dans cette famille.'
    );
  });

  it('should render an icon', () => {
    const iconEl = fixture.nativeElement.querySelector('.empty-state__icon');
    expect(iconEl).toBeTruthy();
    expect(iconEl.getAttribute('aria-hidden')).toBe('true');
  });

  it('should render an action button when actionLabel is provided', () => {
    component.actionLabel = 'Ajouter un membre';
    fixture.detectChanges();

    const button = fixture.nativeElement.querySelector('button[mat-raised-button]');
    expect(button).toBeTruthy();
    expect(button.textContent?.trim()).toContain('Ajouter un membre');
  });

  it('should not render an action button when actionLabel is not provided', () => {
    component.actionLabel = undefined;
    fixture.detectChanges();

    const button = fixture.nativeElement.querySelector('button[mat-raised-button]');
    expect(button).toBeFalsy();
  });

  it('should emit action event when action button is clicked', () => {
    component.actionLabel = 'Ajouter un membre';
    fixture.detectChanges();

    const emitSpy = jest.spyOn(component.action, 'emit');

    const button: HTMLButtonElement = fixture.nativeElement.querySelector(
      'button[mat-raised-button]'
    );
    button.click();

    expect(emitSpy).toHaveBeenCalled();
  });

  it('should have role="status" for accessibility', () => {
    const container: HTMLElement = fixture.nativeElement.querySelector(
      '.empty-state'
    );
    expect(container.getAttribute('role')).toBe('status');
  });

  it('should have aria-live="polite"', () => {
    const container: HTMLElement = fixture.nativeElement.querySelector(
      '.empty-state'
    );
    expect(container.getAttribute('aria-live')).toBe('polite');
  });
});
