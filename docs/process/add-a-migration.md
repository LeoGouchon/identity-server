# Ajouter une migration

Changer le schéma ou les données de la base avec Flyway, sans casser la production. Règles :
[../conventions/persistence.md](../conventions/persistence.md). Pourquoi Flyway :
[ADR 0003](../decisions/0003-postgresql-flyway.md).

## Fichiers clés

- `src/main/resources/db/migration/` : les migrations (`V1` à `V6` aujourd'hui).
- `src/main/resources/db/dev-data/V9999__seed_dev_data.sql` : données de dev (comptes et invitation de test).
- `config/config.example.yml` : `spring.flyway.*` (emplacements `db/migration` et `db/dev-data`) et
  `spring.jpa.hibernate.ddl-auto: validate`.
- `docker-compose.yml` : service `db` (PostgreSQL 17), publié sur le port **5434**, volume `dbdata`.

## Checklist

1. [ ] **Numéro** : `git fetch origin`, puis prendre le plus grand `V<n>` de `origin/dev` et créer
       `V<n+1>__<description_snake_case>.sql` (ex. `V7__add_last_login_to_identity_user.sql`). Si une autre PR prend
       ce numéro avant la tienne, renuméroter au rebase (le fichier n'est pas encore appliqué en production).
2. [ ] **SQL PostgreSQL** écrit pour les données existantes :
    - colonne obligatoire : l'ajouter nullable, la remplir (`UPDATE`), puis poser `NOT NULL` (exemple :
      `V6__add_invitation_client.sql`) ;
    - valeur par défaut d'une colonne existante : la choisir pour la production, pas pour le local (`V3` remplit
      `audience` avec `hubscore-api`) ;
    - renommage ou suppression : vérifier qu'aucun code de la version précédente ne la lit encore au moment du
      déploiement ;
    - suppression de données (comptes, sessions, clé de signature) : décidée dans le brief, jamais implicite.
3. [ ] **Contraintes et index** nommés explicitement (`fk_…`, `idx_…`, `chk_…`), comme `idx_invitation_token_client`.
4. [ ] **Secrets** : un jeton se stocke haché (`token_hash`), jamais en clair ; une colonne de secret est signalée
       dans le brief.
5. [ ] **Entité** alignée sur le schéma : Hibernate valide au démarrage (`ddl-auto: validate`) et refuse de démarrer
       si une colonne manque ou n'a pas le bon type.
6. [ ] **Seed de dev** mis à jour si une colonne obligatoire est ajoutée à `identity_user` ou `invitation_token`. Tant
       qu'il reste en `V9999` (dette), une base locale qui l'a chargé ignore la nouvelle migration et refuse de
       démarrer : `make rebuild`.
7. [ ] **Vérifier sur PostgreSQL** : `make up`, puis `make logs` (migration appliquée, démarrage sans erreur de
       validation). Pour regarder la base : `docker compose exec db psql -U <DB_USER> <DB_NAME>` (valeurs du `.env`).
8. [ ] **Doc** : table ou colonne nouvelle dans [../architecture/persistence.md](../architecture/persistence.md).

## Ce qu'il ne faut pas faire

- Modifier une migration déjà mergée dans `dev` : Flyway refuse de démarrer (somme de contrôle). On écrit une
  nouvelle migration qui corrige.
- Compter sur les tests pour valider le SQL : ils n'utilisent que des mocks, ni H2 ni Flyway ne tournent.
- Mettre des données de démonstration dans `db/migration/` : elles iraient en production.
- Toucher à `signing_key` sans plan : perdre la clé invalide tous les jetons émis et déconnecte toutes les
  applications.

## En cas de migration ratée en local

Flyway marque la migration en échec dans `flyway_schema_history`. En local, le plus simple est de corriger le fichier
(tant qu'il n'est pas mergé) et de repartir d'une base vide :

- `make rebuild` : supprime le volume PostgreSQL puis relance ; **toutes les données locales sont perdues** ;
- `make clean` : supprime conteneurs et volume sans relancer (même perte).

La clé de signature locale est régénérée au démarrage suivant : les jetons déjà émis par ce serveur local ne sont
plus valides, il faut se reconnecter dans les applications de dev.
