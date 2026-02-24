# Story S6-005: Seed Email Templates

> 3 points | Priority: P1 | Service: notification-service
> Sprint file: [Back to Sprint Index](./_index.md)

---

## Context

The EmailService (S6-002) resolves email templates by category code from the `t_email_template` table. Without seeded templates, no emails can be sent. This story creates a Liquibase data changeset that inserts five professional French email templates with Thymeleaf syntax: WELCOME, PAYMENT_SUCCESS, PAYMENT_FAILED, SUBSCRIPTION_CONFIRMED, and ATTENDANCE_REMINDER. Each template includes a consistent header/footer, responsive HTML structure, and documented variable placeholders. The templates use Thymeleaf's inline expression syntax (`[[${variable}]]`) for seamless rendering from the database. This story depends on S6-001 (the `t_email_template` table must exist before data can be seeded).

## Tasks

| # | Task | File Path | What To Create | How To Verify |
|---|------|-----------|----------------|---------------|
| 1 | Liquibase seed data changeset | `backend/notification-service/src/main/resources/db/changelog/changesets/004-seed-email-templates.yaml` | 5 INSERT statements for email templates | All templates exist in DB after migration |
| 2 | Failing tests (TDD contract) | `backend/notification-service/src/test/java/.../repository/EmailTemplateSeedTest.java` | Integration test verifying seeded data | All 5 templates resolve correctly |

---

## Task 1 Detail: Liquibase Seed Data Changeset

- **What**: Liquibase YAML changeset that inserts five email templates into `t_email_template` with French content, Thymeleaf syntax, and documented variables
- **Where**: `backend/notification-service/src/main/resources/db/changelog/changesets/004-seed-email-templates.yaml`
- **Why**: The EmailService cannot send emails without templates. Seeding via Liquibase ensures templates are always present in any environment (dev, CI, staging, production) and are version-controlled.
- **Content**:

```yaml
databaseChangeLog:
  - changeSet:
      id: 004-seed-email-templates
      author: family-hobbies-team
      comment: "Seed initial French email templates for notification categories"
      changes:

        # ── WELCOME Template ────────────────────────────────────────────────
        - insert:
            tableName: t_email_template
            columns:
              - column:
                  name: code
                  value: "WELCOME"
              - column:
                  name: subject_template
                  value: "Bienvenue sur Family Hobbies Manager !"
              - column:
                  name: body_template
                  value: >-
                    <!DOCTYPE html>
                    <html lang="fr">
                    <head>
                      <meta charset="UTF-8"/>
                      <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
                      <style>
                        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; margin: 0; padding: 0; background-color: #f5f5f5; }
                        .container { max-width: 600px; margin: 0 auto; background-color: #ffffff; }
                        .header { background-color: #1565C0; color: #ffffff; padding: 24px; text-align: center; }
                        .header h1 { margin: 0; font-size: 24px; }
                        .content { padding: 32px 24px; color: #333333; line-height: 1.6; }
                        .content h2 { color: #1565C0; margin-top: 0; }
                        .cta-button { display: inline-block; background-color: #1565C0; color: #ffffff; text-decoration: none; padding: 12px 32px; border-radius: 4px; margin: 16px 0; }
                        .footer { background-color: #f5f5f5; padding: 16px 24px; text-align: center; font-size: 12px; color: #888888; }
                        ul { padding-left: 20px; }
                        li { margin-bottom: 8px; }
                      </style>
                    </head>
                    <body>
                      <div class="container">
                        <div class="header">
                          <h1>Family Hobbies Manager</h1>
                        </div>
                        <div class="content">
                          <h2>Bienvenue [[${firstName}]] !</h2>
                          <p>Nous sommes ravis de vous accueillir sur <strong>Family Hobbies Manager</strong>,
                          votre plateforme pour decouvrir et gerer les activites de toute la famille.</p>
                          <p>Voici comment bien demarrer :</p>
                          <ul>
                            <li><strong>Explorez les associations</strong> pres de chez vous : sport, danse, musique, theatre et bien plus</li>
                            <li><strong>Creez votre famille</strong> et ajoutez les membres de votre foyer</li>
                            <li><strong>Inscrivez-vous</strong> aux activites qui vous interessent</li>
                            <li><strong>Suivez les cours</strong> et gerez la presence de chaque membre</li>
                          </ul>
                          <a href="https://familyhobbies.fr/dashboard" class="cta-button">Acceder a mon espace</a>
                          <p>Si vous avez des questions, n'hesitez pas a nous contacter.</p>
                          <p>A bientot,<br/>L'equipe Family Hobbies Manager</p>
                        </div>
                        <div class="footer">
                          <p>Cet email a ete envoye a [[${email}]]. Si vous n'etes pas a l'origine de cette inscription, veuillez ignorer ce message.</p>
                          <p>&copy; 2026 Family Hobbies Manager. Tous droits reserves.</p>
                        </div>
                      </div>
                    </body>
                    </html>
              - column:
                  name: variables
                  value: "firstName,lastName,email"
              - column:
                  name: active
                  valueBoolean: true

        # ── PAYMENT_SUCCESS Template ────────────────────────────────────────
        - insert:
            tableName: t_email_template
            columns:
              - column:
                  name: code
                  value: "PAYMENT_SUCCESS"
              - column:
                  name: subject_template
                  value: "Paiement confirme - Reference #[[${paymentId}]]"
              - column:
                  name: body_template
                  value: >-
                    <!DOCTYPE html>
                    <html lang="fr">
                    <head>
                      <meta charset="UTF-8"/>
                      <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
                      <style>
                        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; margin: 0; padding: 0; background-color: #f5f5f5; }
                        .container { max-width: 600px; margin: 0 auto; background-color: #ffffff; }
                        .header { background-color: #2E7D32; color: #ffffff; padding: 24px; text-align: center; }
                        .header h1 { margin: 0; font-size: 24px; }
                        .content { padding: 32px 24px; color: #333333; line-height: 1.6; }
                        .content h2 { color: #2E7D32; margin-top: 0; }
                        .details-table { width: 100%; border-collapse: collapse; margin: 16px 0; }
                        .details-table td { padding: 8px 12px; border-bottom: 1px solid #eeeeee; }
                        .details-table td:first-child { font-weight: bold; color: #555555; width: 40%; }
                        .success-badge { display: inline-block; background-color: #E8F5E9; color: #2E7D32; padding: 4px 12px; border-radius: 12px; font-weight: bold; font-size: 14px; }
                        .footer { background-color: #f5f5f5; padding: 16px 24px; text-align: center; font-size: 12px; color: #888888; }
                      </style>
                    </head>
                    <body>
                      <div class="container">
                        <div class="header">
                          <h1>Paiement confirme</h1>
                        </div>
                        <div class="content">
                          <h2><span class="success-badge">Confirme</span></h2>
                          <p>Votre paiement a ete traite avec succes. Voici le recapitulatif :</p>
                          <table class="details-table">
                            <tr><td>Reference</td><td>#[[${paymentId}]]</td></tr>
                            <tr><td>Montant</td><td>[[${amount}]] [[${currency}]]</td></tr>
                            <tr><td>Mode de paiement</td><td>[[${paymentMethod}]]</td></tr>
                            <tr><td>Date</td><td>[[${paidAt}]]</td></tr>
                            <tr><td>Inscription</td><td>#[[${subscriptionId}]]</td></tr>
                          </table>
                          <p>Vous pouvez consulter le detail de ce paiement dans votre espace personnel.</p>
                          <p>Merci pour votre confiance,<br/>L'equipe Family Hobbies Manager</p>
                        </div>
                        <div class="footer">
                          <p>Ce recu de paiement a ete genere automatiquement. Conservez-le pour vos dossiers.</p>
                          <p>&copy; 2026 Family Hobbies Manager. Tous droits reserves.</p>
                        </div>
                      </div>
                    </body>
                    </html>
              - column:
                  name: variables
                  value: "paymentId,subscriptionId,amount,currency,paymentMethod,paidAt"
              - column:
                  name: active
                  valueBoolean: true

        # ── PAYMENT_FAILED Template ─────────────────────────────────────────
        - insert:
            tableName: t_email_template
            columns:
              - column:
                  name: code
                  value: "PAYMENT_FAILED"
              - column:
                  name: subject_template
                  value: "Echec de paiement - Reference #[[${paymentId}]]"
              - column:
                  name: body_template
                  value: >-
                    <!DOCTYPE html>
                    <html lang="fr">
                    <head>
                      <meta charset="UTF-8"/>
                      <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
                      <style>
                        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; margin: 0; padding: 0; background-color: #f5f5f5; }
                        .container { max-width: 600px; margin: 0 auto; background-color: #ffffff; }
                        .header { background-color: #C62828; color: #ffffff; padding: 24px; text-align: center; }
                        .header h1 { margin: 0; font-size: 24px; }
                        .content { padding: 32px 24px; color: #333333; line-height: 1.6; }
                        .content h2 { color: #C62828; margin-top: 0; }
                        .details-table { width: 100%; border-collapse: collapse; margin: 16px 0; }
                        .details-table td { padding: 8px 12px; border-bottom: 1px solid #eeeeee; }
                        .details-table td:first-child { font-weight: bold; color: #555555; width: 40%; }
                        .error-badge { display: inline-block; background-color: #FFEBEE; color: #C62828; padding: 4px 12px; border-radius: 12px; font-weight: bold; font-size: 14px; }
                        .cta-button { display: inline-block; background-color: #1565C0; color: #ffffff; text-decoration: none; padding: 12px 32px; border-radius: 4px; margin: 16px 0; }
                        .footer { background-color: #f5f5f5; padding: 16px 24px; text-align: center; font-size: 12px; color: #888888; }
                      </style>
                    </head>
                    <body>
                      <div class="container">
                        <div class="header">
                          <h1>Echec de paiement</h1>
                        </div>
                        <div class="content">
                          <h2><span class="error-badge">Echec</span></h2>
                          <p>Nous n'avons pas pu traiter votre paiement. Voici les details :</p>
                          <table class="details-table">
                            <tr><td>Reference</td><td>#[[${paymentId}]]</td></tr>
                            <tr><td>Montant</td><td>[[${amount}]] EUR</td></tr>
                            <tr><td>Raison</td><td>[[${failureReason}]]</td></tr>
                            <tr><td>Date</td><td>[[${failedAt}]]</td></tr>
                          </table>
                          <p><strong>Que faire ?</strong></p>
                          <ul>
                            <li>Verifiez les informations de votre moyen de paiement</li>
                            <li>Assurez-vous que votre compte dispose de fonds suffisants</li>
                            <li>Reessayez le paiement depuis votre espace personnel</li>
                          </ul>
                          <a href="https://familyhobbies.fr/payments" class="cta-button">Reessayer le paiement</a>
                          <p>Si le probleme persiste, contactez notre support.</p>
                          <p>Cordialement,<br/>L'equipe Family Hobbies Manager</p>
                        </div>
                        <div class="footer">
                          <p>Cet email a ete genere automatiquement suite a un echec de paiement.</p>
                          <p>&copy; 2026 Family Hobbies Manager. Tous droits reserves.</p>
                        </div>
                      </div>
                    </body>
                    </html>
              - column:
                  name: variables
                  value: "paymentId,subscriptionId,amount,failureReason,failedAt"
              - column:
                  name: active
                  valueBoolean: true

        # ── SUBSCRIPTION_CONFIRMED Template ─────────────────────────────────
        - insert:
            tableName: t_email_template
            columns:
              - column:
                  name: code
                  value: "SUBSCRIPTION_CONFIRMED"
              - column:
                  name: subject_template
                  value: "Inscription confirmee - Saison [[${season}]]"
              - column:
                  name: body_template
                  value: >-
                    <!DOCTYPE html>
                    <html lang="fr">
                    <head>
                      <meta charset="UTF-8"/>
                      <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
                      <style>
                        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; margin: 0; padding: 0; background-color: #f5f5f5; }
                        .container { max-width: 600px; margin: 0 auto; background-color: #ffffff; }
                        .header { background-color: #1565C0; color: #ffffff; padding: 24px; text-align: center; }
                        .header h1 { margin: 0; font-size: 24px; }
                        .content { padding: 32px 24px; color: #333333; line-height: 1.6; }
                        .content h2 { color: #1565C0; margin-top: 0; }
                        .details-table { width: 100%; border-collapse: collapse; margin: 16px 0; }
                        .details-table td { padding: 8px 12px; border-bottom: 1px solid #eeeeee; }
                        .details-table td:first-child { font-weight: bold; color: #555555; width: 40%; }
                        .info-box { background-color: #E3F2FD; border-left: 4px solid #1565C0; padding: 12px 16px; margin: 16px 0; border-radius: 0 4px 4px 0; }
                        .cta-button { display: inline-block; background-color: #1565C0; color: #ffffff; text-decoration: none; padding: 12px 32px; border-radius: 4px; margin: 16px 0; }
                        .footer { background-color: #f5f5f5; padding: 16px 24px; text-align: center; font-size: 12px; color: #888888; }
                      </style>
                    </head>
                    <body>
                      <div class="container">
                        <div class="header">
                          <h1>Inscription confirmee</h1>
                        </div>
                        <div class="content">
                          <h2>Votre inscription est confirmee !</h2>
                          <p>Felicitations ! Votre inscription a bien ete enregistree pour la saison [[${season}]].</p>
                          <table class="details-table">
                            <tr><td>Numero d'inscription</td><td>#[[${subscriptionId}]]</td></tr>
                            <tr><td>Activite</td><td>#[[${activityId}]]</td></tr>
                            <tr><td>Membre</td><td>#[[${familyMemberId}]]</td></tr>
                            <tr><td>Saison</td><td>[[${season}]]</td></tr>
                            <tr><td>Montant</td><td>[[${amount}]] EUR</td></tr>
                          </table>
                          <div class="info-box">
                            <strong>Prochaines etapes :</strong>
                            <ul style="margin: 8px 0 0 0; padding-left: 20px;">
                              <li>Consultez le planning des cours dans votre espace</li>
                              <li>Preparez le materiel necessaire pour la premiere seance</li>
                              <li>N'hesitez pas a contacter l'association pour toute question</li>
                            </ul>
                          </div>
                          <a href="https://familyhobbies.fr/subscriptions" class="cta-button">Voir mes inscriptions</a>
                          <p>Bonne saison !<br/>L'equipe Family Hobbies Manager</p>
                        </div>
                        <div class="footer">
                          <p>Cet email confirme votre inscription. Conservez-le pour vos dossiers.</p>
                          <p>&copy; 2026 Family Hobbies Manager. Tous droits reserves.</p>
                        </div>
                      </div>
                    </body>
                    </html>
              - column:
                  name: variables
                  value: "subscriptionId,familyMemberId,activityId,season,amount"
              - column:
                  name: active
                  valueBoolean: true

        # ── ATTENDANCE_REMINDER Template ────────────────────────────────────
        - insert:
            tableName: t_email_template
            columns:
              - column:
                  name: code
                  value: "ATTENDANCE_REMINDER"
              - column:
                  name: subject_template
                  value: "Rappel - Cours demain : [[${activityName}]]"
              - column:
                  name: body_template
                  value: >-
                    <!DOCTYPE html>
                    <html lang="fr">
                    <head>
                      <meta charset="UTF-8"/>
                      <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
                      <style>
                        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; margin: 0; padding: 0; background-color: #f5f5f5; }
                        .container { max-width: 600px; margin: 0 auto; background-color: #ffffff; }
                        .header { background-color: #F57C00; color: #ffffff; padding: 24px; text-align: center; }
                        .header h1 { margin: 0; font-size: 24px; }
                        .content { padding: 32px 24px; color: #333333; line-height: 1.6; }
                        .content h2 { color: #F57C00; margin-top: 0; }
                        .details-table { width: 100%; border-collapse: collapse; margin: 16px 0; }
                        .details-table td { padding: 8px 12px; border-bottom: 1px solid #eeeeee; }
                        .details-table td:first-child { font-weight: bold; color: #555555; width: 40%; }
                        .reminder-box { background-color: #FFF3E0; border-left: 4px solid #F57C00; padding: 12px 16px; margin: 16px 0; border-radius: 0 4px 4px 0; }
                        .footer { background-color: #f5f5f5; padding: 16px 24px; text-align: center; font-size: 12px; color: #888888; }
                      </style>
                    </head>
                    <body>
                      <div class="container">
                        <div class="header">
                          <h1>Rappel de cours</h1>
                        </div>
                        <div class="content">
                          <h2>N'oubliez pas votre cours demain !</h2>
                          <p>Ceci est un rappel pour le cours de demain.</p>
                          <table class="details-table">
                            <tr><td>Activite</td><td>[[${activityName}]]</td></tr>
                            <tr><td>Membre</td><td>[[${memberName}]]</td></tr>
                            <tr><td>Horaire</td><td>[[${sessionTime}]]</td></tr>
                            <tr><td>Lieu</td><td>[[${location}]]</td></tr>
                          </table>
                          <div class="reminder-box">
                            <strong>Pensez a :</strong>
                            <ul style="margin: 8px 0 0 0; padding-left: 20px;">
                              <li>Apporter votre equipement</li>
                              <li>Arriver 10 minutes en avance</li>
                              <li>Prevenir l'association en cas d'absence</li>
                            </ul>
                          </div>
                          <p>Bonne seance !<br/>L'equipe Family Hobbies Manager</p>
                        </div>
                        <div class="footer">
                          <p>Vous recevez ce rappel car les notifications de rappel sont activees dans vos preferences.</p>
                          <p>&copy; 2026 Family Hobbies Manager. Tous droits reserves.</p>
                        </div>
                      </div>
                    </body>
                    </html>
              - column:
                  name: variables
                  value: "activityName,memberName,sessionTime,location"
              - column:
                  name: active
                  valueBoolean: true

      rollback:
        - delete:
            tableName: t_email_template
            where: code IN ('WELCOME', 'PAYMENT_SUCCESS', 'PAYMENT_FAILED', 'SUBSCRIPTION_CONFIRMED', 'ATTENDANCE_REMINDER')
```

- **Verify**: Start notification-service -> Liquibase applies changeset 004 -> `SELECT code, subject_template FROM t_email_template` returns 5 rows

---

## Template Variable Reference

| Template Code | Variables | Description |
|---------------|-----------|-------------|
| `WELCOME` | `firstName`, `lastName`, `email` | User's name and email for personalized greeting |
| `PAYMENT_SUCCESS` | `paymentId`, `subscriptionId`, `amount`, `currency`, `paymentMethod`, `paidAt` | Payment receipt details |
| `PAYMENT_FAILED` | `paymentId`, `subscriptionId`, `amount`, `failureReason`, `failedAt` | Failure details with retry instructions |
| `SUBSCRIPTION_CONFIRMED` | `subscriptionId`, `familyMemberId`, `activityId`, `season`, `amount` | Subscription confirmation with next steps |
| `ATTENDANCE_REMINDER` | `activityName`, `memberName`, `sessionTime`, `location` | Course reminder with logistics |

---

## Template Design Decisions

1. **Consistent header/footer**: Every template has the same structure (header with service name + color, content area, footer with copyright). The header color changes per category: blue (general), green (success), red (error), orange (reminder).

2. **Responsive design**: Templates use a 600px max-width container with percentage-based padding, compatible with major email clients (Gmail, Outlook, Apple Mail).

3. **Thymeleaf inline syntax**: Uses `[[${variable}]]` instead of `th:text` because templates are processed as strings from the database, not as file-based Thymeleaf templates.

4. **French language**: All content is in French per project requirements. Accent characters are rendered correctly via UTF-8 encoding.

5. **Rollback support**: The changeset includes a rollback block that deletes all seeded templates, enabling clean rollback if needed.

---

## Failing Tests (TDD Contract)

### Test 1: EmailTemplateSeedTest

**Where**: `backend/notification-service/src/test/java/com/familyhobbies/notificationservice/repository/EmailTemplateSeedTest.java`

```java
package com.familyhobbies.notificationservice.repository;

import com.familyhobbies.notificationservice.entity.EmailTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying that Liquibase changeset 004 seeds all expected
 * email templates into the database.
 *
 * <p>Requires the full Spring context to run Liquibase migrations.
 * Uses the "test" profile with an embedded H2 or Testcontainers PostgreSQL.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Email Template Seed Data")
class EmailTemplateSeedTest {

    @Autowired
    private EmailTemplateRepository emailTemplateRepository;

    @Test
    @DisplayName("should_have_5_active_templates_when_seed_changeset_applied")
    void should_have_5_active_templates_when_seed_changeset_applied() {
        // When
        long activeCount = emailTemplateRepository.findAll().stream()
                .filter(t -> Boolean.TRUE.equals(t.getActive()))
                .count();

        // Then
        assertThat(activeCount).isEqualTo(5);
    }

    @Test
    @DisplayName("should_have_WELCOME_template_when_seed_applied")
    void should_have_WELCOME_template_when_seed_applied() {
        // When
        Optional<EmailTemplate> template =
                emailTemplateRepository.findByCodeAndActiveTrue("WELCOME");

        // Then
        assertThat(template).isPresent();
        assertThat(template.get().getSubjectTemplate())
                .contains("Bienvenue");
        assertThat(template.get().getBodyTemplate())
                .contains("firstName");
        assertThat(template.get().getVariables())
                .contains("firstName", "lastName", "email");
    }

    @Test
    @DisplayName("should_have_PAYMENT_SUCCESS_template_when_seed_applied")
    void should_have_PAYMENT_SUCCESS_template_when_seed_applied() {
        // When
        Optional<EmailTemplate> template =
                emailTemplateRepository.findByCodeAndActiveTrue("PAYMENT_SUCCESS");

        // Then
        assertThat(template).isPresent();
        assertThat(template.get().getSubjectTemplate())
                .contains("Paiement confirme");
        assertThat(template.get().getBodyTemplate())
                .contains("amount")
                .contains("paymentMethod")
                .contains("currency");
        assertThat(template.get().getVariables())
                .contains("paymentId", "amount", "currency");
    }

    @Test
    @DisplayName("should_have_PAYMENT_FAILED_template_when_seed_applied")
    void should_have_PAYMENT_FAILED_template_when_seed_applied() {
        // When
        Optional<EmailTemplate> template =
                emailTemplateRepository.findByCodeAndActiveTrue("PAYMENT_FAILED");

        // Then
        assertThat(template).isPresent();
        assertThat(template.get().getSubjectTemplate())
                .contains("Echec de paiement");
        assertThat(template.get().getBodyTemplate())
                .contains("failureReason")
                .contains("Reessayer");
        assertThat(template.get().getVariables())
                .contains("paymentId", "amount", "failureReason");
    }

    @Test
    @DisplayName("should_have_SUBSCRIPTION_CONFIRMED_template_when_seed_applied")
    void should_have_SUBSCRIPTION_CONFIRMED_template_when_seed_applied() {
        // When
        Optional<EmailTemplate> template =
                emailTemplateRepository.findByCodeAndActiveTrue("SUBSCRIPTION_CONFIRMED");

        // Then
        assertThat(template).isPresent();
        assertThat(template.get().getSubjectTemplate())
                .contains("Inscription confirmee");
        assertThat(template.get().getBodyTemplate())
                .contains("season")
                .contains("activityId")
                .contains("familyMemberId");
        assertThat(template.get().getVariables())
                .contains("subscriptionId", "season", "amount");
    }

    @Test
    @DisplayName("should_have_ATTENDANCE_REMINDER_template_when_seed_applied")
    void should_have_ATTENDANCE_REMINDER_template_when_seed_applied() {
        // When
        Optional<EmailTemplate> template =
                emailTemplateRepository.findByCodeAndActiveTrue("ATTENDANCE_REMINDER");

        // Then
        assertThat(template).isPresent();
        assertThat(template.get().getSubjectTemplate())
                .contains("Rappel");
        assertThat(template.get().getBodyTemplate())
                .contains("activityName")
                .contains("memberName")
                .contains("sessionTime")
                .contains("location");
        assertThat(template.get().getVariables())
                .contains("activityName", "memberName", "sessionTime", "location");
    }

    @Test
    @DisplayName("should_have_french_content_in_all_templates_when_seed_applied")
    void should_have_french_content_in_all_templates_when_seed_applied() {
        // When
        var templates = emailTemplateRepository.findAll();

        // Then -- verify all templates contain French text
        for (EmailTemplate template : templates) {
            assertThat(template.getBodyTemplate())
                    .as("Template %s should contain French content", template.getCode())
                    .containsAnyOf("Bonjour", "Merci", "equipe", "Cordialement",
                            "Felicitations", "Rappel", "Bienvenue");
        }
    }

    @Test
    @DisplayName("should_have_valid_html_structure_in_all_templates_when_seed_applied")
    void should_have_valid_html_structure_in_all_templates_when_seed_applied() {
        // When
        var templates = emailTemplateRepository.findAll();

        // Then -- verify basic HTML structure
        for (EmailTemplate template : templates) {
            assertThat(template.getBodyTemplate())
                    .as("Template %s should have HTML structure", template.getCode())
                    .contains("<!DOCTYPE html>")
                    .contains("<html")
                    .contains("</html>")
                    .contains("<body")
                    .contains("</body>");
        }
    }

    @Test
    @DisplayName("should_have_consistent_footer_in_all_templates_when_seed_applied")
    void should_have_consistent_footer_in_all_templates_when_seed_applied() {
        // When
        var templates = emailTemplateRepository.findAll();

        // Then -- verify footer consistency
        for (EmailTemplate template : templates) {
            assertThat(template.getBodyTemplate())
                    .as("Template %s should have copyright footer", template.getCode())
                    .contains("Family Hobbies Manager")
                    .contains("2026");
        }
    }
}
```

### Test 2: EmailTemplateRenderingTest

**Where**: `backend/notification-service/src/test/java/com/familyhobbies/notificationservice/service/EmailTemplateRenderingTest.java`

```java
package com.familyhobbies.notificationservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests verifying that Thymeleaf correctly renders the email templates
 * with sample variables. Does NOT require database or Spring context.
 */
@DisplayName("Email Template Rendering")
class EmailTemplateRenderingTest {

    private SpringTemplateEngine templateEngine;

    @BeforeEach
    void setUp() {
        templateEngine = new SpringTemplateEngine();
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCacheable(false);
        templateEngine.setTemplateResolver(resolver);
    }

    @Test
    @DisplayName("should_render_welcome_template_with_firstName_when_variable_provided")
    void should_render_welcome_template_with_firstName_when_variable_provided() {
        // Given
        String template = "<html><body>Bienvenue [[${firstName}]] !</body></html>";
        Context context = new Context();
        context.setVariables(Map.of("firstName", "Marie"));

        // When
        String result = templateEngine.process(template, context);

        // Then
        assertThat(result).contains("Bienvenue Marie !");
    }

    @Test
    @DisplayName("should_render_payment_success_template_with_amount_when_variables_provided")
    void should_render_payment_success_template_with_amount_when_variables_provided() {
        // Given
        String template = "<html><body>Montant : [[${amount}]] [[${currency}]]</body></html>";
        Context context = new Context();
        context.setVariables(Map.of("amount", "75.50", "currency", "EUR"));

        // When
        String result = templateEngine.process(template, context);

        // Then
        assertThat(result).contains("Montant : 75.50 EUR");
    }

    @Test
    @DisplayName("should_render_payment_failed_template_with_reason_when_variable_provided")
    void should_render_payment_failed_template_with_reason_when_variable_provided() {
        // Given
        String template = "<html><body>Raison : [[${failureReason}]]</body></html>";
        Context context = new Context();
        context.setVariables(Map.of("failureReason", "Carte refusee"));

        // When
        String result = templateEngine.process(template, context);

        // Then
        assertThat(result).contains("Raison : Carte refusee");
    }

    @Test
    @DisplayName("should_render_subscription_template_with_season_when_variable_provided")
    void should_render_subscription_template_with_season_when_variable_provided() {
        // Given
        String template = "<html><body>Saison [[${season}]]</body></html>";
        Context context = new Context();
        context.setVariables(Map.of("season", "2025-2026"));

        // When
        String result = templateEngine.process(template, context);

        // Then
        assertThat(result).contains("Saison 2025-2026");
    }

    @Test
    @DisplayName("should_render_attendance_template_with_all_fields_when_variables_provided")
    void should_render_attendance_template_with_all_fields_when_variables_provided() {
        // Given
        String template = "<html><body>Cours : [[${activityName}]] a [[${location}]] "
                + "le [[${sessionTime}]] pour [[${memberName}]]</body></html>";
        Context context = new Context();
        context.setVariables(Map.of(
                "activityName", "Judo",
                "location", "Gymnase Jean Moulin, Lyon",
                "sessionTime", "Mercredi 14h00 - 15h30",
                "memberName", "Lucas Dupont"
        ));

        // When
        String result = templateEngine.process(template, context);

        // Then
        assertThat(result).contains("Cours : Judo");
        assertThat(result).contains("Gymnase Jean Moulin, Lyon");
        assertThat(result).contains("Mercredi 14h00 - 15h30");
        assertThat(result).contains("Lucas Dupont");
    }

    @Test
    @DisplayName("should_render_subject_template_with_inline_expression_when_variable_provided")
    void should_render_subject_template_with_inline_expression_when_variable_provided() {
        // Given -- subject templates also use Thymeleaf expressions
        String subjectTemplate = "Paiement confirme - Reference #[[${paymentId}]]";
        Context context = new Context();
        context.setVariables(Map.of("paymentId", "42"));

        // When
        String result = templateEngine.process(subjectTemplate, context);

        // Then
        assertThat(result).contains("Reference #42");
    }
}
```

---

## Acceptance Criteria Checklist

- [ ] Liquibase changeset 004 applies without error after changesets 001-003
- [ ] All 5 templates seeded in `t_email_template` with `active = true`
- [ ] WELCOME template: French greeting, firstName variable, getting started guide
- [ ] PAYMENT_SUCCESS template: amount, currency, payment method, reference, receipt format
- [ ] PAYMENT_FAILED template: failure reason, retry instructions, CTA button
- [ ] SUBSCRIPTION_CONFIRMED template: season, activity, member, amount, next steps
- [ ] ATTENDANCE_REMINDER template: activity name, session time, location, member name
- [ ] All templates have consistent header/footer with Family Hobbies Manager branding
- [ ] All templates use Thymeleaf inline expression syntax `[[${variable}]]`
- [ ] All templates use responsive HTML with 600px max-width
- [ ] Template variables documented in the `variables` column
- [ ] Rollback block removes all 5 templates cleanly
- [ ] All 15 JUnit 5 tests pass green (9 seed verification + 6 rendering)
