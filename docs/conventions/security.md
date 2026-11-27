# Sécurité

Les règles propres à un serveur d'identité : ce qu'il garde, ce qu'il compare et ce qu'il révèle. Le fonctionnement
actuel est dans [../architecture/security.md](../architecture/security.md) et
[../architecture/oauth2.md](../architecture/oauth2.md).

## Fichiers clés

- `security/SecurityConfig.java` : accès des routes, CSRF, CORS, formulaire de connexion, encodeur de mots de passe.
- `oauth2/OAuth2Service.java` : PKCE, `redirect_uri`, audience, rotation des refresh tokens.
- `registration/RegistrationService.java` : secret de provisioning.
- `password_reset/PasswordResetService.java` : jetons à usage unique, réponse générique.

## Jetons stockés hachés

Un jeton qui donne un accès (refresh token, jeton de réinitialisation) n'est **jamais** stocké en clair : on garde
son SHA-256 et on cherche par le hash. La valeur brute ne sort qu'une fois, vers son destinataire.

```java
tokens.save(new PasswordResetToken(user.getId(), hash(rawToken), expiresAt, now));
```

- Un jeton se génère avec `SecureRandom` (32 octets) ou `UUID.randomUUID()`, jamais avec `Random`.
- Un jeton est à usage unique quand le protocole le dit (code d'autorisation, réinitialisation, refresh token) et a
  une expiration.

**État** : ✅ refresh tokens et jetons de réinitialisation hachés ; ⚠️ jetons d'invitation stockés en clair (voir
[../tech-debt.md](../tech-debt.md)).

## Secrets et mots de passe

- Un **secret** partagé (`provisioning-secret`) se compare en temps constant : `MessageDigest.isEqual(a, b)` sur les
  octets, jamais `String.equals`.
- Un **mot de passe** passe par le `PasswordEncoder` (BCrypt) de `SecurityConfig`, jamais par un hachage maison. La
  même règle de mot de passe (8 caractères minimum) s'applique à l'inscription et à la réinitialisation.
- Les valeurs `change-me` de `config/config.example.yml` ne servent qu'en local ; un secret différent par
  environnement et par application.
- Aucun secret dans le portail (`frontend/`), dans une réponse, dans un log ni dans Git (`.env`, `config/config.yml`
  sont ignorés).

**État** : ✅ BCrypt ; ⚠️ secret de provisioning comparé avec `String.equals` ; inscription sans longueur minimale ;
`.env` suivi par Git.

## Pas d'énumération des comptes

Une réponse ne révèle pas si une adresse a un compte : même statut, même corps, et autant que possible même durée,
qu'elle existe ou non.

```java
users.findByEmailIgnoreCase(request.email()).ifPresent(user -> { ... });
return new PasswordResetResponse(GENERIC_MESSAGE);
```

- Un échec de connexion renvoie une seule erreur (`authentication_failed`), sans distinguer e-mail et mot de passe.
- Un échec de jeton de réinitialisation donne toujours « lien invalide ou expiré ».

**État** : ✅ demande de réinitialisation, connexion ; ⚠️ un échec SMTP révèle les comptes existants ; l'inscription
répond `409` sur un e-mail déjà pris (accepté : elle exige une invitation valide).

## Protocole OAuth2

- **`redirect_uri`** (et `post_logout_redirect_uri`) comparé **exactement** à la liste du client : schéma, port,
  chemin, barre finale. Jamais de préfixe ni de motif.
- **PKCE S256 obligatoire** ; le `code_verifier` est vérifié à l'échange du code.
- **Code d'autorisation** à usage unique, expiré après 2 minutes, lié au client et au `redirect_uri`.
- **`state`** renvoyé tel quel et **encodé** dans l'URL de redirection (`UriComponentsBuilder`), jamais concaténé.
- **Scope** limité aux scopes configurés.
- **Audience** : choisie parmi `identity.allowed-ressources` (`resource`, sinon le premier backend) ; un backend
  inconnu est refusé (`invalid_target`). La rotation du refresh token **conserve** l'audience d'origine.
- **Refresh token** tourné à chaque usage ; l'ancien est marqué utilisé et révoqué.
- **Claims** : un claim affirme un fait vérifié (`email_verified` ne vaut `true` que si l'adresse l'est).
- **Jetons reçus** : un JWT accepté par ce serveur (`/userinfo`) a son émetteur et son audience vérifiés.

**État** : ✅ `redirect_uri`, PKCE, code, audience, rotation ; ⚠️ `state` concaténé, scope non contrôlé,
`email_verified` toujours `true`, décodeur JWT sans émetteur ni audience, pas de détection de rejeu.

## Routes et CSRF

- `SecurityConfig` part de **règles explicites** : une nouvelle route publique s'ajoute à `permitAll` avec la raison
  dans la PR ; une route qui agit pour un utilisateur connecté est `authenticated()`.
- Une exclusion CSRF n'est permise que pour une route sans cookie de session (serveur à serveur protégé par secret,
  endpoints OAuth2 appelés par un client public). Une route authentifiée par cookie garde le CSRF, ou l'exception est
  justifiée par écrit.
- Les origines CORS sont une liste exacte (`identity.cors.allowed-origins`), jamais `*` avec les credentials.
- Hors local : cookie de session `Secure` (`IDENTITY_COOKIE_SECURE=true`), `SameSite=Lax`, HTTPS.

**État** : ⚠️ `anyRequest().permitAll()` par défaut ; `/login` et `claim` exclus du CSRF ; CORS déclaré deux fois.

## Clé de signature

La clé privée RSA ne sort jamais du serveur ; seule la clé publique est publiée (`/oauth2/jwks`). Un changement de
gestion de clé (rotation, coffre) passe par un ADR ([ADR 0002](../decisions/0002-serveur-oauth2-maison.md)).

**État** : ⚠️ clé privée en clair en base, `kid` figé, pas de rotation.
