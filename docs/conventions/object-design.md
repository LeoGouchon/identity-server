# Conception objet

Les principes SOLID et objet, appliqués avec pragmatisme, et les patterns à utiliser quand une variation ou une
frontière existe vraiment. Pourquoi : [ADR 0004](../decisions/0004-organisation-par-feature.md).

## Fichiers clés

- `password_reset/PasswordResetToken.java` : une entité qui garde son invariant (`isUsableAt`, `consumeAt`).
- `email/EmailClient.java`, `email/SmtpEmailClient.java` : une intégration derrière une abstraction.
- `password_reset/EmailSender.java` : le contrat dont dépend le service, testable sans SMTP.

## SOLID

- **Responsabilité unique** : controllers, services applicatifs, entités, repositories, service de jetons et
  intégrations externes ont chacun une préoccupation cohérente. On découpe un service quand des cas d'usage ou des
  politiques sans rapport s'y accumulent.
- **Inversion des dépendances** : on dépend d'une interface ou d'une abstraction du domaine quand une intégration, un
  stockage ou une politique peut varier. Les dépendances arrivent par le constructeur
  ([code-style.md](code-style.md#injection-par-constructeur)) ; un controller ou un service n'instancie jamais
  d'infrastructure.
- **Composition plutôt qu'héritage** : un service se compose de collaborateurs ciblés. L'héritage est réservé à une
  vraie relation « est un », substituable.

**État** : ⚠️ `TokenService` gère la clé de signature **et** émet les jetons ; la redirection après connexion vit dans
`SecurityConfig`.

## Encapsulation

L'état modifiable reste privé. Les invariants sont protégés par le constructeur ou par des méthodes du domaine. Pas
de setter ajouté pour arranger la persistance ou les tests : un setter n'existe que si la transition qu'il représente
est valide.

```java
public PasswordResetToken(UUID userId, String tokenHash, LocalDateTime expiresAt, LocalDateTime createdAt) {
    this.id = UUID.randomUUID();
    ...
}

public boolean isUsableAt(LocalDateTime now) {
    return usedAt == null && expiresAt.isAfter(now);
}
```

**État** : ✅ `PasswordResetToken`, `IdentityUser.changePassword` ; ⚠️ `@Setter` de classe sur `IdentityUser`,
`InvitationToken`, `RefreshTokenSession`, `SigningKey`.

## Dire, ne pas demander

Le comportement vit à côté des données qu'il gouverne. On préfère `invitation.consume(now)` ou
`refreshSession.rotate(now)` à un service qui lit l'état puis modifie plusieurs champs de l'extérieur, dès que
l'invariant appartient à l'entité.

```java
// Cible : la règle « utilisable » et la consommation appartiennent à la session
RefreshTokenSession current = refreshTokens.findByTokenHash(hash(raw)).orElseThrow(InvalidGrantException::new);
current.rotate(clientId, now);  // refuse un autre client, un jeton expiré, révoqué ou déjà utilisé
```

**État** : ✅ `PasswordResetService.resetPassword` (`token.isUsableAt`, `token.consumeAt`) ; ⚠️
`OAuth2Service.refresh` et `revoke` (`setUsedAt`, `setRevokedAt`), `RegistrationService.claim` (`setUsedAt`).

## Immuabilité

- Requêtes, réponses et configuration sont des `record` ou des objets valeur immuables.
- Une frontière applicative ne renvoie ni collection interne modifiable ni entité.
- Une collection construite au démarrage est figée (`toUnmodifiableSet`, `Collections.unmodifiableMap`, comme les
  clients et les backends de `OAuth2Service`).

**État** : ✅ DTO en `record`, collections de `OAuth2Service` figées ; ⚠️ `IdentityProperties` est mutable.

## Polymorphisme

Quand un comportement varie selon le grant, le backend, la politique de jeton ou le fournisseur externe, et que chaque
variante a ses propres règles, on utilise une petite interface et une implémentation par variante plutôt qu'une
chaîne de conditions qui grandit.

**État** : ✅ `OAuth2Service.token` choisit entre deux grants par un `if` : acceptable à deux variantes. Un troisième
grant (ou des règles qui divergent) déclenche une Strategy par grant.

## Patterns

On utilise un pattern pour rendre explicite une variation ou une frontière **démontrée**, jamais par cérémonie :

- **Strategy** : politiques interchangeables (authentification, jeton, backend, provisioning). Cible pour les grants.
- **Factory** : création qui choisit entre plusieurs implémentations valides ou impose des règles de construction.
  Aucune aujourd'hui.
- **Adapter** : autour d'un client externe, pour que le service ignore le transport. ✅ `SmtpEmailClient` ;
  ⚠️ provisioning.
- **Repository** : la persistance, hors des controllers et des entités. ✅ Spring Data.
- **Handler d'exceptions** : traduction cohérente des erreurs en HTTP. ✅ `@ResponseStatus`
  ([api-design.md](api-design.md#erreurs)).

- **Le plus simple qui garde ces frontières** : pas d'interface, de fabrique ni de hiérarchie quand il n'existe qu'une
  implémentation stable et aucune variation. C'est pourquoi les services sont des classes concrètes.
- Une intégration externe (SMTP, provisioning d'une application comme hubscore) passe par un adapter : le service
  appelle `EmailClient.send(message)`, pas `JavaMailSender`.

**État** : ⚠️ `RegistrationService.provisionUser` appelle `RestClient` directement (URL, en-tête, codes HTTP) au lieu
d'un adapter de provisioning.
