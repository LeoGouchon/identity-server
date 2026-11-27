# Sécurité

Comment le serveur authentifie l'utilisateur (formulaire et session), protège ses routes, accepte les jetons qu'il
émet et authentifie les applications par secret.

## Fichiers clés

- `security/SecurityConfig.java` : chaîne de filtres, form login, CSRF, CORS, `UserDetailsService`, BCrypt, décodeur
  JWT.
- `IdentityServerApplication.java` : seconde configuration CORS (MVC).
- `registration/RegistrationService.java` : contrôle du secret de provisioning.
- `config/config.example.yml` : `server.servlet.session.cookie.*`, `identity.cors`, `identity.auth-frontend-url`.

## Accès par route

Règles de `SecurityConfig`, dans l'ordre :

| Route                                                                              | Accès                                  |
| ---------------------------------------------------------------------------------- | -------------------------------------- |
| `/.well-known/**`, `/oauth2/jwks`, `/oauth2/token`, `/oauth2/revoke`, `/error`     | public                                 |
| `/api/internal/**`                                                                 | public (protégé par un secret partagé) |
| `/oauth2/authorize`, `/userinfo`, `/connect/logout`, `/api/v1/invitations/*/claim` | authentifié (session ou bearer JWT)    |
| tout le reste (`/api/v1/signup`, `/api/v1/password/**`, `GET /api/v1/invitations/*`…) | public (`anyRequest().permitAll()`) |

Une route nouvelle est donc **publique par défaut** si elle n'est pas ajoutée à la liste `authenticated()`.

## Connexion par formulaire

- Le portail poste `username` (l'e-mail) et `password` sur `POST /login` (formulaire HTML natif, voir
  [frontend.md](frontend.md)).
- `loginPage` et `failureUrl` pointent vers le portail : `identity.auth-frontend-url + "/login"` et
  `"/login?error=authentication_failed"`. Une route protégée appelée sans session redirige donc vers le portail.
- **Succès** : si le formulaire contient un champ `invitation`, redirection vers
  `<portail>/invitation/complete?invitation=<jeton>` ; sinon `SavedRequestAwareAuthenticationSuccessHandler` renvoie à
  la requête d'origine (en pratique `/oauth2/authorize`).
- `UserDetailsService` cherche l'utilisateur par e-mail sans tenir compte de la casse, refuse un compte
  `enabled = false` et donne à tous l'autorité `ROLE_USER`. Les mots de passe sont vérifiés par `BCryptPasswordEncoder`.
- `/logout` (Spring Security) redirige vers `/` ; le logout OIDC est `/connect/logout` ([oauth2.md](oauth2.md)).

```java
.formLogin(form -> form
        .loginPage(authFrontendUrl + "/login")
        .loginProcessingUrl("/login")
        .failureUrl(authFrontendUrl + "/login?error=authentication_failed")
```

## Session et cookies

La session HTTP de Spring Security sert la connexion unique entre clients. Le cookie est réglé dans
`config/config.example.yml` :

| Propriété                                   | Valeur                                           |
| ------------------------------------------- | ------------------------------------------------ |
| `server.servlet.session.cookie.http-only`   | `true`                                           |
| `server.servlet.session.cookie.secure`      | `IDENTITY_COOKIE_SECURE` (`false` par défaut)    |
| `server.servlet.session.cookie.same-site`   | `IDENTITY_COOKIE_SAME_SITE` (`LAX` par défaut)   |

La propriété `cookie.secure` du même fichier n'est lue par aucun code.

## CSRF

CSRF est actif, sauf sur `/login`, `/oauth2/token`, `/oauth2/revoke`, `/api/internal/**`, `/api/v1/signup`,
`/api/v1/password/**` et `/api/v1/invitations/*/claim`. En pratique, toutes les routes `POST` appelées par le portail
ou les clients en sont exclues.

## Jetons reçus (resource server)

`.oauth2ResourceServer(oauth -> oauth.jwt(...))` permet d'appeler les routes authentifiées avec
`Authorization: Bearer <JWT>`. Le décodeur est un `NimbusJwtDecoder` construit sur la **clé publique locale**
(`TokenService.keyPair()`) : il vérifie la signature et les dates, mais ni l'émetteur ni l'audience. Les services
distinguent les deux modes par le principal :

```java
UUID subject = authentication.getPrincipal() instanceof Jwt jwt
        ? UUID.fromString(jwt.getSubject())
        : users.findByEmailIgnoreCase(authentication.getName()).orElseThrow().getId();
```

## CORS

Les origines viennent de `identity.cors.allowed-origins`, déclarées deux fois :

- `SecurityConfig.corsConfigurationSource` (utilisée par Spring Security) : méthodes `GET`, `POST`, `OPTIONS`,
  en-têtes `Content-Type` et `Authorization`, cookies autorisés ;
- `IdentityServerApplication.addCorsMappings` (MVC) : toutes méthodes et tous en-têtes, cookies autorisés.

## Secrets de provisioning

Chaque client OAuth a son `provisioning-secret` (`identity.oauth-clients[]`). Il est :

- **reçu** dans l'en-tête `X-Identity-Provisioning-Secret` de `POST /api/internal/invitations` et comparé avec
  `String.equals` au secret du client demandé (`401` sinon) ;
- **envoyé** dans le même en-tête lors du provisioning vers l'application ([registration.md](registration.md)).

## Données sensibles stockées

| Donnée                       | Stockage                                                 |
| ---------------------------- | -------------------------------------------------------- |
| Mot de passe                 | hachage BCrypt (`identity_user.password_hash`)           |
| Refresh token                | SHA-256 en base64url (`refresh_token_session.token_hash`) |
| Jeton de réinitialisation    | SHA-256 en hexadécimal (`password_reset_token.token_hash`) |
| Jeton d'invitation           | en clair (`invitation_token.token`)                      |
| Clé privée de signature      | PKCS#8 en base64, en clair (`signing_key.private_key`)   |

## Limites connues

Décodeur sans `iss` ni `aud`, exclusions CSRF de `/login` et du `claim`, comparaison de secret non constante, clé
privée en clair, `email_verified` toujours vrai, CORS en double : voir [../tech-debt.md](../tech-debt.md). Règles à
suivre : [../conventions/security.md](../conventions/security.md).
