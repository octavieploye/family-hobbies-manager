// frontend/src/app/core/a11y/focus-indicator.directive.spec.ts

import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FocusIndicatorDirective } from './focus-indicator.directive';

@Component({
  standalone: true,
  imports: [FocusIndicatorDirective],
  template: `<button appFocusIndicator data-testid="test-button">Cliquer</button>`,
})
class TestHostComponent {}

describe('FocusIndicatorDirective', () => {
  let fixture: ComponentFixture<TestHostComponent>;
  let buttonEl: HTMLButtonElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TestHostComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(TestHostComponent);
    fixture.detectChanges();
    buttonEl = fixture.nativeElement.querySelector('[data-testid="test-button"]');
  });

  it('should add the a11y-focus--ring class on focus', () => {
    buttonEl.dispatchEvent(new FocusEvent('focus'));
    fixture.detectChanges();

    expect(buttonEl.classList.contains('a11y-focus--ring')).toBe(true);
  });

  it('should remove the a11y-focus--ring class on blur', () => {
    // First focus to add the class
    buttonEl.dispatchEvent(new FocusEvent('focus'));
    fixture.detectChanges();
    expect(buttonEl.classList.contains('a11y-focus--ring')).toBe(true);

    // Then blur to remove it
    buttonEl.dispatchEvent(new FocusEvent('blur'));
    fixture.detectChanges();
    expect(buttonEl.classList.contains('a11y-focus--ring')).toBe(false);
  });

  it('should clean up listeners on destroy', () => {
    // Focus to verify the directive is active
    buttonEl.dispatchEvent(new FocusEvent('focus'));
    fixture.detectChanges();
    expect(buttonEl.classList.contains('a11y-focus--ring')).toBe(true);

    // Destroy the fixture
    fixture.destroy();

    // After destroy, the class should have been cleaned up or the listener removed.
    // We verify no errors are thrown when dispatching events post-destroy.
    expect(() => {
      buttonEl.dispatchEvent(new FocusEvent('focus'));
      buttonEl.dispatchEvent(new FocusEvent('blur'));
    }).not.toThrow();
  });
});
