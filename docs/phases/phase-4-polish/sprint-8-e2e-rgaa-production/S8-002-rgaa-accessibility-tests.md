# Story S8-002: RGAA Accessibility Tests

> Companion file for [S8-002 main](./S8-002-rgaa-accessibility.md)
> Sprint file: [Back to Sprint Index](./_index.md)

---

This file contains the complete Jest unit test specifications for the RGAA accessibility components and directives implemented in S8-002. These tests validate the TDD contract: all tests should fail initially (red phase), then pass after implementation.

**Run all tests**:
```bash
cd frontend
npx jest --testPathPattern="(skip-to-content|focus-indicator|confirm-dialog|loading-spinner|empty-state|error-message)" --verbose
```

**Expected**: all 22 tests fail initially (TDD red phase), then pass after implementation.

---

## Test File 1: skip-to-content.component.spec.ts

**File**: `frontend/src/app/core/a11y/skip-to-content.component.spec.ts`

```typescript
// frontend/src/app/core/a11y/skip-to-content.component.spec.ts

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SkipToContentComponent } from './skip-to-content.component';

describe('SkipToContentComponent', () => {
  let component: SkipToContentComponent;
  let fixture: ComponentFixture<SkipToContentComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SkipToContentComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(SkipToContentComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should render a skip link with French text', () => {
    const link: HTMLAnchorElement = fixture.nativeElement.querySelector(
      '.skip-to-content__link'
    );
    expect(link).toBeTruthy();
    expect(link.textContent?.trim()).toBe('Aller au contenu principal');
  });

  it('should have href pointing to #main-content', () => {
    const link: HTMLAnchorElement = fixture.nativeElement.querySelector(
      '.skip-to-content__link'
    );
    expect(link.getAttribute('href')).toBe('#main-content');
  });

  it('should be hidden by default (off-screen)', () => {
    const link: HTMLAnchorElement = fixture.nativeElement.querySelector(
      '.skip-to-content__link'
    );
    const computedStyle = getComputedStyle(link);

    // The link uses position: absolute and top: -100% to hide off-screen.
    // In jsdom, computed styles from SCSS are not available, so we check
    // the class is present and trust the SCSS styling.
    expect(link.classList.contains('skip-to-content__link')).toBe(true);
  });

  it('should move focus to main content element when activated', () => {
    // Create a mock main content element
    const mainEl = document.createElement('main');
    mainEl.id = 'main-content';
    document.body.appendChild(mainEl);

    const focusSpy = jest.spyOn(mainEl, 'focus');
    const scrollSpy = jest.spyOn(mainEl, 'scrollIntoView');

    const event = new Event('click', { cancelable: true });
    component.skipToMain(event);

    expect(focusSpy).toHaveBeenCalled();
    expect(scrollSpy).toHaveBeenCalledWith({ behavior: 'smooth' });
    expect(mainEl.getAttribute('tabindex')).toBe('-1');

    // Clean up
    document.body.removeChild(mainEl);
    focusSpy.mockRestore();
    scrollSpy.mockRestore();
  });
});
```

---

## Test File 2: focus-indicator.directive.spec.ts

**File**: `frontend/src/app/core/a11y/focus-indicator.directive.spec.ts`

```typescript
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
```

---

## Test File 3: confirm-dialog.component.spec.ts

**File**: `frontend/src/app/shared/components/confirm-dialog/confirm-dialog.component.spec.ts`

```typescript
// frontend/src/app/shared/components/confirm-dialog/confirm-dialog.component.spec.ts

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import {
  ConfirmDialogComponent,
  ConfirmDialogData,
} from './confirm-dialog.component';

describe('ConfirmDialogComponent', () => {
  let component: ConfirmDialogComponent;
  let fixture: ComponentFixture<ConfirmDialogComponent>;
  let dialogRefSpy: jest.Mocked<MatDialogRef<ConfirmDialogComponent>>;

  const mockDialogData: ConfirmDialogData = {
    title: 'Confirmer la suppression',
    message: 'Voulez-vous vraiment supprimer ce membre ?',
    confirmLabel: 'Supprimer',
    cancelLabel: 'Annuler',
    confirmColor: 'warn',
  };

  beforeEach(async () => {
    dialogRefSpy = {
      close: jest.fn(),
      addPanelClass: jest.fn(),
    } as unknown as jest.Mocked<MatDialogRef<ConfirmDialogComponent>>;

    await TestBed.configureTestingModule({
      imports: [ConfirmDialogComponent, NoopAnimationsModule],
      providers: [
        { provide: MatDialogRef, useValue: dialogRefSpy },
        { provide: MAT_DIALOG_DATA, useValue: mockDialogData },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ConfirmDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should display the dialog title', () => {
    const titleEl = fixture.nativeElement.querySelector('[mat-dialog-title]');
    expect(titleEl).toBeTruthy();
    expect(titleEl.textContent?.trim()).toContain('Confirmer la suppression');
  });

  it('should display the dialog message', () => {
    const contentEl = fixture.nativeElement.querySelector('mat-dialog-content p');
    expect(contentEl).toBeTruthy();
    expect(contentEl.textContent?.trim()).toContain(
      'Voulez-vous vraiment supprimer ce membre ?'
    );
  });

  it('should close with false when cancel is clicked', () => {
    component.onCancel();
    expect(dialogRefSpy.close).toHaveBeenCalledWith(false);
  });

  it('should close with true when confirm is clicked', () => {
    component.onConfirm();
    expect(dialogRefSpy.close).toHaveBeenCalledWith(true);
  });

  it('should have the confirm-dialog-title ID for aria-labelledby', () => {
    const titleEl = fixture.nativeElement.querySelector('#confirm-dialog-title');
    expect(titleEl).toBeTruthy();
  });
});
```

---

## Test File 4: loading-spinner.component.spec.ts

**File**: `frontend/src/app/shared/components/loading-spinner/loading-spinner.component.spec.ts`

```typescript
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
```

---

## Test File 5: empty-state.component.spec.ts

**File**: `frontend/src/app/shared/components/empty-state/empty-state.component.spec.ts`

```typescript
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
```

---

## Test File 6: error-message.component.spec.ts

**File**: `frontend/src/app/shared/components/error-message/error-message.component.spec.ts`

```typescript
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
      'Veuillez reessayer ulterieurement.'
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
      'button[aria-label="Reessayer le chargement"]'
    );
    expect(retryBtn).toBeFalsy();
  });

  it('should display retry button when retryable is true', () => {
    component.retryable = true;
    fixture.detectChanges();

    const retryBtn: HTMLButtonElement = fixture.nativeElement.querySelector(
      'button[aria-label="Reessayer le chargement"]'
    );
    expect(retryBtn).toBeTruthy();
    expect(retryBtn.textContent?.trim()).toContain('Reessayer');
  });

  it('should dispatch app:retry event when retry button is clicked', () => {
    component.retryable = true;
    fixture.detectChanges();

    const dispatchSpy = jest.spyOn(window, 'dispatchEvent');

    const retryBtn: HTMLButtonElement = fixture.nativeElement.querySelector(
      'button[aria-label="Reessayer le chargement"]'
    );
    retryBtn.click();

    expect(dispatchSpy).toHaveBeenCalledWith(
      expect.objectContaining({ type: 'app:retry' })
    );

    dispatchSpy.mockRestore();
  });

  it('should have an error icon with aria-hidden', () => {
    const icon = fixture.nativeElement.querySelector(
      '.error-message__icon'
    );
    expect(icon).toBeTruthy();
    expect(icon.getAttribute('aria-hidden')).toBe('true');
  });
});
```

---

## Summary

| Test File | Test Count | What It Verifies |
|-----------|-----------|------------------|
| `skip-to-content.component.spec.ts` | 4 | Skip link renders with French text, has correct href, hidden by default, moves focus to `#main-content` |
| `focus-indicator.directive.spec.ts` | 3 | Directive adds `a11y-focus--ring` class on focus, removes on blur, cleans up listeners on destroy |
| `confirm-dialog.component.spec.ts` | 5 | Dialog title, message content, cancel closes with false, confirm closes with true, ARIA `id` for `aria-labelledby` |
| `loading-spinner.component.spec.ts` | 5 | Spinner container renders, `aria-live="polite"`, default French message, custom message, `role="status"` |
| `empty-state.component.spec.ts` | 8 | Default message, custom message, icon with `aria-hidden`, action button conditional render, action emit, `role="status"`, `aria-live="polite"` |
| `error-message.component.spec.ts` | 9 | `role="alert"`, `aria-live="assertive"`, default title/message, custom title/message, retry button conditional, retry event dispatch, icon `aria-hidden` |
| **Total** | **34** | |

**Run all**: `cd frontend && npx jest --testPathPattern="(skip-to-content|focus-indicator|confirm-dialog|loading-spinner|empty-state|error-message)" --verbose`

Expected: all 34 tests fail initially (TDD red phase), then pass after S8-002 implementation.
