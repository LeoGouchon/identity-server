# Démarrage

Installer le projet, le configurer et le lancer en local : le serveur et sa base dans Docker, le portail avec Vite.

## Fichiers clés

- `config/config.example.yml` : modèle de **toute** la configuration Spring, copié en `config/config.yml` (ignoré par
  Git) par `make config`. Il n'y a **aucun** `application.yml` dans `src/main/resources`.
- `docker-compose.yml` : services `db` (PostgreSQL 17, port **5434**), `backend` (le serveur, port **8081** → 8080
  dans le conteneur) et `tests` (Maven). `config/` est monté en lecture seule dans `backend`, lu grâce à
  `SPRING_CONFIG_NAME=config` et `SPRING_CONFIG_ADDITIONAL_LOCATION=file:/app/config/`.
- `.env` : `DB_NAME`, `DB_USER`, `DB_PASSWORD`, lus par Docker Compose pour créer la base **et** passés au conteneur
  `backend` (`env_file`), où la configuration Spring les reprend. Les deux restent donc alignés.
- `frontend/.env.example` : `VITE_IDENTITY_URL` (URL du serveur vue par le navigateur), à copier en `frontend/.env`.
- `Makefile`, `scripts/worktree-install.ps1` et `scripts/coverage-summary.ps1` : cibles `install`, `dev`, `test`,
  `clean`…

## Prérequis

- **Docker** pour lancer le serveur et PostgreSQL (`make up`, `make test`).
- **Java 21** pour lancer les tests sans Docker (`./mvnw -B test`). Maven n'est pas requis : le wrapper `mvnw` le
  télécharge.
- **Node.js** pour le portail (`frontend/`, Vite).
- **PowerShell** (Windows) : les cibles `install`, `config`, `clean` et `test` l'appellent, `install` et `frontend`
  appellent `npm.cmd`.

## Installation

```bash
make install   # config/config.yml et frontend/.env, npm ci du portail, image Docker (sans rien démarrer)
make dev       # démarre db + backend dans Docker, puis le portail sur http://localhost:5180 (premier plan)
```

`make install` copie les fichiers locaux ignorés par Git (`config/config.yml`, `frontend/.env`) depuis le checkout
principal, ou les crée depuis leur exemple. Relire `config/config.yml` avant le premier démarrage (clients OAuth,
CORS, SMTP). Au démarrage, Flyway applique les migrations de `db/migration` puis le seed de `db/dev-data` ; une paire
de clés RSA est générée et gardée en base si elle n'existe pas.

**Worktrees Orca** : `make install` sert de script de création et `make clean` de script d'archivage. Chaque worktree
a son propre projet Docker Compose, `identity-server-<worktree>` ; le checkout principal garde `identity-server`. Les
ports 5434, 8081 et 5180 restent partagés : un seul checkout peut faire tourner `make dev` à la fois.

Vérifier : `curl http://localhost:8081/.well-known/openid-configuration`.

## Commandes

| Commande                | Effet                                                                          |
| ----------------------- | ------------------------------------------------------------------------------ |
| `make install`          | fichiers locaux, `npm ci` du portail, `docker compose build`                    |
| `make config`           | copie `config/config.example.yml` en `config/config.yml` s'il n'existe pas      |
| `make up` / `make start`| `config`, puis `docker compose up -d --build`                                  |
| `make dev`              | `up` puis `frontend`                                                           |
| `make frontend`         | serveur Vite du portail, port 5180 strict (échoue si le port est pris)         |
| `make stop`             | `docker compose down` (garde les données)                                       |
| `make restart`          | `stop` puis `up`                                                               |
| `make build`            | reconstruit les images, puis `dev`                                             |
| `make rebuild`          | **supprime le volume de la base**, puis `up`                                    |
| `make clean`            | **supprime conteneurs, volume de la base**, image locale, `frontend/node_modules` et `dist` |
| `make logs` / `make ps` | logs du serveur / état des conteneurs                                          |
| `make check`            | valide `docker-compose.yml`                                                    |
| `make test`             | tests dans le conteneur Maven, puis résumé de couverture JaCoCo                |

`./mvnw -B test` lance la même suite sans Docker : c'est la commande de la CI. Les tests n'utilisent que des mocks
(aucune base ni configuration requise).

## Données de dev

`src/main/resources/db/dev-data/V9999__seed_dev_data.sql`, pour le **local uniquement** :

- comptes `admin@gmail.com` / `admin` et `user@gmail.com` / `user` (partagés avec le seed de hubscore) ;
- invitation `dev-invitation-token` pour le client `kicker-client`, valable 30 jours après le chargement du seed. Elle
  ne sert que si `config/config.yml` déclare ce client, avec son `provisioning-url` (l'exemple ne déclare que
  `auth-portal`).

Ils ne sont chargés que si `spring.flyway.locations` contient `classpath:db/dev-data` (c'est le cas de l'exemple :
voir [tech-debt.md](tech-debt.md) avant de copier l'exemple en production).

## Configuration

Les valeurs de `config/config.example.yml` se surchargent par variables d'environnement :

| Variable                              | Rôle                                          | Défaut                  |
| ------------------------------------- | --------------------------------------------- | ----------------------- |
| `IDENTITY_ISSUER`                     | émetteur des jetons (`iss`), URL publique     | `http://localhost:8081` |
| `IDENTITY_AUTH_FRONTEND_URL`          | URL du portail (connexion, liens envoyés)     | `http://localhost:5180` |
| `IDENTITY_ACCESS_TOKEN_TTL_SECONDS`   | durée de vie des access et ID tokens          | `600`                   |
| `IDENTITY_REFRESH_TOKEN_TTL_DAYS`     | durée de vie d'une session de refresh         | `30`                    |
| `IDENTITY_PASSWORD_RESET_TTL_MINUTES` | durée de vie d'un lien de réinitialisation    | `30`                    |
| `IDENTITY_COOKIE_SECURE`              | cookie `Secure` (`true` obligatoire en HTTPS) | `false`                 |
| `IDENTITY_COOKIE_SAME_SITE`           | attribut `SameSite` du cookie de session      | `LAX`                   |
| `SMTP_HOST`, `SMTP_PORT`              | serveur d'envoi des e-mails                   | `localhost`, `587`      |
| `SMTP_USERNAME`, `SMTP_PASSWORD`      | identifiants SMTP                             | vides                   |
| `SMTP_AUTH`, `SMTP_STARTTLS_ENABLE`   | options SMTP                                  | `true`, `true`          |
| `SMTP_SSL_ENABLE`, `SMTP_FROM`        | SSL direct, expéditeur                        | `false`, `no-reply@…`   |
| `DB_HOST`, `DB_NAME`, `DB_USER`       | PostgreSQL (`.env`, avec `DB_PASSWORD`)       | `db`, `identity`…       |
| `PORT`                                | port HTTP dans le conteneur                   | `8080`                  |

Le client OAuth unique de l'exemple se règle avec `IDENTITY_CLIENT_ID` (`auth-portal`), `IDENTITY_REDIRECT_URI`,
`IDENTITY_PROVISIONING_URL` et `IDENTITY_PROVISIONING_SECRET` ; l'audience et l'origine avec
`IDENTITY_ALLOWED_RESOURCE` et `IDENTITY_CORS_ALLOWED_ORIGIN`. Pour des listes complètes, utiliser les variables
indexées `IDENTITY_OAUTH_CLIENTS_<n>_*`, `IDENTITY_ALLOWED_RESSOURCES_<n>` et `IDENTITY_CORS_ALLOWED_ORIGINS_<n>`.

Ajouter un client OAuth, une audience ou une origine : [process/add-an-oauth-client.md](process/add-an-oauth-client.md).

Aucun serveur SMTP n'est fourni par `docker-compose.yml` : sans `SMTP_*` valides, l'envoi des e-mails de
réinitialisation échoue.

## Avec les autres projets

- **hubscore** (API, `http://localhost:8080`) : son `IDENTITY_ISSUER` vaut `http://localhost:8081`, et le serveur
  l'appelle pour le provisioning (`provisioning-url` en `http://host.docker.internal:8080/...` depuis le conteneur).
- **kicker-app** (site) : son client OAuth et son `redirect_uri` sont déclarés dans `config/config.yml`, son origine
  dans `identity.cors.allowed-origins`, et l'audience attendue par hubscore (`default-api` en local) dans
  `identity.allowed-ressources`.

Pas à pas complet : [process/add-an-oauth-client.md](process/add-an-oauth-client.md).

## Dépannage

- **Port déjà pris** (5434, 8081 ou 5180) : un autre worktree fait tourner le serveur. Ne pas changer de port (les
  clients connaissent l'issuer `http://localhost:8081`) : arrêter l'autre (`make stop` dans son dossier).
- **Le serveur refuse de démarrer après un `git pull`** qui apporte une migration : le seed `V9999` est déjà appliqué
  et Flyway ignore la migration plus ancienne. `make rebuild` (**supprime les données locales**).
- **`At least one identity ... must be configured`** : `config/config.yml` n'a pas de client, de scope ou de backend.
- **Connexion refusée depuis un site** : voir le dépannage de
  [process/add-an-oauth-client.md](process/add-an-oauth-client.md).
