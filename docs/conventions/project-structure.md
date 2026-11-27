# Structure du projet

Où ranger chaque classe : le code est organisé par feature ou par protocole, et les controllers ne contiennent que
la définition des endpoints. Pourquoi : [ADR 0004](../decisions/0004-organisation-par-feature.md).

## Fichiers clés

- `src/main/java/com/leogouchon/identityserver/` : un package par feature.
- `src/test/java/com/leogouchon/identityserver/` : même arborescence.
- [../architecture/overview.md](../architecture/overview.md) : le rôle de chaque package aujourd'hui.

## Un package par feature

```text
com.leogouchon.identityserver/
+-- oauth2/            TokenController, OAuth2Service (flux OAuth2 / OIDC)
+-- registration/      RegistrationController, RegistrationService, dto/ (inscription, invitations)
+-- password_reset/    PasswordResetController, PasswordResetService, entité, repository, dto/
+-- invitation/        InvitationToken, InvitationTokenRepository
+-- token/             RefreshTokenSession, RefreshTokenSessionRepository
+-- user/              IdentityUser, IdentityUserRepository
+-- security/          SecurityConfig, TokenService, SigningKey, SigningKeyRepository
+-- email/             EmailClient, SmtpEmailClient, EmailTemplateRenderer
+-- config/            IdentityProperties
```

- Un **nouveau controller** va dans le package de la feature qu'il expose. Son service, ses DTO, ses entités et ses
  repositories restent à côté quand c'est possible.
- Une entité partagée par plusieurs features (`IdentityUser`, `InvitationToken`) a son propre package, sans
  controller.
- Les couches sont à plat dans le package (pas de sous-dossiers `controller/`, `service/`), sauf `dto/`.
- Un nouveau package est en minuscules, sans séparateur (`passwordreset`), selon la convention Java.

**État** : ⚠️ `password_reset` est en snake_case ; on le garde tant qu'aucune PR dédiée ne le renomme.

## Controllers minces

Un controller contient **uniquement** la définition de ses endpoints :

- il lie la route HTTP, les paramètres, les en-têtes et le corps ;
- il délègue la validation et la logique métier à son service ;
- il renvoie le résultat du service, ou fait l'opération HTTP minimale de l'endpoint (une redirection).

Il ne fait **jamais** : accès à un repository, encodage d'un mot de passe, création d'un jeton, contrôle de droits,
modification d'une entité. Il n'attrape pas les exceptions métier pour refaire le travail du service : elles
remontent et portent leur statut ([api-design.md](api-design.md#erreurs)).

```java
@PostMapping("/v1/signup")
public SignupResponse signup(@Valid @RequestBody SignupRequest request) {
    return registration.signup(request);
}
```

**État** : ✅ `RegistrationController`, `PasswordResetController` ; ⚠️ `TokenController.logout` instancie et appelle
`SecurityContextLogoutHandler` ; aucun corps n'est validé (`@Valid` absent).

## Un service par controller

Chaque controller a **son** service de feature : `TokenController` → `OAuth2Service`, `RegistrationController` →
`RegistrationService`, `PasswordResetController` → `PasswordResetService`. Le service porte le déroulé applicatif :
validation, coordination des écritures, génération des jetons, appels aux intégrations.

- Un service est une **classe concrète** : pas d'interface tant qu'il n'existe qu'une implémentation
  ([object-design.md](object-design.md#patterns)).
- Un service qui accumule des cas d'usage sans rapport se découpe ([object-design.md](object-design.md#solid)).
- **OAuth2** : les routes du protocole restent dans `oauth2.TokenController`, leur comportement dans
  `oauth2.OAuth2Service`.

**État** : ✅.

## DTO

- Un DTO de requête ou de réponse est un `record` **autonome**, dans le package `dto` de sa feature
  (`registration/dto/SignupRequest.java`).
- Jamais de `record` imbriqué dans un controller ou un service pour un contrat d'API.
- Noms explicites (`SignupRequest`, `InvitationRequest`, `SignupResponse`), jamais une `Map` quand la forme est
  stable.
- Jamais d'entité JPA dans une requête ou une réponse.

**État** : ✅ `registration/dto/`, `password_reset/dto/` ; ⚠️ `OAuth2Service` et `TokenController` renvoient des
`Map<String, Object>` (jetons, userinfo, discovery, JWKS).

## Fichiers satellites

- **Exception typée** : dans le package de sa feature, une classe par cause (`InvitationExpiredException`).
- **Abstraction d'intégration** : interface dans la feature qui l'utilise ou dans un package technique (`email/`),
  implémentation à côté ([object-design.md](object-design.md#patterns)).
- **Record interne** : un `record` privé au service est permis pour un état qui ne sort pas
  (`OAuth2Service.AuthorizationCode`).

## Ressources

- `src/main/resources/db/migration/` : migrations Flyway ([persistence.md](persistence.md#migrations-flyway)).
- `src/main/resources/db/dev-data/` : données de dev, jamais chargées en production.
- `src/main/resources/templates/email/` : gabarits Thymeleaf des e-mails, avec `fragments/layout.html`.
- **Aucun** `application*.yml` dans `src/main/resources` : la configuration est externe, dans `config/`
  (`config/config.example.yml` versionné, `config/config.yml` ignoré par Git).

## Portail

`frontend/` est une application React séparée (Vite), servie à part. Sa structure suit
[frontend.md](frontend.md).

## Tests

`src/test/java` reproduit l'arborescence de `src/main/java` (`oauth2/OAuth2ServiceTest.java`). Détail :
[testing.md](testing.md).
