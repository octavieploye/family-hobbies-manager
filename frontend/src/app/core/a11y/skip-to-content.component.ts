// frontend/src/app/core/a11y/skip-to-content.component.ts

import { Component } from '@angular/core';

@Component({
  selector: 'app-skip-to-content',
  standalone: true,
  templateUrl: './skip-to-content.component.html',
  styleUrls: ['./skip-to-content.component.scss']
})
export class SkipToContentComponent {

  /**
   * Moves focus to the main content area and scrolls it into view.
   * Uses getElementById to find the target -- the <main> element must have id="main-content".
   */
  skipToMain(event: Event): void {
    event.preventDefault();
    const main = document.getElementById('main-content');
    if (main) {
      main.setAttribute('tabindex', '-1');
      main.focus();
      main.scrollIntoView({ behavior: 'smooth' });
      // Remove tabindex after blur to avoid persistent tab stop
      main.addEventListener('blur', () => main.removeAttribute('tabindex'), {
        once: true
      });
    }
  }
}
