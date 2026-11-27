# API

La liste de tous les endpoints exposés, avec leur accès, leurs entrées et leur réponse. À tenir à jour à chaque
endpoint ajouté ou modifié.

## Fichiers clés

- `oauth2/TokenController.java`, `registration/RegistrationController.java`,
  `password_reset/PasswordResetController.java` : les routes.
- `security/SecurityConfig.java` : l'accès de chaque route et `POST /login`.
- `registration/dto/`, `password_reset/dto/` : les corps JSON.

**Accès** : _public_ = aucune authentification ; _session_ = cookie de session du serveur (ou bearer JWT émis par le
serveur, voir [security.md](security.md)) ; _secret_ = en-tête `X-Identity-Provisioning-Secret`. Les paramètres
OAuth2 sont en `snake_case`, les corps JSON en `camelCase`.

## OAuth2 / OIDC

Détail du protocole : [oauth2.md](oauth2.md).

| Méthode | Chemin                              | Accès   | Entrée                                                                                   | Réponse                                   |
| ------- | ----------------------------------- | ------- | ---------------------------------------------------------------------------------------- | ----------------------------------------- |
| `GET`   | `/.well-known/openid-configuration` | public  | —                                                                                        | document de discovery                     |
| `GET`   | `/oauth2/jwks`                      | public  | —                                                                                        | `{"keys": [ ...clé RSA... ]}`             |
| `GET`   | `/oauth2/authorize`                 | session | `response_type`, `client_id`, `redirect_uri`, `scope`, `state`, `nonce`, `code_challenge`, `code_challenge_method`, `resource` | `302` vers `redirect_uri?code=&state=` |
| `POST`  | `/oauth2/token`                     | public  | formulaire : `grant_type`, `client_id`, `code`, `redirect_uri`, `code_verifier`, `refresh_token` | jetons (JSON)                     |
| `POST`  | `/oauth2/revoke`                    | public  | formulaire : `token` (refresh token)                                                     | `200`, corps vide                         |
| `GET`   | `/userinfo`                         | session | —                                                                                        | `sub`, `email`, `email_verified`, noms    |
| `GET`   | `/connect/logout`                   | session | `client_id`, `post_logout_redirect_uri`, `state` (facultatifs)                           | `302` vers l'URI (ou `/`)                 |

Erreurs : `400` (`OAuthException` : `invalid_request`, `invalid_target`, `invalid_grant`, `unsupported_grant_type`),
paramètre obligatoire manquant (`400` de Spring MVC).

## Connexion (Spring Security)

| Méthode | Chemin    | Accès  | Entrée                                                    | Réponse                                                                 |
| ------- | --------- | ------ | --------------------------------------------------------- | ----------------------------------------------------------------------- |
| `POST`  | `/login`  | public | formulaire : `username` (e-mail), `password`, `invitation` (facultatif) | `302` : requête d'origine, `<portail>/invitation/complete?invitation=` ou `<portail>/login?error=authentication_failed` |
| `POST`  | `/logout` | public | jeton CSRF                                                | `302` vers `/`                                                          |

## Inscription et invitations

Détail : [registration.md](registration.md).

| Méthode | Chemin                               | Accès   | Entrée                                                             | Réponse              | Erreurs                 |
| ------- | ------------------------------------ | ------- | ------------------------------------------------------------------ | -------------------- | ----------------------- |
| `GET`   | `/api/v1/invitations/{token}`        | public  | —                                                                  | `InvitationResponse` | `400`                   |
| `POST`  | `/api/v1/signup`                     | public  | `SignupRequest` : `email`, `password`, `invitationToken`, `firstName`, `lastName` | `SignupResponse` : `id`, `email` (`200`) | `400`, `409`, `502` |
| `POST`  | `/api/v1/invitations/{token}/claim`  | session | —                                                                  | `InvitationResponse` | `400`, `401`, `502`     |

`InvitationResponse` : `token`, `clientId`, `applicationName`, `invitationUrl`, `applicationUrl`.

## Mot de passe

Détail : [password-reset.md](password-reset.md).

| Méthode | Chemin                     | Accès  | Entrée                                        | Réponse                              | Erreurs |
| ------- | -------------------------- | ------ | --------------------------------------------- | ------------------------------------ | ------- |
| `POST`  | `/api/v1/password/forgot`  | public | `ForgotPasswordRequest` : `email`             | `PasswordResetResponse` : `message`  | aucune prévue (toujours `200`) |
| `POST`  | `/api/v1/password/reset`   | public | `ResetPasswordRequest` : `email`, `token`, `password` | `PasswordResetResponse` : `message` | `400` |

## Internes (appelés par les applications)

| Méthode | Chemin                       | Accès  | Entrée                                                 | Réponse              | Erreurs      |
| ------- | ---------------------------- | ------ | ------------------------------------------------------ | -------------------- | ------------ |
| `POST`  | `/api/internal/invitations`  | secret | `InvitationRequest` : `clientId`, `playerId` (facultatif) | `InvitationResponse` (`200`) | `400`, `401` |

Dans l'autre sens, le serveur appelle le `provisioning-url` de chaque application
([registration.md](registration.md)).

## Format d'erreur

Il n'y a pas de handler centralisé : toutes les erreurs sortent au format par défaut de Spring Boot, sans le message
de l'exception ni le code OAuth :

```json
{"timestamp": "2026-11-27T10:00:00.000+00:00", "status": 400, "error": "Bad Request", "path": "/oauth2/token"}
```

Une route authentifiée appelée sans session ni jeton est refusée par Spring Security (redirection vers la page de
connexion du portail pour une navigation). Écarts avec la cible (corps OAuth2 `{"error": ...}`, validation des corps,
réponses en `Map`) : [../tech-debt.md](../tech-debt.md) et [../conventions/api-design.md](../conventions/api-design.md).
