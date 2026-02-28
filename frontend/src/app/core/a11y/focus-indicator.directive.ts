// frontend/src/app/core/a11y/focus-indicator.directive.ts

import { Directive, ElementRef, OnInit, OnDestroy, Renderer2 } from '@angular/core';

/**
 * Directive that ensures visible focus indicators on all interactive elements.
 * Applied globally via the a11y module.
 *
 * Adds a visible 2px solid outline on :focus-visible that matches WCAG 2.4.7.
 * Uses :focus-visible to avoid showing outlines on mouse click (only keyboard).
 *
 * Usage: Applied automatically via global stylesheet (_a11y.scss).
 * This directive is available for cases where programmatic focus control
 * needs additional visual feedback.
 */
@Directive({
  selector: '[appFocusIndicator]',
  standalone: true
})
export class FocusIndicatorDirective implements OnInit, OnDestroy {
  private focusListener?: () => void;
  private blurListener?: () => void;

  constructor(
    private el: ElementRef<HTMLElement>,
    private renderer: Renderer2
  ) {}

  ngOnInit(): void {
    const element = this.el.nativeElement;

    this.focusListener = this.renderer.listen(element, 'focus', () => {
      this.renderer.addClass(element, 'a11y-focus--ring');
    });

    this.blurListener = this.renderer.listen(element, 'blur', () => {
      this.renderer.removeClass(element, 'a11y-focus--ring');
    });
  }

  ngOnDestroy(): void {
    this.focusListener?.();
    this.blurListener?.();
  }
}
