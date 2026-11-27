# Conception de l'API

Les règles des endpoints : routes, paramètres, DTO, validation, statuts, erreurs et compatibilité avec les clients.
La liste des endpoints existants est dans [../architecture/api.md](../architecture/api.md).

## Fichiers clés

- `oauth2/TokenController.java` : les routes du protocole OAuth2 / OIDC.
- `registration/RegistrationController.java`, `password_reset/PasswordResetController.java` : les routes de l'API.
- `security/SecurityConfig.java` : accès de chaque route.

## Routes

| Préfixe             | Pour qui                              | Accès                                             |
| ------------------- | ------------------------------------- | ------------------------------------------------- |
| protocole (*)       | clients OAuth2 et navigateur          | chemins fixés par les RFC                         |
| `/api/v1/...`       | le portail et les navigateurs         | public, ou session / jeton selon la route         |
| `/api/internal/...` | serveur à serveur (une application)   | secret `X-Identity-Provisioning-Secret` du client |

(*) `/.well-known/**`, `/oauth2/**`, `/userinfo`, `/connect/logout`, `/login`.

- Les chemins du protocole ne se renomment pas : les clients les lisent dans la discovery ou les codent en dur.
- Une route de l'API est versionnée (`/api/v1/`), au pluriel pour une collection (`/invitations/{token}`), et une
  action qui n'est pas du CRUD est un `POST` sur un sous-chemin (`/invitations/{token}/claim`).
- Toute nouvelle route a un accès **décidé** dans `SecurityConfig` ([security.md](security.md#routes-et-csrf)).

**État** : ✅.

## Paramètres OAuth2

- Noms standard en **snake_case** (`response_type`, `client_id`, `redirect_uri`, `code_verifier`, `grant_type`),
  liés par `@RequestParam("…")`.
- `/oauth2/token` et `/oauth2/revoke` reçoivent du `application/x-www-form-urlencoded` (RFC 6749, RFC 7009).
- Les réponses du protocole gardent leurs noms standard (`access_token`, `expires_in`, `id_token`).

**État** : ✅.

## DTO et JSON

- Corps JSON de l'API en **camelCase** (`invitationToken`, `clientId`), portés par des `record` du package `dto` de
  la feature ([project-structure.md](project-structure.md#dto)).
- Une réponse du protocole à contrat stable (jetons, userinfo) est aussi un `record`, avec `@JsonProperty` pour le
  snake_case.

**État** : ⚠️ réponses du protocole en `Map<String, Object>`.

## Validation

Le corps d'une requête est validé à l'entrée par Jakarta Validation : contraintes sur le `record`, `@Valid
@RequestBody` dans le controller. Les règles qui dépendent de la base ou de la configuration (invitation valide,
client connu) restent dans le service.

```java
public record SignupRequest(@NotBlank @Email String email, @NotBlank @Size(min = 8) String password,
                            @NotBlank String invitationToken, @Size(max = 100) String firstName,
                            @Size(max = 100) String lastName) {
}
```

**État** : ⚠️ aucun corps validé ; l'inscription accepte un mot de passe vide.

## Statuts HTTP

| Cas                                                        | Statut                                   |
| ---------------------------------------------------------- | ---------------------------------------- |
| Lecture ou action réussie                                  | `200` avec le corps                      |
| Redirection du protocole (`authorize`, `logout`, `login`)  | `302` vers une URL validée               |
| Corps ou paramètre invalide, invitation invalide ou expirée | `400`                                    |
| Erreur du protocole OAuth2                                 | `400` avec `{"error": "..."}` (RFC 6749) |
| Secret absent ou faux, session absente                     | `401`                                    |
| Doublon (e-mail déjà pris)                                 | `409`                                    |
| Application aval injoignable ou qui refuse le provisioning | `502`                                    |
| Inattendu                                                  | `500` générique, sans détail interne     |

Une réponse ne contient jamais de détail interne (trace, SQL, secret, URL interne).

## Erreurs

- Le **service** lève une exception typée qui porte son statut : `@ResponseStatus` sur la classe, ou traduction par
  un handler centralisé (`@RestControllerAdvice`) quand le corps doit suivre un format.
- Le **controller** ne l'attrape pas : il la laisse remonter au mécanisme centralisé.
- Les erreurs du protocole OAuth2 produisent le format standard : `{"error": "invalid_grant"}` sur `/oauth2/token`,
  et, pour `/oauth2/authorize` avec un `redirect_uri` valide, une redirection avec `error` et `state`.

```java
@ResponseStatus(HttpStatus.CONFLICT)
public class EmailAlreadyUsedException extends RuntimeException {
    public EmailAlreadyUsedException() {
        super("Email already exists");
    }
}
```

**État** : ✅ aucun controller n'attrape d'exception ; ⚠️ `ResponseStatusException` dans les services, et
`OAuthException` sort au format d'erreur Spring, sans le code OAuth2.

## Compatibilité avec les clients

Ce serveur a plusieurs consommateurs : les fronts (kicker-app et le portail) et les API qui valident ses jetons
(hubscore). Sont des **contrats** :

- les chemins et paramètres du protocole, et la discovery ;
- les claims des jetons (`sub`, `aud`, `email`, `given_name`, `family_name`, `nonce`, `scope`) ;
- le corps envoyé au provisioning (`identityUserId`, `email`, `firstName`, `lastName`, `playerId`) et l'en-tête du
  secret ([ADR 0005](../decisions/0005-inscription-sur-invitation.md)) ;
- les DTO de l'API lus par le portail.

Ajouter un champ ou un claim facultatif est compatible. Retirer ou renommer un champ, changer un statut ou le sens
d'un claim est une **rupture** : elle se coordonne avec les clients touchés, s'annonce par `!` dans le commit
([git-workflow.md](git-workflow.md)) et passe par un ADR si elle touche au protocole.
