# Réinitialisation du mot de passe et e-mails

Le parcours « mot de passe oublié » par lien à usage unique, et la chaîne d'envoi des e-mails HTML par SMTP.

## Fichiers clés

- `password_reset/PasswordResetController.java` : `POST /api/v1/password/forgot` et `/reset`.
- `password_reset/PasswordResetService.java` : génération, vérification et consommation du jeton.
- `password_reset/PasswordResetToken.java` : entité avec `isUsableAt(now)` et `consumeAt(now)`.
- `password_reset/EmailSender.java`, `PasswordResetEmailSender.java` : les deux e-mails du parcours.
- `email/EmailClient.java`, `SmtpEmailClient.java`, `EmailTemplateRenderer.java`, `EmailMessage.java` : l'envoi.
- `src/main/resources/templates/email/` : gabarits Thymeleaf (`password-reset.html`, `password-changed.html`,
  `fragments/layout.html`).

## Demander un lien

`POST /api/v1/password/forgot`, corps `{"email": "..."}` (public, transactionnel) :

1. l'utilisateur est cherché par e-mail sans tenir compte de la casse ;
2. s'il existe : ses jetons non utilisés sont supprimés (`deleteByUserIdAndUsedAtIsNull`), un jeton de 32 octets
   aléatoires (`SecureRandom`, base64url) est créé, et seul son SHA-256 en hexadécimal est enregistré, avec une
   expiration à `identity.password-reset.token-ttl-minutes` (30 min par défaut) ;
3. l'e-mail part avec le lien
   `<identity.auth-frontend-url>/forgot-password?email=<e-mail>&token=<jeton>` ;
4. dans tous les cas, la réponse est la même :

```json
{"message": "Si cette adresse correspond à un compte, un lien de réinitialisation a été envoyé."}
```

Ce message générique évite de révéler si une adresse a un compte.

## Choisir le nouveau mot de passe

`POST /api/v1/password/reset`, corps `{"email", "token", "password"}` (public, transactionnel). Toute erreur renvoie
le même `400` « Lien de réinitialisation invalide ou expiré » :

- mot de passe absent ou de moins de 8 caractères ;
- e-mail ou jeton absent ;
- jeton inconnu, déjà utilisé ou expiré (`isUsableAt(now)`) ;
- compte désactivé, ou e-mail différent de celui du compte du jeton.

Sinon : `user.changePassword(<BCrypt>)`, `token.consumeAt(now)`, e-mail de confirmation, et réponse
`{"message": "Votre mot de passe a été modifié. Vous pouvez maintenant vous connecter."}`. Les sessions et refresh
tokens existants ne sont pas révoqués.

## Envoi des e-mails

```text
PasswordResetService ─► EmailSender (interface)
                         └─ PasswordResetEmailSender ─► EmailTemplateRenderer (Thymeleaf) ─► HTML
                                                     └─► EmailClient (interface) ─► SmtpEmailClient ─► JavaMailSender
```

| E-mail                 | Sujet                                       | Gabarit                       | Variables  |
| ---------------------- | ------------------------------------------- | ----------------------------- | ---------- |
| Lien de réinitialisation | « Réinitialisation de votre mot de passe » | `email/password-reset`        | `resetUrl` |
| Confirmation           | « Votre mot de passe a été modifié »        | `email/password-changed`      | aucune     |

- Les gabarits remplacent le fragment `email/fragments/layout` (titre et contenu). Le texte « Ce lien expire dans 30
  minutes » est écrit en dur dans `password-reset.html`, quelle que soit la durée configurée.
- `SmtpEmailClient` envoie un message MIME UTF-8 en HTML, depuis `spring.mail.from`. Une `MessagingException` à la
  composition devient `IllegalStateException` ; une erreur d'envoi SMTP remonte telle quelle.
- L'envoi a lieu dans la transaction du service : un échec SMTP sort en `500` et annule l'écriture du jeton (voir
  [../tech-debt.md](../tech-debt.md)).

## Configuration SMTP

Dans `config/config.example.yml`, sous `spring.mail` :

| Variable d'environnement | Propriété                                | Défaut                                   |
| ------------------------ | ---------------------------------------- | ---------------------------------------- |
| `SMTP_HOST`, `SMTP_PORT` | `host`, `port`                           | `localhost`, `587`                       |
| `SMTP_USERNAME`, `SMTP_PASSWORD` | `username`, `password`           | vides                                    |
| `SMTP_AUTH`              | `properties.mail.smtp.auth`              | `true`                                   |
| `SMTP_STARTTLS_ENABLE`   | `properties.mail.smtp.starttls.enable`   | `true`                                   |
| `SMTP_SSL_ENABLE`        | `properties.mail.smtp.ssl.enable`        | `false`                                  |
| `SMTP_FROM`              | `from`                                   | `Identity Server <no-reply@example.com>` |
| `IDENTITY_PASSWORD_RESET_TTL_MINUTES` | `identity.password-reset.token-ttl-minutes` | `30`                       |

Le portail gère les deux étapes sur la même page `/forgot-password` ([frontend.md](frontend.md)).
