# Persistance

Le schéma PostgreSQL, les entités JPA qui le lisent, les migrations Flyway et les données de dev
([ADR 0003](../decisions/0003-postgresql-flyway.md)).

## Fichiers clés

- `src/main/resources/db/migration/` : migrations `V1` à `V6`.
- `src/main/resources/db/dev-data/V9999__seed_dev_data.sql` : comptes et invitation de dev.
- `user/IdentityUser.java`, `invitation/InvitationToken.java`, `token/RefreshTokenSession.java`,
  `password_reset/PasswordResetToken.java`, `security/SigningKey.java` : les entités.
- `*Repository.java` : interfaces Spring Data, requêtes dérivées uniquement.
- `config/config.example.yml` : `spring.datasource`, `spring.jpa`, `spring.flyway`.

## Configuration

```yaml
jpa:
  hibernate:
    ddl-auto: validate      # Hibernate vérifie le schéma au démarrage, ne le modifie jamais
  open-in-view: false
flyway:
  locations: classpath:db/migration,classpath:db/dev-data
```

La base est PostgreSQL 17 (service `db` de `docker-compose.yml`, port hôte `5434`). Il n'y a aucun `application.yml`
dans `src/main/resources` : tout vient du fichier monté depuis `config/`
([../getting-started.md](../getting-started.md)).

## Tables et entités

| Table                   | Entité                | Colonnes clés                                                                                   |
| ----------------------- | --------------------- | ----------------------------------------------------------------------------------------------- |
| `identity_user`         | `IdentityUser`        | `id` (UUID = `sub` des jetons), `email` unique (320), `first_name`, `last_name`, `password_hash`, `enabled`, `created_at` |
| `invitation_token`      | `InvitationToken`     | `token` unique (128), `client_id` (index), `player_id`, `expires_at`, `used_at`, `created_at`  |
| `refresh_token_session` | `RefreshTokenSession` | `token_hash` unique, `user_id` → `identity_user` (cascade), `client_id`, `scope` (1000), `audience`, `expires_at`, `used_at`, `revoked_at` |
| `password_reset_token`  | `PasswordResetToken`  | `user_id` → `identity_user` (cascade), `token_hash` unique (64), `expires_at`, `used_at`        |
| `signing_key`           | `SigningKey`          | `id`, `private_key`, `public_key` (base64), `created_at` (non mappé)                            |

- **Identifiants** : UUID partout. `IdentityUser` et `PasswordResetToken` les génèrent en Java (`UUID.randomUUID()`),
  `InvitationToken` et `RefreshTokenSession` par `@GeneratedValue`, `SigningKey` par `TokenService`.
- **Relations** : seule `RefreshTokenSession.user` est une relation JPA (`@ManyToOne(fetch = LAZY)`), chargée par
  `@EntityGraph(attributePaths = "user")` dans `findByTokenHash`. `PasswordResetToken.userId` est un simple UUID.
- **Modification** : `PasswordResetToken` (méthodes `isUsableAt`, `consumeAt`, pas de setter) et
  `IdentityUser.changePassword` portent leurs règles ; les autres entités ont un `@Setter` de classe et sont modifiées
  depuis les services (voir [../tech-debt.md](../tech-debt.md)).

## Repositories

| Repository                       | Requêtes                                          |
| -------------------------------- | ------------------------------------------------- |
| `IdentityUserRepository`         | `findByEmailIgnoreCase`                           |
| `InvitationTokenRepository`      | `findByToken`                                     |
| `RefreshTokenSessionRepository`  | `findByTokenHash` (avec l'utilisateur)            |
| `PasswordResetTokenRepository`   | `findByTokenHash`, `deleteByUserIdAndUsedAtIsNull` |
| `SigningKeyRepository`           | `findAll` (la première clé est utilisée)          |

## Horodatage

Les colonnes sont des `TIMESTAMP` sans fuseau. Le code écrit et compare en UTC :
`LocalDateTime.now(ZoneOffset.UTC)` pour `created_at`, les expirations et `used_at` / `revoked_at`. Les valeurs par
défaut SQL (`DEFAULT NOW()`) ne servent que pour les insertions faites hors de l'application (seed, `signing_key.
created_at`).

## Migrations

| Migration                              | Contenu                                                                                  |
| -------------------------------------- | ---------------------------------------------------------------------------------------- |
| `V1__create_identity_schema.sql`       | extension `pgcrypto`, tables `identity_user`, `invitation_token`, `refresh_token_session` |
| `V2__create_signing_key.sql`           | table `signing_key`                                                                      |
| `V3__add_refresh_token_audience.sql`   | colonne `audience` (`NOT NULL`, défaut `hubscore-api` pour les sessions existantes)      |
| `V4__add_user_name.sql`                | `first_name`, `last_name` sur `identity_user`                                            |
| `V5__create_password_reset_token.sql`  | table `password_reset_token`                                                             |
| `V6__add_invitation_client.sql`        | `client_id` sur `invitation_token` : ajouté nullable, rempli (`default-api`), puis `NOT NULL` et indexé |

Flyway les applique au démarrage ; Hibernate valide ensuite que les entités correspondent au schéma.

## Données de dev

`V9999__seed_dev_data.sql` est dans `db/dev-data/`, un emplacement Flyway séparé, mais l'exemple de configuration
l'active (voir [../tech-debt.md](../tech-debt.md)). Il crée :

- `admin@gmail.com` / `admin` et `user@gmail.com` / `user` (UUID fixes, partagés avec le seed de hubscore) ;
- l'invitation `dev-invitation-token` pour le client `kicker-client`, liée à un `playerId`, valable 30 jours **à
  partir de la création de la base**.

Numéroté `V9999`, il passe devant toute migration ajoutée ensuite : sur une base locale qui l'a chargé, une nouvelle
`V7` a une version inférieure à la dernière appliquée, Flyway ne l'applique pas (`outOfOrder` désactivé) et
l'application ne démarre pas. Il faut repartir d'une base vide (`make rebuild`, qui **supprime les données
locales**).

## Ce qui n'existe pas

Aucune purge des lignes expirées, aucune requête JPQL ou native, aucun test sur une vraie base : les tests simulent
les repositories avec Mockito.
