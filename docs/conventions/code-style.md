# Style de code

Les règles d'écriture Java communes à tout le projet. Il n'y a pas de formateur automatique (voir
[../tech-debt.md](../tech-debt.md)) : on suit le style du code voisin, formaté par l'IDE (indentation de 4 espaces).

## Fichiers clés

- `pom.xml` : Java 21, Spring Boot 3.5, Lombok, JJWT.
- `password_reset/PasswordResetService.java` : un service typique (injection, transactions, temps en UTC).
- `config/IdentityProperties.java` : la configuration `identity.*`.

## Injection par constructeur

Les dépendances sont des champs `private final`, injectés par l'unique constructeur. Pas d'`@Autowired` (inutile avec
un seul constructeur), jamais sur un champ. Un bean n'est jamais instancié avec `new` dans un autre bean, et un
controller ou un service n'instancie pas d'infrastructure (client HTTP, handler Spring, encodeur).

```java
@Service
public class PasswordResetService {
    private final IdentityUserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final EmailSender emailSender;

    public PasswordResetService(IdentityUserRepository users, PasswordResetTokenRepository tokens,
                                EmailSender emailSender) { ... }
}
```

Un client se construit à partir du bean fourni par Spring (`RestClient.Builder` injecté, puis `build()`), comme
`RegistrationService`.

**État** : ⚠️ `TokenController.logout` instancie `SecurityContextLogoutHandler`, `SecurityConfig` instancie
`SavedRequestAwareAuthenticationSuccessHandler` dans un lambda.

## Configuration

Une propriété de `config/config.yml` est lue par un `record` `@ConfigurationProperties`, validé au démarrage, puis
injecté là où il sert :

```java
@Validated
@ConfigurationProperties(prefix = "identity")
public record IdentityProperties(@NotBlank String issuer, @NotEmpty List<OAuthClient> oauthClients, ...) {
}
```

- Pas de `@Value` éparpillés : toute la configuration `identity.*` est au même endroit, et une valeur manquante
  arrête le démarrage avec un message clair.
- Une valeur par défaut est écrite **une seule fois** (dans `config/config.example.yml` ou le `record`).
- Une nouvelle propriété s'ajoute aussi à `config/config.example.yml`.

**État** : ⚠️ `IdentityProperties` est une classe à setters ; `identity.issuer`, `access-token-ttl-seconds`,
`refresh-token-ttl-days` et `auth-frontend-url` sont lus par `@Value` dans quatre classes, avec une valeur par défaut
dupliquée.

## Lombok

- Sur une **entité** : `@Getter` et `@NoArgsConstructor` (pour JPA) seulement. Pas de `@Setter` de classe (il
  contourne les invariants, voir [object-design.md](object-design.md#encapsulation)), ni `@Data`, `@EqualsAndHashCode`
  ou `@ToString`.
- Les DTO et la configuration sont des `record` : pas de Lombok.

**État** : ✅ `PasswordResetToken` ; ⚠️ `@Getter @Setter` de classe sur `IdentityUser`, `InvitationToken`,
`RefreshTokenSession`, `SigningKey` et `IdentityProperties`.

## Nommage

| Élément           | Règle                                               | Exemple                                  |
| ----------------- | --------------------------------------------------- | ---------------------------------------- |
| Controller        | `<Feature>Controller`, ou nom du protocole          | `RegistrationController`, `TokenController`|
| Service           | `<Feature>Service`, classe concrète                 | `RegistrationService`, `OAuth2Service`   |
| Repository        | `<Entité>Repository` (Spring Data)                  | `InvitationTokenRepository`              |
| DTO               | `<Action>Request` / `<Action>Response`, sans `DTO`  | `SignupRequest`, `InvitationResponse`    |
| Intégration       | interface du rôle, implémentation technique         | `EmailClient` → `SmtpEmailClient`        |
| Exception         | `<Cause>Exception`, dans la feature                 | `OAuthException`                         |
| Méthode, variable | `camelCase`, verbe pour une méthode                 | `createInvitation`, `isUsableAt`         |
| Constante         | `UPPER_SNAKE_CASE`, `static final`                  | `GENERIC_MESSAGE`                        |

Packages : [project-structure.md](project-structure.md). Tests : [testing.md](testing.md#nommage).

## Imports et types

- Toujours des imports, jamais de nom pleinement qualifié dans le code (`java.util.UUID.randomUUID()`).
- `var` est accepté quand le type est évident à droite du `=`.
- `Optional` en retour d'une recherche qui peut ne rien trouver ; jamais en paramètre ni en champ. Pas de `.get()`,
  ni d'`orElseThrow()` sans argument : `orElseThrow` avec l'exception typée de la feature.

**État** : ⚠️ noms pleinement qualifiés dans `TokenService`, `SecurityConfig`, `OAuth2Service`,
`RegistrationService`, `PasswordResetService` et les tests ; `orElseThrow()` sans argument dans `OAuth2Service` et
`RegistrationService` (une absence sort en `500`).

## Le temps

On n'utilise **jamais** l'horloge implicite du système. Chaque lecture du temps passe un fuseau ou une horloge
explicite, et tout est en **UTC**, en base comme dans les jetons :

- `LocalDateTime.now(ZoneOffset.UTC)` pour les dates enregistrées en base et les comparaisons d'expiration ;
- `Instant.now(Clock.systemUTC())` pour les dates des JWT (`iat`, `exp`) ;
- jamais `LocalDateTime.now()`, `Instant.now()` ni `new Date()` sans argument ; `java.util.Date` seulement à la
  frontière de JJWT (`Date.from(instant)`).

```java
LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
tokens.save(new PasswordResetToken(user.getId(), hash(rawToken), now.plusMinutes(ttl), now));
```

Une méthode d'entité qui dépend du temps le reçoit en paramètre (`isUsableAt(now)`, `consumeAt(now)`).

**État** : ✅ toutes les lectures du temps passent `ZoneOffset.UTC` ou `Clock.systemUTC()`.

## Exceptions

- Une erreur attendue lève une **exception typée** de la feature, qui porte son statut HTTP (`@ResponseStatus`) ou
  que traduit un handler centralisé ([api-design.md](api-design.md#erreurs)).
- On n'attrape une exception que pour la traiter ou la traduire avec sa cause (comme `RegistrationService` traduit un
  refus du provisioning en `502`). Pas de `catch (Exception e)` qui masque, pas de `throws RuntimeException`.
- Une exception vérifiée impossible (`NoSuchAlgorithmException` pour SHA-256) devient une `IllegalStateException`.

**État** : ⚠️ les services lèvent des `ResponseStatusException` ; `OAuthException` est imbriquée dans
`OAuth2Service`.

## Logs et commentaires

- Logs SLF4J (`LoggerFactory.getLogger` ou `@Slf4j`), jamais `System.out`. Un log d'erreur passe l'exception.
- **Jamais** de secret, de jeton (code, refresh token, jeton d'invitation ou de réinitialisation), de mot de passe ni
  d'e-mail complet dans un log : on journalise l'identifiant du compte et le `clientId`.
- Un commentaire dit le _pourquoi_ que le code ne montre pas, en une ligne, en anglais. Pas de code commenté.

**État** : ✅ `RegistrationService` journalise l'identifiant du compte, le client et l'URL de provisioning.

## Langue

- Code, commentaires, messages d'erreur renvoyés par l'API et commits en **anglais**.
- Textes du portail (`frontend/`) et des e-mails (`templates/email/`) en **français** : le portail affiche ses propres
  messages, il ne montre pas ceux de l'API.
- Documentation (`docs/`, `specs/`) en **français**.

**État** : ⚠️ `PasswordResetService` renvoie ses messages en français.
