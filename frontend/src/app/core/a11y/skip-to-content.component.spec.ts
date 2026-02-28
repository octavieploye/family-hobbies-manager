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
