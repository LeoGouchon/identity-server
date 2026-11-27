# Persistance

Les règles des entités, des repositories, des transactions et des migrations. Le schéma actuel est dans
[../architecture/persistence.md](../architecture/persistence.md) ; pourquoi PostgreSQL et Flyway :
[ADR 0003](../decisions/0003-postgresql-flyway.md).

## Fichiers clés

- `*/<Entité>.java`, `*/<Entité>Repository.java` : entités JPA et repositories Spring Data, dans leur feature.
- `src/main/resources/db/migration/` : migrations Flyway.
- `src/main/resources/db/dev-data/V9999__seed_dev_data.sql` : données de dev.
- `config/config.example.yml` : `spring.jpa.hibernate.ddl-auto: validate`, `open-in-view: false`, `spring.flyway.*`.

## Entités

- Le **constructeur** pose un état valide (identifiant, champs obligatoires, dates) ; le constructeur sans argument
  ne sert qu'à JPA.
- Une modification passe par une **méthode d'intention** qui vérifie la règle (`changePassword`, `consumeAt`,
  cible : `invitation.consume(now)`, `session.rotate(clientId, now)`, `session.revoke(now)`). Voir
  [object-design.md](object-design.md#dire-ne-pas-demander).
- Une méthode qui dépend du temps le reçoit en paramètre, en UTC ([code-style.md](code-style.md#le-temps)).
- Relations en `FetchType.LAZY` ; un chargement nécessaire se demande au repository (`@EntityGraph`, comme
  `RefreshTokenSessionRepository.findByTokenHash`).
- `@Getter` seulement, pas de `@Setter` de classe ni de `@Data`.
- Une entité ne sort jamais d'un service vers un controller : conversion en DTO dans le service.

**État** : ✅ `PasswordResetToken`, relation `LAZY` de `RefreshTokenSession`, `open-in-view: false` ; ⚠️ `@Setter` de
classe sur `IdentityUser`, `InvitationToken`, `RefreshTokenSession`, `SigningKey`.

## Repositories

- Une interface Spring Data par entité, dans le package de l'entité.
- Requêtes dérivées d'abord (`findByEmailIgnoreCase`), `@Query` JPQL si besoin, SQL natif en dernier recours.
- Seul un service appelle un repository : jamais un controller, jamais une entité.
- Une recherche par e-mail ignore la casse (`IgnoreCase`).

**État** : ✅.

## Transactions

- `@Transactional` de **Spring** (`org.springframework.transaction.annotation`), jamais celui de `jakarta`.
- Sur chaque méthode de service qui fait **plusieurs écritures**, ou une lecture puis une écriture qui doivent rester
  cohérentes (rotation d'un refresh token, consommation d'un jeton et changement de mot de passe).
- Un appel HTTP sortant ou un envoi d'e-mail dans une transaction se justifie : il garde la connexion ouverte et ne
  s'annule pas avec elle. L'inscription le fait exprès : un provisioning en échec annule la création du compte
  ([ADR 0005](../decisions/0005-inscription-sur-invitation.md)).

**État** : ✅ `signup`, `requestReset`, `resetPassword` ; ⚠️ `OAuth2Service.refresh` enregistre l'ancienne et la
nouvelle session sans transaction.

## Migrations Flyway

- Tout changement de schéma est une **nouvelle** migration `src/main/resources/db/migration/V<n>__<description>.sql`,
  description en snake_case anglais (`V6__add_invitation_client.sql`).
- **On ne modifie jamais une migration mergée** : Flyway refuse de démarrer (somme de contrôle). On en écrit une qui
  corrige.
- Une colonne obligatoire sur une table qui a des données s'ajoute en trois temps : nullable, remplie, puis
  `NOT NULL` (exemple : `V6`).
- Les index se nomment explicitement (`idx_<table>_<colonne>`), comme les nouvelles contraintes (`fk_…`, `uq_…`).
- L'entité reste alignée sur le schéma : Hibernate valide au démarrage (`ddl-auto: validate`).
- Recette pas à pas : [../process/add-a-migration.md](../process/add-a-migration.md).

**État** : ✅ ; les contraintes en ligne de `V1` et `V5` ne sont pas nommées (migrations mergées, on n'y touche
pas).

## Dates en UTC

Les colonnes de date sont des `TIMESTAMP` sans fuseau qui contiennent de l'**UTC** : le code écrit et compare avec
`LocalDateTime.now(ZoneOffset.UTC)`. Les valeurs par défaut SQL (`DEFAULT NOW()`) dépendent du fuseau de la base :
une date utile au métier est posée par le code, pas par la base.

**État** : ✅ dates posées par le code.

## Données de dev

- `db/dev-data/` contient les comptes de test (`admin@gmail.com`, `user@gmail.com`) et une invitation de dev. Il
  n'est chargé que si `spring.flyway.locations` l'inclut, **jamais** en production.
- Jamais de donnée de démonstration dans `db/migration/` : elle irait en production.

**État** : ⚠️ `config/config.example.yml` charge `db/dev-data` par défaut ; seed en migration versionnée `V9999`.
