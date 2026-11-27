# Vue d'ensemble

La stack, le découpage en packages par feature et le parcours d'une requête de bout en bout.

## Fichiers clés

- `IdentityServerApplication.java` : point d'entrée, `@EnableConfigurationProperties(IdentityProperties)`,
  `@EnableScheduling`, CORS MVC.
- `config/IdentityProperties.java` : propriétés `identity.*` (clients OAuth, backends, scopes, CORS, réinitialisation).
- `security/SecurityConfig.java` : chaîne de filtres Spring Security.
- `pom.xml` : dépendances et plugins (Lombok, JaCoCo, Surefire).
- `frontend/` : le portail de connexion (voir [frontend.md](frontend.md)).

## Stack

| Besoin             | Choix                                                                              |
| ------------------ | ---------------------------------------------------------------------------------- |
| Langage, runtime   | Java 21, Spring Boot 3.5                                                           |
| Web                | Spring Web (MVC, Tomcat), `RestClient` pour les appels sortants                    |
| Sécurité           | Spring Security : form login et session, OAuth 2.0 resource server (JWT), BCrypt   |
| Jetons             | JJWT 0.12 (signature RS256), clé RSA 2048 gardée en base                           |
| Données            | Spring Data JPA (Hibernate), PostgreSQL 17, Flyway                                 |
| E-mail             | Spring Mail (SMTP), Thymeleaf pour les gabarits HTML                               |
| Boilerplate        | Lombok (`@Getter`, `@Setter`, `@NoArgsConstructor`)                                |
| Tests              | JUnit 5, Mockito, JaCoCo ; H2 présent dans le `pom.xml` mais inutilisé             |
| Livraison          | Docker (build Maven), semantic-release, VPS                                        |
| Portail            | React 19, Vite, antd 6, emotion, TanStack Query                                    |

## Packages par feature

Le code est rangé par feature ou par protocole ([ADR 0004](../decisions/0004-organisation-par-feature.md)) :

| Package          | Contenu                                                                                    |
| ---------------- | ------------------------------------------------------------------------------------------ |
| `oauth2`         | `TokenController` (routes OAuth2 / OIDC), `OAuth2Service` (flux, codes, émission)          |
| `registration`   | `RegistrationController`, `RegistrationService` (inscription, invitations, provisioning), `dto/` |
| `password_reset` | controller, service, entité `PasswordResetToken`, `EmailSender`, `dto/`                    |
| `invitation`     | entité `InvitationToken` et son repository                                                 |
| `token`          | entité `RefreshTokenSession` et son repository                                             |
| `user`           | entité `IdentityUser` et son repository                                                    |
| `security`       | `SecurityConfig`, `TokenService` (clé et JWT), entité `SigningKey`                         |
| `email`          | `EmailClient` / `SmtpEmailClient`, `EmailTemplateRenderer`, `EmailMessage`                 |
| `config`         | `IdentityProperties`                                                                       |

## Couches

Pas de sous-dossiers par couche : dans un package, les classes sont à plat.

```
XxxController     @RestController : route, paramètres, corps → un seul service
XxxService        @Service, classe concrète : règles, transactions, appels aux repositories et intégrations
XxxRepository     interface Spring Data (requêtes dérivées)
Xxx (entité)      @Entity JPA
dto/              record de requête et de réponse
```

Les services ne sont pas des interfaces : une seule implémentation chacun. Seuls `EmailClient` et `EmailSender` en
sont, pour isoler le SMTP.

## Parcours d'une requête

Exemple : `POST /oauth2/token` avec `grant_type=authorization_code`.

1. **Sécurité** (`SecurityConfig`) : route publique, exclue du CSRF ; CORS appliqué.
2. **Controller** (`TokenController.token`) : lie les paramètres de formulaire (`grant_type`, `client_id`, `code`,
   `redirect_uri`, `code_verifier`, `refresh_token`) et appelle `OAuth2Service.token`.
3. **Service** (`OAuth2Service.authorizationCode`) : retire le code de la `Map` en mémoire, vérifie expiration,
   client, `redirect_uri` et PKCE, puis `issue` : crée une `RefreshTokenSession` (jeton haché) et demande les JWT à
   `TokenService`.
4. **Réponse** : une `Map` sérialisée en JSON (`access_token`, `id_token`, `refresh_token`, `token_type`,
   `expires_in`, `scope`).

Détail du protocole : [oauth2.md](oauth2.md).

## Gestion des erreurs

Il n'y a pas de `@RestControllerAdvice` :

- les services lèvent `ResponseStatusException` avec le statut voulu (`400`, `401`, `409`, `502`) ;
- `OAuth2Service` lève `OAuthException`, classe imbriquée annotée `@ResponseStatus(HttpStatus.BAD_REQUEST)`, dont le
  message est le code OAuth (`invalid_request`, `invalid_grant`, `invalid_target`, `unsupported_grant_type`) ;
- la réponse est le format d'erreur par défaut de Spring Boot (`timestamp`, `status`, `error`, `path`), sans le
  message ni le code OAuth (`server.error.include-message` n'est pas activé) ;
- une exception inattendue sort en `500`.

Écarts avec la cible : [../tech-debt.md](../tech-debt.md).

## Tâches de fond

`@EnableScheduling` est actif, mais aucune tâche `@Scheduled` n'existe : les codes d'autorisation expirés et les
lignes expirées ne sont jamais purgés.
