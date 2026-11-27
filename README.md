# Identity Server

Serveur d'authentification central des applications Gouchon : comptes, connexion unique (SSO) et jetons, via un flux
OAuth 2.0 authorization code + PKCE inspiré d'OpenID Connect. Ce n'est **pas** un fournisseur OpenID Connect complet
ni générique. Spring Boot 3 (Java 21), PostgreSQL et Flyway, avec un portail React (`frontend/`) pour la connexion,
l'inscription sur invitation et la réinitialisation du mot de passe. Ses clients sont notamment
[kicker-app](https://github.com/LeoGouchon/kicker-app) et son API hubscore.

## Démarrage rapide

```bash
make up                # crée config/config.yml si absent, puis PostgreSQL + serveur dans Docker
make frontend-install  # dépendances du portail (une fois)
make frontend          # portail sur http://localhost:5180
./mvnw -B test         # tests (mocks, sans Docker)
```

| URL                                                       | Contenu                                  |
| --------------------------------------------------------- | ---------------------------------------- |
| `http://localhost:8081/.well-known/openid-configuration`  | discovery : tous les endpoints du serveur |
| `http://localhost:5180/login`                             | page de connexion du portail             |
| `localhost:5434`                                          | base PostgreSQL locale                   |

| Commande     | Effet                                                         |
| ------------ | ------------------------------------------------------------- |
| `make logs`  | logs du serveur                                               |
| `make stop`  | arrête les conteneurs (garde les données)                     |
| `make test`  | tests dans Docker et résumé de couverture JaCoCo              |
| `make clean` | supprime les conteneurs **et** la base locale                 |

Comptes de dev (local uniquement) : `admin@gmail.com` / `admin` et `user@gmail.com` / `user`.

## Documentation

Toute la documentation est dans [`docs/`](docs/README.md) :

- [Démarrage et configuration](docs/getting-started.md)
- [Protocole OAuth2 : flux, jetons, refresh, déconnexion](docs/architecture/oauth2.md)
- [Brancher une application (site et API)](docs/process/add-an-oauth-client.md)
- [Décisions (ADR)](docs/decisions/README.md) et [dette technique](docs/tech-debt.md)

Les agents IA (Claude Code) démarrent par [`CLAUDE.md`](CLAUDE.md).
