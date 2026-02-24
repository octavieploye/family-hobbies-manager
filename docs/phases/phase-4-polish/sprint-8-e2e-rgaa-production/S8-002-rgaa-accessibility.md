# Story S8-002: Implement RGAA Accessibility

> 8 points | Priority: P0 | Service: frontend
> Sprint file: [Back to Sprint Index](./_index.md)

---

## Context

RGAA (Referentiel General d'Amelioration de l'Accessibilite) is the French government accessibility standard, based on WCAG 2.1 AA. This story audits and remediates the entire Angular frontend to achieve zero critical/serious axe-core violations. The work covers 8 categories: skip-to-content and semantic landmarks, form accessibility, table markup, keyboard navigation, color contrast, image alt text, dialog focus management, and loading states. Every Angular component in the application must be touched to ensure RGAA compliance.

All new components use Angular 17+ standalone patterns with `@if`/`@for` control flow. SCSS follows BEM naming. User-facing text is in French.

## Cross-References

- **S8-003** (Playwright Accessibility Tests) validates the fixes applied here
- **S8-001** (E2E test infrastructure) provides the Playwright setup used by S8-003
- All Angular components from Sprints 1-7 are prerequisites (layout, auth, families, associations, subscriptions, attendance, payments, notifications)

## Dependencies

- All Angular components from previous sprints must be built
- Angular Material configured with a custom theme
- `@angular/cdk/a11y` module available (FocusTrap, LiveAnnouncer)

---

## Tasks

| # | Task | File(s) | What To Create | How To Verify |
|---|------|---------|----------------|---------------|
| 1 | Axe-core audit | N/A (manual) | Run axe-core on all pages, document all violations | Audit report generated |
| 2 | Skip-to-content component | `core/a11y/skip-to-content.component.*` | Keyboard-only visible skip link | Tab into page, link appears, activates main |
| 3 | Semantic landmarks on all pages | `core/layout/app.component.html` + all page templates | `<main>`, `<nav>`, `<header>`, `<footer>`, `<section>`, `<article>`, `lang="fr"` on `<html>` | axe-core landmark rules pass |
| 4 | Form accessibility | All form templates | `<label>` linked, `aria-required`, `aria-describedby`, `aria-invalid`, `fieldset`/`legend` | axe-core form rules pass |
| 5 | Table accessibility | All table templates | `<caption>`, `<th scope>`, `aria-sort` | axe-core table rules pass |
| 6 | Keyboard navigation | All interactive components | `aria-current="page"`, keyboard menu, focus trap, visible focus | Tab through all elements |
| 7 | Color contrast | `_variables.scss`, `_a11y.scss` | WCAG AA contrast ratios, text labels on status chips | Contrast checker passes |
| 8 | Image alt text | All templates with images | `alt` on all `<img>`, `alt=""` for decorative | axe-core image rules pass |
| 9 | Dialog focus management | All Material dialog components | Focus trapped, ESC closes, `role="dialog"`, `aria-labelledby` | Dialog opens with focus, Tab trapped |
| 10 | Loading states | Loading spinner, content areas | `aria-live="polite"`, `aria-busy` | Screen reader announces loading |
| 11 | Focus indicator directive | `core/a11y/focus-indicator.directive.ts` | Visible 2px solid focus ring on all interactive elements | Focus visible on every element |
| 12 | Global a11y stylesheet | `shared/styles/_a11y.scss` | Global accessibility overrides | All styles applied |
| 13 | WCAG color palette | `shared/styles/_variables.scss` | Updated palette with >= 4.5:1 ratios | Contrast ratios verified |

---

## Task 1: Axe-core Audit

Run axe-core DevTools (or `@axe-core/cli`) against every page in the application. Document all violations by severity.

**Audit Process**:

```bash
# Install axe-core CLI
npm install -g @axe-core/cli

# Run against all routes (app must be running on localhost:4200)
axe http://localhost:4200/auth/login \
    http://localhost:4200/auth/register \
    http://localhost:4200/dashboard \
    http://localhost:4200/families \
    http://localhost:4200/associations/search \
    http://localhost:4200/subscriptions \
    http://localhost:4200/attendance \
    http://localhost:4200/payments \
    http://localhost:4200/notifications \
    http://localhost:4200/settings \
    --tags wcag2a,wcag2aa,wcag21aa \
    --exit
```

**Expected Violations (pre-remediation)**:

| Category | Rule ID | Severity | Count (est.) | Pages |
|----------|---------|----------|-------------|-------|
| Landmarks | `landmark-one-main` | serious | 10 | All |
| Landmarks | `region` | moderate | 10 | All |
| Forms | `label` | critical | 15+ | Login, Register, Family, Subscription |
| Forms | `aria-required-children` | serious | 5+ | Forms with radio groups |
| Tables | `th-has-data-cells` | serious | 4 | Members, Subscriptions, Attendance, Payments |
| Color | `color-contrast` | serious | 10+ | All (depending on theme) |
| Images | `image-alt` | critical | 3+ | Association cards, user avatars |
| Keyboard | `focus-visible` | serious | 20+ | All interactive elements |
| Language | `html-has-lang` | serious | 1 | index.html |

**Target**: 0 critical, 0 serious violations after remediation.

---

## Task 2: Skip-to-Content Component

### File: `frontend/src/app/core/a11y/skip-to-content.component.ts`

```typescript
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
   * Uses getElementById to find the target — the <main> element must have id="main-content".
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
```

### File: `frontend/src/app/core/a11y/skip-to-content.component.html`

```html
<!-- frontend/src/app/core/a11y/skip-to-content.component.html -->

<a
  class="skip-to-content__link"
  href="#main-content"
  (click)="skipToMain($event)"
  (keydown.enter)="skipToMain($event)">
  Aller au contenu principal
</a>
```

### File: `frontend/src/app/core/a11y/skip-to-content.component.scss`

```scss
// frontend/src/app/core/a11y/skip-to-content.component.scss

.skip-to-content {
  &__link {
    position: absolute;
    top: -100%;
    left: 16px;
    z-index: 10000;
    padding: 12px 24px;
    background-color: #1a237e;
    color: #ffffff;
    font-size: 1rem;
    font-weight: 600;
    text-decoration: none;
    border-radius: 0 0 4px 4px;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
    transition: top 0.15s ease-in-out;

    &:focus {
      top: 0;
      outline: 3px solid #ff6f00;
      outline-offset: 2px;
    }
  }
}
```

---

## Task 3: Semantic Landmarks on All Pages

### File: `frontend/src/index.html` (update)

```html
<!doctype html>
<html lang="fr">
<head>
  <meta charset="utf-8">
  <title>Family Hobbies Manager</title>
  <base href="/">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="description" content="Gerez les activites de loisirs de votre famille">
  <link rel="icon" type="image/x-icon" href="favicon.ico">
</head>
<body>
  <app-root></app-root>
</body>
</html>
```

### File: `frontend/src/app/core/layout/app.component.html` (update)

The root layout wraps all pages in proper ARIA landmarks.

```html
<!-- frontend/src/app/core/layout/app.component.html -->

<app-skip-to-content />

<header class="app-layout__header" role="banner">
  <app-header />
</header>

<div class="app-layout__body">
  @if (showSidebar()) {
    <nav class="app-layout__sidebar" role="navigation" aria-label="Menu principal">
      <app-sidebar />
    </nav>
  }

  <main id="main-content" class="app-layout__main" role="main">
    <router-outlet />
  </main>
</div>

<footer class="app-layout__footer" role="contentinfo">
  <app-footer />
</footer>
```

### File: `frontend/src/app/core/layout/header/header.component.html` (update pattern)

```html
<!-- frontend/src/app/core/layout/header/header.component.html -->

<mat-toolbar color="primary" role="banner">
  <a routerLink="/dashboard" class="header__logo" aria-label="Accueil - Family Hobbies Manager">
    <img src="assets/logo.svg" alt="" aria-hidden="true" class="header__logo-img" />
    <span class="header__title">Family Hobbies Manager</span>
  </a>

  <nav class="header__nav" aria-label="Navigation principale">
    <a mat-button
       routerLink="/dashboard"
       routerLinkActive="header__nav-link--active"
       [attr.aria-current]="isActive('/dashboard') ? 'page' : null">
      Tableau de bord
    </a>
    <a mat-button
       routerLink="/families"
       routerLinkActive="header__nav-link--active"
       [attr.aria-current]="isActive('/families') ? 'page' : null">
      Familles
    </a>
    <a mat-button
       routerLink="/associations/search"
       routerLinkActive="header__nav-link--active"
       [attr.aria-current]="isActive('/associations') ? 'page' : null">
      Associations
    </a>
    <a mat-button
       routerLink="/subscriptions"
       routerLinkActive="header__nav-link--active"
       [attr.aria-current]="isActive('/subscriptions') ? 'page' : null">
      Inscriptions
    </a>
    <a mat-button
       routerLink="/attendance"
       routerLinkActive="header__nav-link--active"
       [attr.aria-current]="isActive('/attendance') ? 'page' : null">
      Presence
    </a>
    <a mat-button
       routerLink="/payments"
       routerLinkActive="header__nav-link--active"
       [attr.aria-current]="isActive('/payments') ? 'page' : null">
      Paiements
    </a>
  </nav>

  <div class="header__actions">
    <app-notification-bell />
    <button mat-icon-button
            [matMenuTriggerFor]="userMenu"
            aria-label="Menu utilisateur"
            aria-haspopup="true">
      <mat-icon>account_circle</mat-icon>
    </button>
    <mat-menu #userMenu="matMenu">
      <a mat-menu-item routerLink="/settings">Parametres</a>
      <button mat-menu-item (click)="logout()">Deconnexion</button>
    </mat-menu>
  </div>
</mat-toolbar>
```

### File: `frontend/src/app/core/layout/footer/footer.component.html` (update pattern)

```html
<!-- frontend/src/app/core/layout/footer/footer.component.html -->

<footer class="footer" role="contentinfo">
  <div class="footer__content">
    <p class="footer__copyright">
      &copy; {{ currentYear }} Family Hobbies Manager.
      Tous droits reserves.
    </p>
    <nav class="footer__nav" aria-label="Liens du pied de page">
      <a routerLink="/legal/privacy" class="footer__link">
        Politique de confidentialite
      </a>
      <a routerLink="/legal/terms" class="footer__link">
        Mentions legales
      </a>
      <a routerLink="/legal/accessibility" class="footer__link">
        Accessibilite
      </a>
    </nav>
  </div>
</footer>
```

### Heading Hierarchy Standard

Every page component must follow this heading hierarchy:

```html
<!-- Page template pattern -->
<section aria-labelledby="page-title">
  <h1 id="page-title">Titre de la page</h1>

  <section aria-labelledby="section-1-title">
    <h2 id="section-1-title">Sous-section</h2>
    <!-- content -->
  </section>

  <section aria-labelledby="section-2-title">
    <h2 id="section-2-title">Autre sous-section</h2>
    <!-- content -->
  </section>
</section>
```

**Pages to update** (h1 title for each):

| Route | h1 Text | Component |
|-------|---------|-----------|
| `/auth/login` | Connexion | `LoginComponent` |
| `/auth/register` | Creer un compte | `RegisterComponent` |
| `/dashboard` | Tableau de bord | `DashboardComponent` |
| `/families` | Mes familles | `FamilyListComponent` |
| `/families/:id` | Details de la famille | `FamilyDetailComponent` |
| `/associations/search` | Rechercher une association | `AssociationSearchComponent` |
| `/associations/:id` | Details de l'association | `AssociationDetailComponent` |
| `/subscriptions` | Mes inscriptions | `SubscriptionListComponent` |
| `/attendance` | Suivi de presence | `AttendanceCalendarComponent` |
| `/payments` | Mes paiements | `PaymentListComponent` |
| `/payments/:id` | Details du paiement | `PaymentDetailComponent` |
| `/notifications` | Notifications | `NotificationListComponent` |
| `/settings` | Parametres | `SettingsComponent` |

---

## Task 4: Form Accessibility

All forms must comply with RGAA criteria 11.1 (labels), 11.2 (required), 11.10 (error messages), and 11.5 (fieldsets).

### Pattern: Accessible Text Input

```html
<!-- Accessible text input pattern — apply to all form fields -->
<mat-form-field appearance="outline">
  <mat-label for="email">Adresse e-mail</mat-label>
  <input matInput
         id="email"
         type="email"
         formControlName="email"
         [attr.aria-required]="true"
         [attr.aria-invalid]="form.get('email')?.invalid && form.get('email')?.touched"
         [attr.aria-describedby]="form.get('email')?.errors ? 'email-error' : null"
         autocomplete="email" />
  @if (form.get('email')?.hasError('required') && form.get('email')?.touched) {
    <mat-error id="email-error" role="alert">
      L'adresse e-mail est obligatoire.
    </mat-error>
  }
  @if (form.get('email')?.hasError('email') && form.get('email')?.touched) {
    <mat-error id="email-error" role="alert">
      Veuillez saisir une adresse e-mail valide.
    </mat-error>
  }
</mat-form-field>
```

### Pattern: Accessible Radio Group with Fieldset/Legend

```html
<!-- Radio group with fieldset/legend — RGAA 11.5 -->
<fieldset class="form__fieldset">
  <legend class="form__legend">Type de membre</legend>
  <mat-radio-group formControlName="memberType"
                   [attr.aria-required]="true"
                   aria-label="Type de membre">
    <mat-radio-button value="ADULT">Adulte</mat-radio-button>
    <mat-radio-button value="CHILD">Enfant</mat-radio-button>
  </mat-radio-group>
</fieldset>
```

### Pattern: Accessible Select

```html
<!-- Accessible select — RGAA 11.1 -->
<mat-form-field appearance="outline">
  <mat-label for="association">Association</mat-label>
  <mat-select id="association"
              formControlName="associationId"
              [attr.aria-required]="true"
              [attr.aria-invalid]="form.get('associationId')?.invalid && form.get('associationId')?.touched"
              [attr.aria-describedby]="form.get('associationId')?.errors ? 'association-error' : null">
    @for (assoc of associations(); track assoc.id) {
      <mat-option [value]="assoc.id">{{ assoc.name }}</mat-option>
    }
  </mat-select>
  @if (form.get('associationId')?.hasError('required') && form.get('associationId')?.touched) {
    <mat-error id="association-error" role="alert">
      Veuillez selectionner une association.
    </mat-error>
  }
</mat-form-field>
```

### File: `frontend/src/app/features/auth/login/login.component.html` (remediated)

```html
<!-- frontend/src/app/features/auth/login/login.component.html -->

<section class="login" aria-labelledby="login-title">
  <h1 id="login-title" class="login__title">Connexion</h1>

  <form [formGroup]="loginForm"
        (ngSubmit)="onSubmit()"
        class="login__form"
        aria-label="Formulaire de connexion">

    <mat-form-field appearance="outline" class="login__field">
      <mat-label>Adresse e-mail</mat-label>
      <input matInput
             id="login-email"
             type="email"
             formControlName="email"
             [attr.aria-required]="true"
             [attr.aria-invalid]="loginForm.get('email')?.invalid && loginForm.get('email')?.touched"
             [attr.aria-describedby]="loginForm.get('email')?.errors && loginForm.get('email')?.touched ? 'login-email-error' : null"
             autocomplete="email" />
      @if (loginForm.get('email')?.hasError('required') && loginForm.get('email')?.touched) {
        <mat-error id="login-email-error" role="alert">
          L'adresse e-mail est obligatoire.
        </mat-error>
      }
      @if (loginForm.get('email')?.hasError('email') && loginForm.get('email')?.touched) {
        <mat-error id="login-email-error" role="alert">
          Veuillez saisir une adresse e-mail valide.
        </mat-error>
      }
    </mat-form-field>

    <mat-form-field appearance="outline" class="login__field">
      <mat-label>Mot de passe</mat-label>
      <input matInput
             id="login-password"
             [type]="hidePassword() ? 'password' : 'text'"
             formControlName="password"
             [attr.aria-required]="true"
             [attr.aria-invalid]="loginForm.get('password')?.invalid && loginForm.get('password')?.touched"
             [attr.aria-describedby]="loginForm.get('password')?.errors && loginForm.get('password')?.touched ? 'login-password-error' : null"
             autocomplete="current-password" />
      <button mat-icon-button
              matSuffix
              type="button"
              (click)="togglePasswordVisibility()"
              [attr.aria-label]="hidePassword() ? 'Afficher le mot de passe' : 'Masquer le mot de passe'">
        <mat-icon>{{ hidePassword() ? 'visibility_off' : 'visibility' }}</mat-icon>
      </button>
      @if (loginForm.get('password')?.hasError('required') && loginForm.get('password')?.touched) {
        <mat-error id="login-password-error" role="alert">
          Le mot de passe est obligatoire.
        </mat-error>
      }
    </mat-form-field>

    @if (authError()) {
      <div class="login__error" role="alert" aria-live="assertive">
        <mat-icon aria-hidden="true">error</mat-icon>
        <span>{{ authError() }}</span>
      </div>
    }

    <button mat-raised-button
            color="primary"
            type="submit"
            class="login__submit"
            [disabled]="loginForm.invalid || loading()"
            [attr.aria-busy]="loading()">
      @if (loading()) {
        <mat-spinner diameter="20" aria-hidden="true"></mat-spinner>
        <span class="sr-only">Connexion en cours...</span>
      } @else {
        Connexion
      }
    </button>

    <p class="login__register-link">
      Pas encore de compte ?
      <a routerLink="/auth/register">Creer un compte</a>
    </p>
  </form>
</section>
```

### File: `frontend/src/app/features/auth/register/register.component.html` (remediated)

```html
<!-- frontend/src/app/features/auth/register/register.component.html -->

<section class="register" aria-labelledby="register-title">
  <h1 id="register-title" class="register__title">Creer un compte</h1>

  <form [formGroup]="registerForm"
        (ngSubmit)="onSubmit()"
        class="register__form"
        aria-label="Formulaire d'inscription">

    <mat-form-field appearance="outline" class="register__field">
      <mat-label>Prenom</mat-label>
      <input matInput
             id="register-firstname"
             type="text"
             formControlName="firstName"
             [attr.aria-required]="true"
             [attr.aria-invalid]="registerForm.get('firstName')?.invalid && registerForm.get('firstName')?.touched"
             [attr.aria-describedby]="registerForm.get('firstName')?.errors && registerForm.get('firstName')?.touched ? 'register-firstname-error' : null"
             autocomplete="given-name" />
      @if (registerForm.get('firstName')?.hasError('required') && registerForm.get('firstName')?.touched) {
        <mat-error id="register-firstname-error" role="alert">
          Le prenom est obligatoire.
        </mat-error>
      }
    </mat-form-field>

    <mat-form-field appearance="outline" class="register__field">
      <mat-label>Nom</mat-label>
      <input matInput
             id="register-lastname"
             type="text"
             formControlName="lastName"
             [attr.aria-required]="true"
             [attr.aria-invalid]="registerForm.get('lastName')?.invalid && registerForm.get('lastName')?.touched"
             [attr.aria-describedby]="registerForm.get('lastName')?.errors && registerForm.get('lastName')?.touched ? 'register-lastname-error' : null"
             autocomplete="family-name" />
      @if (registerForm.get('lastName')?.hasError('required') && registerForm.get('lastName')?.touched) {
        <mat-error id="register-lastname-error" role="alert">
          Le nom est obligatoire.
        </mat-error>
      }
    </mat-form-field>

    <mat-form-field appearance="outline" class="register__field">
      <mat-label>Adresse e-mail</mat-label>
      <input matInput
             id="register-email"
             type="email"
             formControlName="email"
             [attr.aria-required]="true"
             [attr.aria-invalid]="registerForm.get('email')?.invalid && registerForm.get('email')?.touched"
             [attr.aria-describedby]="registerForm.get('email')?.errors && registerForm.get('email')?.touched ? 'register-email-error' : null"
             autocomplete="email" />
      @if (registerForm.get('email')?.hasError('required') && registerForm.get('email')?.touched) {
        <mat-error id="register-email-error" role="alert">
          L'adresse e-mail est obligatoire.
        </mat-error>
      }
      @if (registerForm.get('email')?.hasError('email') && registerForm.get('email')?.touched) {
        <mat-error id="register-email-error" role="alert">
          Veuillez saisir une adresse e-mail valide.
        </mat-error>
      }
    </mat-form-field>

    <mat-form-field appearance="outline" class="register__field">
      <mat-label>Mot de passe</mat-label>
      <input matInput
             id="register-password"
             [type]="hidePassword() ? 'password' : 'text'"
             formControlName="password"
             [attr.aria-required]="true"
             [attr.aria-invalid]="registerForm.get('password')?.invalid && registerForm.get('password')?.touched"
             [attr.aria-describedby]="'register-password-hint' + (registerForm.get('password')?.errors && registerForm.get('password')?.touched ? ' register-password-error' : '')"
             autocomplete="new-password" />
      <mat-hint id="register-password-hint">
        Minimum 8 caracteres, une majuscule, un chiffre.
      </mat-hint>
      <button mat-icon-button
              matSuffix
              type="button"
              (click)="togglePasswordVisibility()"
              [attr.aria-label]="hidePassword() ? 'Afficher le mot de passe' : 'Masquer le mot de passe'">
        <mat-icon>{{ hidePassword() ? 'visibility_off' : 'visibility' }}</mat-icon>
      </button>
      @if (registerForm.get('password')?.hasError('required') && registerForm.get('password')?.touched) {
        <mat-error id="register-password-error" role="alert">
          Le mot de passe est obligatoire.
        </mat-error>
      }
      @if (registerForm.get('password')?.hasError('minlength') && registerForm.get('password')?.touched) {
        <mat-error id="register-password-error" role="alert">
          Le mot de passe doit contenir au moins 8 caracteres.
        </mat-error>
      }
    </mat-form-field>

    <mat-form-field appearance="outline" class="register__field">
      <mat-label>Confirmer le mot de passe</mat-label>
      <input matInput
             id="register-confirm-password"
             [type]="hidePassword() ? 'password' : 'text'"
             formControlName="confirmPassword"
             [attr.aria-required]="true"
             [attr.aria-invalid]="registerForm.get('confirmPassword')?.invalid && registerForm.get('confirmPassword')?.touched"
             [attr.aria-describedby]="registerForm.get('confirmPassword')?.errors && registerForm.get('confirmPassword')?.touched ? 'register-confirm-error' : null"
             autocomplete="new-password" />
      @if (registerForm.get('confirmPassword')?.hasError('required') && registerForm.get('confirmPassword')?.touched) {
        <mat-error id="register-confirm-error" role="alert">
          La confirmation du mot de passe est obligatoire.
        </mat-error>
      }
      @if (registerForm.get('confirmPassword')?.hasError('passwordMismatch') && registerForm.get('confirmPassword')?.touched) {
        <mat-error id="register-confirm-error" role="alert">
          Les mots de passe ne correspondent pas.
        </mat-error>
      }
    </mat-form-field>

    @if (registerError()) {
      <div class="register__error" role="alert" aria-live="assertive">
        <mat-icon aria-hidden="true">error</mat-icon>
        <span>{{ registerError() }}</span>
      </div>
    }

    <button mat-raised-button
            color="primary"
            type="submit"
            class="register__submit"
            [disabled]="registerForm.invalid || loading()"
            [attr.aria-busy]="loading()">
      @if (loading()) {
        <mat-spinner diameter="20" aria-hidden="true"></mat-spinner>
        <span class="sr-only">Inscription en cours...</span>
      } @else {
        Creer mon compte
      }
    </button>

    <p class="register__login-link">
      Deja un compte ?
      <a routerLink="/auth/login">Se connecter</a>
    </p>
  </form>
</section>
```

### Family Member Form Pattern (remediated)

```html
<!-- frontend/src/app/features/families/member-form/member-form.component.html (pattern) -->

<section class="member-form" aria-labelledby="member-form-title">
  <h2 id="member-form-title">{{ isEdit() ? 'Modifier le membre' : 'Ajouter un membre' }}</h2>

  <form [formGroup]="memberForm"
        (ngSubmit)="onSubmit()"
        aria-label="Formulaire membre de la famille">

    <mat-form-field appearance="outline">
      <mat-label>Prenom</mat-label>
      <input matInput
             id="member-firstname"
             formControlName="firstName"
             [attr.aria-required]="true"
             [attr.aria-invalid]="memberForm.get('firstName')?.invalid && memberForm.get('firstName')?.touched"
             [attr.aria-describedby]="memberForm.get('firstName')?.errors && memberForm.get('firstName')?.touched ? 'member-firstname-error' : null" />
      @if (memberForm.get('firstName')?.hasError('required') && memberForm.get('firstName')?.touched) {
        <mat-error id="member-firstname-error" role="alert">
          Le prenom est obligatoire.
        </mat-error>
      }
    </mat-form-field>

    <mat-form-field appearance="outline">
      <mat-label>Nom</mat-label>
      <input matInput
             id="member-lastname"
             formControlName="lastName"
             [attr.aria-required]="true"
             [attr.aria-invalid]="memberForm.get('lastName')?.invalid && memberForm.get('lastName')?.touched"
             [attr.aria-describedby]="memberForm.get('lastName')?.errors && memberForm.get('lastName')?.touched ? 'member-lastname-error' : null" />
      @if (memberForm.get('lastName')?.hasError('required') && memberForm.get('lastName')?.touched) {
        <mat-error id="member-lastname-error" role="alert">
          Le nom est obligatoire.
        </mat-error>
      }
    </mat-form-field>

    <mat-form-field appearance="outline">
      <mat-label>Date de naissance</mat-label>
      <input matInput
             id="member-birthdate"
             [matDatepicker]="picker"
             formControlName="birthDate"
             [attr.aria-required]="true"
             [attr.aria-describedby]="'member-birthdate-hint'" />
      <mat-datepicker-toggle matSuffix [for]="picker" aria-label="Ouvrir le calendrier"></mat-datepicker-toggle>
      <mat-datepicker #picker></mat-datepicker>
      <mat-hint id="member-birthdate-hint">Format : JJ/MM/AAAA</mat-hint>
    </mat-form-field>

    <fieldset class="form__fieldset">
      <legend class="form__legend">Type de membre</legend>
      <mat-radio-group formControlName="memberType"
                       [attr.aria-required]="true"
                       aria-label="Type de membre">
        <mat-radio-button value="ADULT">Adulte</mat-radio-button>
        <mat-radio-button value="CHILD">Enfant</mat-radio-button>
      </mat-radio-group>
    </fieldset>

    <div class="member-form__actions">
      <button mat-button type="button" (click)="cancel()">Annuler</button>
      <button mat-raised-button color="primary" type="submit"
              [disabled]="memberForm.invalid || loading()">
        {{ isEdit() ? 'Enregistrer' : 'Ajouter' }}
      </button>
    </div>
  </form>
</section>
```

### Subscription Form Pattern (remediated)

```html
<!-- frontend/src/app/features/subscriptions/subscription-form/subscription-form.component.html (pattern) -->

<section class="subscription-form" aria-labelledby="subscription-form-title">
  <h2 id="subscription-form-title">Nouvelle inscription</h2>

  <form [formGroup]="subscriptionForm"
        (ngSubmit)="onSubmit()"
        aria-label="Formulaire d'inscription a une activite">

    <mat-form-field appearance="outline">
      <mat-label>Membre</mat-label>
      <mat-select id="subscription-member"
                  formControlName="memberId"
                  [attr.aria-required]="true"
                  [attr.aria-invalid]="subscriptionForm.get('memberId')?.invalid && subscriptionForm.get('memberId')?.touched"
                  [attr.aria-describedby]="subscriptionForm.get('memberId')?.errors && subscriptionForm.get('memberId')?.touched ? 'subscription-member-error' : null">
        @for (member of familyMembers(); track member.id) {
          <mat-option [value]="member.id">{{ member.firstName }} {{ member.lastName }}</mat-option>
        }
      </mat-select>
      @if (subscriptionForm.get('memberId')?.hasError('required') && subscriptionForm.get('memberId')?.touched) {
        <mat-error id="subscription-member-error" role="alert">
          Veuillez selectionner un membre.
        </mat-error>
      }
    </mat-form-field>

    <mat-form-field appearance="outline">
      <mat-label>Association</mat-label>
      <mat-select id="subscription-association"
                  formControlName="associationId"
                  [attr.aria-required]="true"
                  [attr.aria-invalid]="subscriptionForm.get('associationId')?.invalid && subscriptionForm.get('associationId')?.touched">
        @for (assoc of associations(); track assoc.id) {
          <mat-option [value]="assoc.id">{{ assoc.name }}</mat-option>
        }
      </mat-select>
    </mat-form-field>

    <mat-form-field appearance="outline">
      <mat-label>Activite</mat-label>
      <mat-select id="subscription-activity"
                  formControlName="activityId"
                  [attr.aria-required]="true">
        @for (activity of activities(); track activity.id) {
          <mat-option [value]="activity.id">{{ activity.name }}</mat-option>
        }
      </mat-select>
    </mat-form-field>

    <fieldset class="form__fieldset">
      <legend class="form__legend">Frequence de paiement</legend>
      <mat-radio-group formControlName="paymentFrequency"
                       [attr.aria-required]="true"
                       aria-label="Frequence de paiement">
        <mat-radio-button value="ANNUAL">Annuel</mat-radio-button>
        <mat-radio-button value="MONTHLY">Mensuel</mat-radio-button>
        <mat-radio-button value="TRIMESTER">Trimestriel</mat-radio-button>
      </mat-radio-group>
    </fieldset>

    <div class="subscription-form__actions">
      <button mat-button type="button" (click)="cancel()">Annuler</button>
      <button mat-raised-button color="primary" type="submit"
              [disabled]="subscriptionForm.invalid || loading()"
              [attr.aria-busy]="loading()">
        Valider l'inscription
      </button>
    </div>
  </form>
</section>
```

---

## Task 5: Table Accessibility

All data tables must comply with RGAA criteria 5.1 (summary), 5.4 (caption), 5.6 (headers), 5.7 (scope).

### Pattern: Accessible Data Table

```html
<!-- Accessible table pattern — apply to all data tables -->
<table mat-table
       [dataSource]="dataSource"
       class="members-table"
       role="table"
       aria-label="Liste des membres de la famille">

  <caption class="sr-only">
    Liste des membres de la famille avec nom, prenom, date de naissance et type.
  </caption>

  <!-- Name column -->
  <ng-container matColumnDef="name">
    <th mat-header-cell *matHeaderCellDef scope="col"
        mat-sort-header
        [attr.aria-sort]="getSortDirection('name')">
      Nom complet
    </th>
    <td mat-cell *matCellDef="let member">
      {{ member.lastName }} {{ member.firstName }}
    </td>
  </ng-container>

  <!-- ... other columns with scope="col" ... -->

  <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
  <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
</table>
```

### Helper Method for aria-sort

```typescript
// Add to any component with sortable tables
getSortDirection(column: string): 'ascending' | 'descending' | 'none' {
  if (this.sort?.active !== column) {
    return 'none';
  }
  return this.sort.direction === 'asc' ? 'ascending' : 'descending';
}
```

### File: Members Table (remediated pattern)

```html
<!-- frontend/src/app/features/families/family-detail/family-detail.component.html (members table) -->

<section aria-labelledby="members-section-title">
  <h2 id="members-section-title">Membres de la famille</h2>

  <table mat-table [dataSource]="members()" matSort aria-label="Membres de la famille">
    <caption class="sr-only">
      Liste des membres de la famille avec leur prenom, nom, date de naissance et type.
    </caption>

    <ng-container matColumnDef="firstName">
      <th mat-header-cell *matHeaderCellDef mat-sort-header scope="col"
          [attr.aria-sort]="getSortDirection('firstName')">
        Prenom
      </th>
      <td mat-cell *matCellDef="let member">{{ member.firstName }}</td>
    </ng-container>

    <ng-container matColumnDef="lastName">
      <th mat-header-cell *matHeaderCellDef mat-sort-header scope="col"
          [attr.aria-sort]="getSortDirection('lastName')">
        Nom
      </th>
      <td mat-cell *matCellDef="let member">{{ member.lastName }}</td>
    </ng-container>

    <ng-container matColumnDef="birthDate">
      <th mat-header-cell *matHeaderCellDef mat-sort-header scope="col"
          [attr.aria-sort]="getSortDirection('birthDate')">
        Date de naissance
      </th>
      <td mat-cell *matCellDef="let member">{{ member.birthDate | date:'dd/MM/yyyy' }}</td>
    </ng-container>

    <ng-container matColumnDef="memberType">
      <th mat-header-cell *matHeaderCellDef scope="col">Type</th>
      <td mat-cell *matCellDef="let member">
        <span class="chip chip--{{ member.memberType | lowercase }}">
          {{ member.memberType === 'ADULT' ? 'Adulte' : 'Enfant' }}
        </span>
      </td>
    </ng-container>

    <ng-container matColumnDef="actions">
      <th mat-header-cell *matHeaderCellDef scope="col">
        <span class="sr-only">Actions</span>
      </th>
      <td mat-cell *matCellDef="let member">
        <button mat-icon-button
                [attr.aria-label]="'Modifier ' + member.firstName + ' ' + member.lastName"
                (click)="editMember(member)">
          <mat-icon aria-hidden="true">edit</mat-icon>
        </button>
        <button mat-icon-button
                [attr.aria-label]="'Supprimer ' + member.firstName + ' ' + member.lastName"
                (click)="deleteMember(member)">
          <mat-icon aria-hidden="true">delete</mat-icon>
        </button>
      </td>
    </ng-container>

    <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
    <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
  </table>

  @if (members().length === 0) {
    <app-empty-state
      message="Aucun membre dans cette famille."
      icon="group"
      actionLabel="Ajouter un membre"
      (action)="addMember()">
    </app-empty-state>
  }
</section>
```

### Subscriptions Table (remediated pattern)

```html
<!-- frontend/src/app/features/subscriptions/subscription-list/subscription-list.component.html (table) -->

<section aria-labelledby="subscriptions-title">
  <h1 id="subscriptions-title">Mes inscriptions</h1>

  <table mat-table [dataSource]="subscriptions()" matSort aria-label="Liste des inscriptions">
    <caption class="sr-only">
      Inscriptions aux activites avec membre, association, activite, statut et date.
    </caption>

    <ng-container matColumnDef="memberName">
      <th mat-header-cell *matHeaderCellDef mat-sort-header scope="col"
          [attr.aria-sort]="getSortDirection('memberName')">
        Membre
      </th>
      <td mat-cell *matCellDef="let sub">{{ sub.memberName }}</td>
    </ng-container>

    <ng-container matColumnDef="associationName">
      <th mat-header-cell *matHeaderCellDef mat-sort-header scope="col"
          [attr.aria-sort]="getSortDirection('associationName')">
        Association
      </th>
      <td mat-cell *matCellDef="let sub">{{ sub.associationName }}</td>
    </ng-container>

    <ng-container matColumnDef="activityName">
      <th mat-header-cell *matHeaderCellDef scope="col">Activite</th>
      <td mat-cell *matCellDef="let sub">{{ sub.activityName }}</td>
    </ng-container>

    <ng-container matColumnDef="status">
      <th mat-header-cell *matHeaderCellDef scope="col">Statut</th>
      <td mat-cell *matCellDef="let sub">
        <!-- Status chip with text label — not color-only (RGAA 3.1) -->
        <span class="chip chip--{{ sub.status | lowercase }}"
              [attr.aria-label]="'Statut : ' + getStatusLabel(sub.status)">
          {{ getStatusLabel(sub.status) }}
        </span>
      </td>
    </ng-container>

    <ng-container matColumnDef="startDate">
      <th mat-header-cell *matHeaderCellDef mat-sort-header scope="col"
          [attr.aria-sort]="getSortDirection('startDate')">
        Date de debut
      </th>
      <td mat-cell *matCellDef="let sub">{{ sub.startDate | date:'dd/MM/yyyy' }}</td>
    </ng-container>

    <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
    <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
  </table>
</section>
```

### Attendance Table (remediated pattern)

```html
<!-- frontend/src/app/features/attendance/attendance-record/attendance-record.component.html (table) -->

<section aria-labelledby="attendance-title">
  <h2 id="attendance-title">Feuille de presence</h2>

  <table mat-table [dataSource]="records()" aria-label="Feuille de presence">
    <caption class="sr-only">
      Presence des membres pour la seance selectionnee avec statut et commentaire.
    </caption>

    <ng-container matColumnDef="memberName">
      <th mat-header-cell *matHeaderCellDef scope="col">Membre</th>
      <td mat-cell *matCellDef="let record">{{ record.memberName }}</td>
    </ng-container>

    <ng-container matColumnDef="status">
      <th mat-header-cell *matHeaderCellDef scope="col">Statut</th>
      <td mat-cell *matCellDef="let record">
        <span class="chip chip--{{ record.status | lowercase }}">
          {{ getAttendanceLabel(record.status) }}
        </span>
      </td>
    </ng-container>

    <ng-container matColumnDef="comment">
      <th mat-header-cell *matHeaderCellDef scope="col">Commentaire</th>
      <td mat-cell *matCellDef="let record">{{ record.comment || '-' }}</td>
    </ng-container>

    <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
    <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
  </table>
</section>
```

### Payments Table (remediated pattern)

```html
<!-- frontend/src/app/features/payments/payment-list/payment-list.component.html (table) -->

<section aria-labelledby="payments-title">
  <h1 id="payments-title">Mes paiements</h1>

  <table mat-table [dataSource]="payments()" matSort aria-label="Liste des paiements">
    <caption class="sr-only">
      Historique des paiements avec date, montant, methode, statut et association.
    </caption>

    <ng-container matColumnDef="date">
      <th mat-header-cell *matHeaderCellDef mat-sort-header scope="col"
          [attr.aria-sort]="getSortDirection('date')">
        Date
      </th>
      <td mat-cell *matCellDef="let payment">{{ payment.createdAt | date:'dd/MM/yyyy' }}</td>
    </ng-container>

    <ng-container matColumnDef="amount">
      <th mat-header-cell *matHeaderCellDef mat-sort-header scope="col"
          [attr.aria-sort]="getSortDirection('amount')">
        Montant
      </th>
      <td mat-cell *matCellDef="let payment">{{ payment.amount | currency:'EUR':'symbol':'1.2-2':'fr-FR' }}</td>
    </ng-container>

    <ng-container matColumnDef="method">
      <th mat-header-cell *matHeaderCellDef scope="col">Methode</th>
      <td mat-cell *matCellDef="let payment">{{ getMethodLabel(payment.method) }}</td>
    </ng-container>

    <ng-container matColumnDef="status">
      <th mat-header-cell *matHeaderCellDef scope="col">Statut</th>
      <td mat-cell *matCellDef="let payment">
        <span class="chip chip--{{ payment.status | lowercase }}">
          {{ getPaymentStatusLabel(payment.status) }}
        </span>
      </td>
    </ng-container>

    <ng-container matColumnDef="actions">
      <th mat-header-cell *matHeaderCellDef scope="col">
        <span class="sr-only">Actions</span>
      </th>
      <td mat-cell *matCellDef="let payment">
        <a mat-icon-button
           [routerLink]="['/payments', payment.id]"
           [attr.aria-label]="'Voir le detail du paiement du ' + (payment.createdAt | date:'dd/MM/yyyy')">
          <mat-icon aria-hidden="true">visibility</mat-icon>
        </a>
      </td>
    </ng-container>

    <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
    <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
  </table>
</section>
```

---

## Task 6: Keyboard Navigation

### `aria-current="page"` on Active Links

Already shown in Task 3 (header). The pattern for sidebar:

```html
<!-- frontend/src/app/core/layout/sidebar/sidebar.component.html -->

<nav class="sidebar" aria-label="Menu principal">
  <ul class="sidebar__list" role="list">
    @for (item of menuItems(); track item.route) {
      <li class="sidebar__item" role="listitem">
        <a class="sidebar__link"
           [routerLink]="item.route"
           routerLinkActive="sidebar__link--active"
           [attr.aria-current]="isActive(item.route) ? 'page' : null">
          <mat-icon aria-hidden="true">{{ item.icon }}</mat-icon>
          <span>{{ item.label }}</span>
        </a>
      </li>
    }
  </ul>
</nav>
```

### Keyboard-Navigable Menu

The sidebar and dropdown menus must support arrow key navigation:

```typescript
// frontend/src/app/core/layout/sidebar/sidebar.component.ts (keyboard handler)

import { Component, signal, HostListener } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { CommonModule } from '@angular/common';

interface MenuItem {
  route: string;
  icon: string;
  label: string;
}

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive, MatIconModule],
  templateUrl: './sidebar.component.html',
  styleUrls: ['./sidebar.component.scss']
})
export class SidebarComponent {
  menuItems = signal<MenuItem[]>([
    { route: '/dashboard', icon: 'dashboard', label: 'Tableau de bord' },
    { route: '/families', icon: 'family_restroom', label: 'Familles' },
    { route: '/associations/search', icon: 'groups', label: 'Associations' },
    { route: '/subscriptions', icon: 'card_membership', label: 'Inscriptions' },
    { route: '/attendance', icon: 'event_available', label: 'Presence' },
    { route: '/payments', icon: 'payment', label: 'Paiements' },
    { route: '/notifications', icon: 'notifications', label: 'Notifications' },
    { route: '/settings', icon: 'settings', label: 'Parametres' }
  ]);

  constructor(private router: Router) {}

  isActive(route: string): boolean {
    return this.router.isActive(route, {
      paths: 'subset',
      queryParams: 'ignored',
      matrixParams: 'ignored',
      fragment: 'ignored'
    });
  }

  @HostListener('keydown', ['$event'])
  onKeydown(event: KeyboardEvent): void {
    const links = Array.from(
      (event.currentTarget as HTMLElement).querySelectorAll('.sidebar__link')
    ) as HTMLElement[];
    const currentIndex = links.indexOf(event.target as HTMLElement);

    if (currentIndex === -1) return;

    let nextIndex: number;
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        nextIndex = (currentIndex + 1) % links.length;
        links[nextIndex].focus();
        break;
      case 'ArrowUp':
        event.preventDefault();
        nextIndex = (currentIndex - 1 + links.length) % links.length;
        links[nextIndex].focus();
        break;
      case 'Home':
        event.preventDefault();
        links[0].focus();
        break;
      case 'End':
        event.preventDefault();
        links[links.length - 1].focus();
        break;
    }
  }
}
```

---

## Task 7: Color Contrast (WCAG AA Compliant Palette)

### File: `frontend/src/app/shared/styles/_variables.scss` (update)

```scss
// frontend/src/app/shared/styles/_variables.scss
// WCAG AA compliant color palette
// All text colors verified against background with >= 4.5:1 contrast ratio
// Large text (18px+ or 14px+ bold) requires >= 3:1

// ── Primary palette ──
$color-primary-50:  #e8eaf6;
$color-primary-100: #c5cae9;
$color-primary-500: #1a237e;  // Primary — contrast 12.6:1 on white
$color-primary-700: #0d1559;  // Darker variant — contrast 15.8:1 on white
$color-primary-contrast: #ffffff;

// ── Accent palette ──
$color-accent-500: #ff6f00;   // Accent — contrast 3.1:1 on white (large text only)
$color-accent-700: #e65100;   // Darker accent — contrast 4.6:1 on white
$color-accent-contrast: #ffffff;

// ── Semantic colors ──
// Success
$color-success-bg: #e8f5e9;
$color-success-text: #1b5e20;     // Contrast 8.2:1 on success-bg
$color-success-border: #2e7d32;

// Warning
$color-warning-bg: #fff3e0;
$color-warning-text: #e65100;     // Contrast 4.6:1 on warning-bg
$color-warning-border: #ef6c00;

// Error
$color-error-bg: #fce4ec;
$color-error-text: #b71c1c;       // Contrast 7.0:1 on error-bg
$color-error-border: #c62828;

// Info
$color-info-bg: #e3f2fd;
$color-info-text: #0d47a1;        // Contrast 7.5:1 on info-bg
$color-info-border: #1565c0;

// ── Neutral palette ──
$color-text-primary: #212121;      // Contrast 15.4:1 on white
$color-text-secondary: #424242;    // Contrast 11.7:1 on white
$color-text-disabled: #757575;     // Contrast 4.6:1 on white (meets AA)
$color-text-on-dark: #ffffff;

$color-bg-primary: #ffffff;
$color-bg-secondary: #fafafa;
$color-bg-surface: #ffffff;
$color-border: #bdbdbd;

// ── Status chip colors ──
// Each chip has a text label (not color-only) per RGAA 3.1
$chip-active-bg: #e8f5e9;
$chip-active-text: #1b5e20;        // 8.2:1 on bg
$chip-active-label: 'Actif';

$chip-pending-bg: #fff3e0;
$chip-pending-text: #e65100;       // 4.6:1 on bg
$chip-pending-label: 'En attente';

$chip-expired-bg: #fce4ec;
$chip-expired-text: #b71c1c;       // 7.0:1 on bg
$chip-expired-label: 'Expire';

$chip-cancelled-bg: #efebe9;
$chip-cancelled-text: #3e2723;     // 11.5:1 on bg
$chip-cancelled-label: 'Annule';

$chip-completed-bg: #e3f2fd;
$chip-completed-text: #0d47a1;     // 7.5:1 on bg
$chip-completed-label: 'Termine';

$chip-failed-bg: #fce4ec;
$chip-failed-text: #b71c1c;        // 7.0:1 on bg
$chip-failed-label: 'Echoue';

// ── Focus indicator ──
$focus-color: #ff6f00;
$focus-width: 2px;
$focus-offset: 2px;
$focus-style: solid;

// ── Typography ──
$font-family-primary: 'Roboto', 'Noto Sans', Arial, sans-serif;
$font-size-base: 1rem;     // 16px
$font-size-sm: 0.875rem;   // 14px
$font-size-lg: 1.125rem;   // 18px
$line-height-base: 1.5;
```

---

## Task 8: Image Alt Text

### Pattern: Content Images

```html
<!-- Content image — meaningful alt text -->
<img [src]="association.logoUrl"
     [alt]="association.name + ' - logo de l\'association'"
     class="association-card__logo"
     loading="lazy" />
```

### Pattern: Decorative Images

```html
<!-- Decorative image — empty alt, hidden from a11y tree -->
<img src="assets/wave-decoration.svg"
     alt=""
     aria-hidden="true"
     class="decorative-wave" />
```

### Pattern: Avatar Images

```html
<!-- User avatar — always has alt text -->
<img [src]="user.avatarUrl || 'assets/default-avatar.svg'"
     [alt]="user.firstName + ' ' + user.lastName + ' - photo de profil'"
     class="header__avatar" />
```

### Pattern: Icon-only Buttons (already shown in tables)

```html
<!-- Icon buttons always need aria-label -->
<button mat-icon-button [attr.aria-label]="'Supprimer ' + item.name">
  <mat-icon aria-hidden="true">delete</mat-icon>
</button>
```

---

## Task 9: Dialog Focus Management

### Pattern: Accessible Material Dialog

All Material dialogs must trap focus, close on ESC, and have proper ARIA attributes. Angular Material CDK handles focus trapping automatically when using `MatDialog`, but we must ensure `aria-labelledby` is set.

### File: `frontend/src/app/shared/components/confirm-dialog/confirm-dialog.component.ts`

```typescript
// frontend/src/app/shared/components/confirm-dialog/confirm-dialog.component.ts

import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef
} from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

export interface ConfirmDialogData {
  title: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  confirmColor?: 'primary' | 'accent' | 'warn';
}

@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatButtonModule, MatIconModule],
  template: `
    <h2 mat-dialog-title id="confirm-dialog-title">
      {{ data.title }}
    </h2>

    <mat-dialog-content id="confirm-dialog-description">
      <p>{{ data.message }}</p>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button
              (click)="onCancel()"
              cdkFocusInitial>
        {{ data.cancelLabel || 'Annuler' }}
      </button>
      <button mat-raised-button
              [color]="data.confirmColor || 'primary'"
              (click)="onConfirm()">
        {{ data.confirmLabel || 'Confirmer' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    :host {
      display: block;
    }
  `]
})
export class ConfirmDialogComponent {
  constructor(
    public dialogRef: MatDialogRef<ConfirmDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: ConfirmDialogData
  ) {
    // Configure dialog ARIA attributes
    this.dialogRef.addPanelClass('confirm-dialog');
  }

  onCancel(): void {
    this.dialogRef.close(false);
  }

  onConfirm(): void {
    this.dialogRef.close(true);
  }
}
```

### Opening Dialogs with Proper ARIA

```typescript
// Pattern for opening any dialog in the application
openConfirmDialog(data: ConfirmDialogData): void {
  this.dialog.open(ConfirmDialogComponent, {
    data,
    width: '400px',
    ariaLabel: data.title,
    ariaDescribedBy: 'confirm-dialog-description',
    autoFocus: 'first-tabbable',
    restoreFocus: true    // Return focus to triggering element on close
  });
}
```

### Dialog A11y Checklist (all dialogs)

| Property | Value | Purpose |
|----------|-------|---------|
| `role` | `dialog` | Angular Material sets this automatically |
| `aria-labelledby` | `{dialog}-title` | Set via `MatDialogConfig.ariaLabel` or template `mat-dialog-title` |
| `aria-describedby` | `{dialog}-description` | Set via `ariaDescribedBy` config or `mat-dialog-content` |
| `autoFocus` | `first-tabbable` | Focus first interactive element |
| `restoreFocus` | `true` | Return focus to trigger on close |
| `disableClose` | `false` (default) | Allow ESC to close |
| `cdkTrapFocus` | Automatic with `MatDialog` | Tab key cycles within dialog |

---

## Task 10: Loading States

### File: `frontend/src/app/shared/components/loading-spinner/loading-spinner.component.ts` (remediated)

```typescript
// frontend/src/app/shared/components/loading-spinner/loading-spinner.component.ts

import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

@Component({
  selector: 'app-loading-spinner',
  standalone: true,
  imports: [CommonModule, MatProgressSpinnerModule],
  template: `
    <div class="loading-spinner"
         role="status"
         aria-live="polite"
         [attr.aria-label]="message">
      <mat-spinner [diameter]="diameter" aria-hidden="true"></mat-spinner>
      <p class="loading-spinner__message">{{ message }}</p>
    </div>
  `,
  styles: [`
    .loading-spinner {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 24px;
      gap: 16px;

      &__message {
        color: var(--color-text-secondary, #424242);
        font-size: 0.875rem;
      }
    }
  `]
})
export class LoadingSpinnerComponent {
  @Input() message = 'Chargement en cours...';
  @Input() diameter = 40;
}
```

### Pattern: Content Area with aria-busy

```html
<!-- Wrap content areas that load asynchronously -->
<div class="page__content"
     [attr.aria-busy]="loading()"
     aria-live="polite">

  @if (loading()) {
    <app-loading-spinner message="Chargement des donnees..." />
  } @else {
    <!-- Actual content -->
    <ng-content />
  }
</div>
```

### File: `frontend/src/app/shared/components/empty-state/empty-state.component.ts` (remediated)

```typescript
// frontend/src/app/shared/components/empty-state/empty-state.component.ts

import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';

@Component({
  selector: 'app-empty-state',
  standalone: true,
  imports: [CommonModule, MatIconModule, MatButtonModule],
  template: `
    <div class="empty-state" role="status" aria-live="polite">
      <mat-icon class="empty-state__icon" aria-hidden="true">{{ icon }}</mat-icon>
      <p class="empty-state__message">{{ message }}</p>
      @if (actionLabel) {
        <button mat-raised-button color="primary" (click)="action.emit()">
          {{ actionLabel }}
        </button>
      }
    </div>
  `,
  styles: [`
    .empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 48px 24px;
      gap: 16px;
      text-align: center;

      &__icon {
        font-size: 48px;
        width: 48px;
        height: 48px;
        color: var(--color-text-disabled, #757575);
      }

      &__message {
        color: var(--color-text-secondary, #424242);
        font-size: 1rem;
      }
    }
  `]
})
export class EmptyStateComponent {
  @Input() message = 'Aucun resultat.';
  @Input() icon = 'inbox';
  @Input() actionLabel?: string;
  @Output() action = new EventEmitter<void>();
}
```

### Error Message Component with aria-live

```typescript
// frontend/src/app/shared/components/error-message/error-message.component.ts

import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';

@Component({
  selector: 'app-error-message',
  standalone: true,
  imports: [CommonModule, MatIconModule, MatButtonModule],
  template: `
    <div class="error-message"
         role="alert"
         aria-live="assertive">
      <mat-icon class="error-message__icon" aria-hidden="true">error_outline</mat-icon>
      <div class="error-message__content">
        <p class="error-message__title">{{ title }}</p>
        <p class="error-message__detail">{{ message }}</p>
      </div>
      @if (retryable) {
        <button mat-button color="primary" (click)="onRetry()" aria-label="Reessayer le chargement">
          Reessayer
        </button>
      }
    </div>
  `,
  styles: [`
    .error-message {
      display: flex;
      align-items: flex-start;
      gap: 12px;
      padding: 16px;
      border: 1px solid var(--color-error-border, #c62828);
      border-radius: 4px;
      background-color: var(--color-error-bg, #fce4ec);

      &__icon {
        color: var(--color-error-text, #b71c1c);
      }

      &__title {
        font-weight: 600;
        color: var(--color-error-text, #b71c1c);
        margin: 0;
      }

      &__detail {
        color: var(--color-text-primary, #212121);
        margin: 4px 0 0;
      }
    }
  `]
})
export class ErrorMessageComponent {
  @Input() title = 'Une erreur est survenue';
  @Input() message = 'Veuillez reessayer ulterieurement.';
  @Input() retryable = false;

  onRetry(): void {
    // Emit retry event — parent handles the logic
    window.dispatchEvent(new CustomEvent('app:retry'));
  }
}
```

---

## Task 11: Focus Indicator Directive

### File: `frontend/src/app/core/a11y/focus-indicator.directive.ts`

```typescript
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
```

---

## Task 12: Global Accessibility Stylesheet

### File: `frontend/src/app/shared/styles/_a11y.scss`

```scss
// frontend/src/app/shared/styles/_a11y.scss
// Global RGAA / WCAG 2.1 AA accessibility overrides
// Imported in styles.scss

@use 'variables' as v;

// ── Screen-reader only text ──
// Use class="sr-only" for visually hidden text that screen readers must read.
// RGAA criterion 10.2 — content visible to assistive technologies
.sr-only {
  position: absolute !important;
  width: 1px !important;
  height: 1px !important;
  padding: 0 !important;
  margin: -1px !important;
  overflow: hidden !important;
  clip: rect(0, 0, 0, 0) !important;
  white-space: nowrap !important;
  border: 0 !important;
}

// Allow .sr-only elements to become visible on focus (for skip links)
.sr-only--focusable {
  &:focus,
  &:active {
    position: static !important;
    width: auto !important;
    height: auto !important;
    padding: inherit !important;
    margin: inherit !important;
    overflow: visible !important;
    clip: auto !important;
    white-space: normal !important;
  }
}

// ── Focus indicators — RGAA 10.7, WCAG 2.4.7 ──
// Visible focus ring on all interactive elements when navigating with keyboard.
// Uses :focus-visible to avoid outlines on mouse click.

*:focus-visible {
  outline: v.$focus-width v.$focus-style v.$focus-color !important;
  outline-offset: v.$focus-offset !important;
}

// Programmatic focus ring class (used by FocusIndicatorDirective)
.a11y-focus--ring {
  outline: v.$focus-width v.$focus-style v.$focus-color !important;
  outline-offset: v.$focus-offset !important;
}

// Remove default outline for mouse users (replaced by :focus-visible)
*:focus:not(:focus-visible) {
  outline: none;
}

// ── Angular Material overrides for focus ──
// Ensure Material components show visible focus

.mat-mdc-button:focus-visible,
.mat-mdc-icon-button:focus-visible,
.mat-mdc-fab:focus-visible,
.mat-mdc-mini-fab:focus-visible,
.mat-mdc-raised-button:focus-visible,
.mat-mdc-outlined-button:focus-visible {
  outline: v.$focus-width v.$focus-style v.$focus-color !important;
  outline-offset: v.$focus-offset !important;
}

.mat-mdc-form-field:focus-within {
  .mdc-notched-outline__leading,
  .mdc-notched-outline__notch,
  .mdc-notched-outline__trailing {
    border-color: v.$focus-color !important;
    border-width: v.$focus-width !important;
  }
}

.mat-mdc-tab:focus-visible {
  outline: v.$focus-width v.$focus-style v.$focus-color !important;
  outline-offset: v.$focus-offset !important;
}

.mat-mdc-checkbox:focus-visible .mdc-checkbox {
  outline: v.$focus-width v.$focus-style v.$focus-color !important;
  outline-offset: v.$focus-offset !important;
}

.mat-mdc-radio-button:focus-visible .mdc-radio {
  outline: v.$focus-width v.$focus-style v.$focus-color !important;
  outline-offset: v.$focus-offset !important;
}

.mat-mdc-slide-toggle:focus-visible .mdc-switch {
  outline: v.$focus-width v.$focus-style v.$focus-color !important;
  outline-offset: v.$focus-offset !important;
}

.mat-mdc-select:focus-visible {
  outline: v.$focus-width v.$focus-style v.$focus-color !important;
  outline-offset: v.$focus-offset !important;
}

// ── Links ──
a:focus-visible {
  outline: v.$focus-width v.$focus-style v.$focus-color !important;
  outline-offset: v.$focus-offset !important;
  border-radius: 2px;
}

// ── Status chips with text labels — RGAA 3.1 (not color-only) ──
.chip {
  display: inline-flex;
  align-items: center;
  padding: 4px 12px;
  border-radius: 16px;
  font-size: 0.75rem;
  font-weight: 600;
  line-height: 1.5;

  &--active,
  &--confirmed {
    background-color: v.$chip-active-bg;
    color: v.$chip-active-text;
  }

  &--pending {
    background-color: v.$chip-pending-bg;
    color: v.$chip-pending-text;
  }

  &--expired {
    background-color: v.$chip-expired-bg;
    color: v.$chip-expired-text;
  }

  &--cancelled {
    background-color: v.$chip-cancelled-bg;
    color: v.$chip-cancelled-text;
  }

  &--completed,
  &--paid {
    background-color: v.$chip-completed-bg;
    color: v.$chip-completed-text;
  }

  &--failed,
  &--rejected {
    background-color: v.$chip-failed-bg;
    color: v.$chip-failed-text;
  }
}

// ── Reduced motion ──
// RGAA 13.8 — respect user preference for reduced motion
@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
    scroll-behavior: auto !important;
  }
}

// ── High contrast mode support ──
@media (forced-colors: active) {
  .chip {
    border: 1px solid currentColor;
  }

  *:focus-visible {
    outline: 2px solid Highlight !important;
  }
}

// ── Fieldset/Legend styling for radio groups — RGAA 11.5 ──
.form__fieldset {
  border: 1px solid v.$color-border;
  border-radius: 4px;
  padding: 16px;
  margin: 16px 0;
}

.form__legend {
  font-size: 0.875rem;
  font-weight: 600;
  color: v.$color-text-secondary;
  padding: 0 8px;
}

// ── Table caption for screen readers ──
// Captions are visually hidden but remain accessible
table caption.sr-only {
  position: absolute !important;
  width: 1px !important;
  height: 1px !important;
  padding: 0 !important;
  margin: -1px !important;
  overflow: hidden !important;
  clip: rect(0, 0, 0, 0) !important;
  white-space: nowrap !important;
  border: 0 !important;
}

// ── Minimum touch target size (WCAG 2.5.5) ──
button,
[role="button"],
a,
input[type="checkbox"],
input[type="radio"] {
  min-height: 44px;
  min-width: 44px;
}

// Exception for inline links in paragraphs
p a,
li a {
  min-height: auto;
  min-width: auto;
}
```

### Import in `frontend/src/styles.scss`

```scss
// frontend/src/styles.scss
@use 'app/shared/styles/variables';
@use 'app/shared/styles/a11y';
// ... other global styles
```

---

## Task 13: WCAG Color Palette

See Task 7 (`_variables.scss`) for the complete WCAG-compliant palette. Here is the contrast verification matrix:

| Color Pair | Foreground | Background | Ratio | Passes AA |
|-----------|-----------|-----------|-------|-----------|
| Primary on white | `#1a237e` | `#ffffff` | 12.6:1 | Yes |
| Accent dark on white | `#e65100` | `#ffffff` | 4.6:1 | Yes |
| Text primary on white | `#212121` | `#ffffff` | 15.4:1 | Yes |
| Text secondary on white | `#424242` | `#ffffff` | 11.7:1 | Yes |
| Text disabled on white | `#757575` | `#ffffff` | 4.6:1 | Yes (AA) |
| Success text on success bg | `#1b5e20` | `#e8f5e9` | 8.2:1 | Yes |
| Warning text on warning bg | `#e65100` | `#fff3e0` | 4.6:1 | Yes |
| Error text on error bg | `#b71c1c` | `#fce4ec` | 7.0:1 | Yes |
| Info text on info bg | `#0d47a1` | `#e3f2fd` | 7.5:1 | Yes |
| White on primary | `#ffffff` | `#1a237e` | 12.6:1 | Yes |
| Focus color on white | `#ff6f00` | `#ffffff` | 3.1:1 | Large text only |

---

## Failing Tests (TDD Contract)

Tests are in the companion file: [S8-002 RGAA Accessibility Tests](./S8-002-rgaa-accessibility-tests.md)

**Test summary**: 22 Jest unit tests across 5 test files:

| Test File | Count | What It Verifies |
|-----------|-------|------------------|
| `skip-to-content.component.spec.ts` | 4 | Skip link renders, hidden by default, visible on focus, moves focus to main |
| `focus-indicator.directive.spec.ts` | 3 | Directive adds/removes class on focus/blur, cleanup on destroy |
| `confirm-dialog.component.spec.ts` | 5 | Dialog title, content, cancel/confirm, ARIA attributes, focus management |
| `loading-spinner.component.spec.ts` | 3 | Spinner renders, aria-live present, custom message displayed |
| `empty-state.component.spec.ts` | 3 | Empty message, icon, action button renders |
| `error-message.component.spec.ts` | 4 | Alert role, message text, retry button, aria-live |

Run all tests:

```bash
cd frontend
npx jest --testPathPattern="(skip-to-content|focus-indicator|confirm-dialog|loading-spinner|empty-state|error-message)" --verbose
```

Expected: all 22 tests fail initially (TDD red phase), then pass after implementation.
