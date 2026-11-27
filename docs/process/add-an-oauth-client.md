# Brancher une application

Connecter un site (client OAuth) et son API (resource server) au serveur d'identité, du choix des valeurs au
dépannage. Le fonctionnement du protocole est décrit dans [../architecture/oauth2.md](../architecture/oauth2.md).

## Fichiers clés

- `config/config.example.yml` : modèle de configuration (`identity.oauth-clients`, `allowed-ressources`, CORS).
- `config/IdentityProperties.java` : forme de la configuration `identity.*`.
- `oauth2/OAuth2Service.java` : contrôle du client, du `redirect_uri` et de l'audience.
- `registration/RegistrationService.java` : invitations et appel de provisioning.

## Vue d'ensemble

Exemple fictif **Acme Recipes** (dans le projet, kicker-app est le site et hubscore l'API) :

| Composant          | Exemple                     | Rôle                                                      |
| ------------------ | --------------------------- | --------------------------------------------------------- |
| Site               | `https://recipes.acme.test` | application navigateur où l'utilisateur se connecte       |
| Serveur d'identité | `https://login.acme.test`   | comptes, mots de passe, session, jetons                   |
| API                | `https://api.acme.test`     | données protégées de l'application                        |
| `client_id`        | `acme-recipes-web`          | identifie le site auprès du serveur d'identité            |
| Audience           | `acme-recipes-api`          | identifie l'API à qui l'access token est destiné          |

```text
Navigateur -> serveur /oauth2/authorize            <- redirection vers le site avec un code à usage unique
Navigateur -> serveur /oauth2/token (code + PKCE)  <- access token, ID token, refresh token
Navigateur -> API, Authorization: Bearer <access token>
API        -> serveur /oauth2/jwks pour vérifier la signature
```

Le site envoie l'**access token** à l'API. L'ID token sert au site seul (affichage de la session) : ce n'est jamais un
identifiant d'API.

## 1. Choisir les valeurs partagées

```text
IDENTITY_ISSUER=https://login.acme.test
IDENTITY_JWK_SET_URI=https://login.acme.test/oauth2/jwks
IDENTITY_AUDIENCE=acme-recipes-api
PROVISIONING_SECRET=<long secret aléatoire, propre à cette API et à cet environnement>
```

- L'**issuer** est écrit dans le claim `iss` : il doit être **exactement** la même URL partout (jamais
  `http://localhost:8081` d'un côté et `https://login.acme.test` de l'autre).
- Le **secret de provisioning** n'est partagé qu'entre le serveur d'identité et cette API. Un secret différent par
  environnement et par API ; jamais dans le JavaScript du site.

## 2. Configurer le serveur d'identité

**En local**, dans `config/config.yml` (créé par `make config` depuis `config/config.example.yml`, ignoré par Git,
monté en lecture seule dans le conteneur) :

```yaml
identity:
  issuer: http://localhost:8081
  oauth-clients:
    - client-id: acme-recipes-web
      display-name: Acme Recipes            # nom affiché sur la page d'invitation
      provisioning-url: http://host.docker.internal:8080/api/internal/identity-users
      provisioning-secret: <secret>
      redirect-uris:
        - http://localhost:4200/auth/callback
  allowed-ressources:
    - acme-recipes-api
  cors:
    allowed-origins:
      - http://localhost:4200
```

**En déploiement**, les mêmes valeurs passent par des variables Spring indexées :

```dotenv
IDENTITY_ISSUER=https://login.acme.test
IDENTITY_CORS_ALLOWED_ORIGINS_0=https://recipes.acme.test
IDENTITY_OAUTH_CLIENTS_0_CLIENT_ID=acme-recipes-web
IDENTITY_OAUTH_CLIENTS_0_REDIRECT_URIS_0=https://recipes.acme.test/auth/callback
IDENTITY_OAUTH_CLIENTS_0_PROVISIONING_URL=https://api.acme.test/api/internal/identity-users
IDENTITY_OAUTH_CLIENTS_0_PROVISIONING_SECRET=<secret>
IDENTITY_ALLOWED_RESSOURCES_0=acme-recipes-api
IDENTITY_COOKIE_SECURE=true          # obligatoire hors du local, avec HTTPS
IDENTITY_COOKIE_SAME_SITE=LAX
```

Un client de plus prend l'index suivant (`IDENTITY_OAUTH_CLIENTS_1_CLIENT_ID`…), de même pour les listes
`REDIRECT_URIS_<n>`, `ALLOWED_RESSOURCES_<n>` et `CORS_ALLOWED_ORIGINS_<n>`.

- **`redirect_uri` exact** : la liste du client est une liste blanche stricte (schéma, port, chemin, `/` final).
  `https://recipes.acme.test/auth/callback/` est refusé si l'URI enregistrée n'a pas de `/` final.
- **Audience** : si le site n'envoie pas `resource`, le premier backend de `allowed-ressources` est choisi.
- **CORS** : chaque origine de navigateur qui appelle le serveur est déclarée explicitement.
- Au démarrage, le serveur refuse une configuration sans client, sans scope, sans backend, ou un client sans
  `redirect-uris` ou en double.

Vérifier ensuite la discovery : `GET https://login.acme.test/.well-known/openid-configuration` annonce
`/oauth2/authorize`, `/oauth2/token` et `/oauth2/jwks`.

## 3. Configurer l'API

L'API est un resource server **sans état** : elle ne voit jamais le mot de passe. Elle reçoit l'issuer, l'URL du JWKS,
son audience et le secret de provisioning (le nom des propriétés dépend de l'API : `identity.issuer`,
`identity.jwk-set-uri`, `identity.audience` dans hubscore). À chaque requête, elle :

1. lit le JWT de l'en-tête `Authorization: Bearer` ;
2. vérifie la signature avec les clés de `/oauth2/jwks` ;
3. vérifie que `iss` vaut l'issuer configuré, et **devrait** vérifier que `aud` contient son audience (le serveur ne
   le garantit pas pour elle) ;
4. retrouve son utilisateur local par le claim `sub` (`users.identity_user_id`) et applique **son** rôle.

## 4. Configurer le site

Le site lit la discovery plutôt que de coder chaque endpoint en dur. Ses réglages :

```text
issuer:                https://login.acme.test
client_id:             acme-recipes-web
redirect_uri:          https://recipes.acme.test/auth/callback
scope:                 openid profile email
resource:              acme-recipes-api
code_challenge_method: S256
```

1. **Connexion** : générer `state`, `nonce` et un `code_verifier` aléatoires, puis rediriger vers
   `/oauth2/authorize?response_type=code&client_id=…&redirect_uri=…&scope=…&resource=…&state=…&nonce=…`
   `&code_challenge=<S256(code_verifier)>&code_challenge_method=S256` (paramètres encodés).
2. **Callback** : vérifier `state`, puis `POST /oauth2/token` (`application/x-www-form-urlencoded`) avec
   `grant_type=authorization_code`, `client_id`, `code`, le même `redirect_uri` et `code_verifier`. Le code expire
   après 2 minutes et ne sert qu'une fois.
3. **Appels** : `Authorization: Bearer <access_token>` vers l'API.
4. **Refresh** : `grant_type=refresh_token` sur `/oauth2/token`. Le refresh token **tourne** : remplacer l'ancien par
   celui de chaque réponse, l'ancien est refusé ensuite.
5. **Déconnexion** : `POST /oauth2/revoke` (`token=<refresh token>`), puis rediriger vers
   `/connect/logout?client_id=…&post_logout_redirect_uri=<URI enregistrée>&state=…`, et effacer les jetons locaux.
   L'access token reste valide jusqu'à son expiration.

## 5. Invitations et provisioning

Le serveur possède l'identité ; l'API possède son utilisateur et son rôle
([ADR 0005](../decisions/0005-inscription-sur-invitation.md), détail dans
[../architecture/registration.md](../architecture/registration.md)).

- **Inviter** : l'API appelle `POST /api/internal/invitations` avec `X-Identity-Provisioning-Secret: <secret>` et
  `{"clientId": "acme-recipes-web", "playerId": "<UUID facultatif>"}`. La réponse contient `invitationUrl`, le lien à
  envoyer.
- **Être provisionnée** : à l'inscription (et au `claim` d'un compte existant), le serveur appelle le
  `provisioning-url` du client :

```http
POST https://api.acme.test/api/internal/identity-users
X-Identity-Provisioning-Secret: <secret>
Content-Type: application/json

{"identityUserId": "<UUID>", "email": "alex@example.test", "firstName": "Alex", "lastName": "Martin",
 "playerId": null}
```

L'API crée ou met à jour son utilisateur relié à `identityUserId`. Cet endpoint est privé (pas un endpoint de
navigateur) et protégé par le secret. Une réponse d'erreur fait échouer l'inscription (`502`).

## 6. En local avec Docker

| Service                  | URL sur la machine      |
| ------------------------ | ----------------------- |
| API (hubscore)           | `http://localhost:8080` |
| Serveur d'identité       | `http://localhost:8081` |
| Base du serveur          | `localhost:5434`        |
| Base de l'API (hubscore) | `localhost:5433`        |

- Le serveur tourne dans Docker et l'API sur la machine : `provisioning-url` en
  `http://host.docker.internal:8080/...`, jamais `localhost` (qui désigne le conteneur lui-même).
- L'API dans un conteneur joint le serveur par `host.docker.internal:8081`, mais l'issuer attendu reste celui écrit
  dans les jetons (`http://localhost:8081`). Sur un même réseau Docker, utiliser le nom du service pour le trafic, en
  gardant l'issuer public.
- Le plus simple : les deux applications Spring sur la machine, issuer `http://localhost:8081`. Avec deux projets
  Compose, vérifier la connectivité depuis chaque conteneur avant de tester la connexion.

## Dépannage

- [ ] `redirect_uri` identique à l'URI enregistrée : schéma, port, chemin, `/` final.
- [ ] `resource` présent et listé dans `allowed-ressources` (sinon `invalid_target`).
- [ ] Issuer de l'API identique au claim `iss` des jetons.
- [ ] L'API joint `/oauth2/jwks`.
- [ ] Le site envoie l'access token, pas l'ID token.
- [ ] Même secret de provisioning des deux côtés pour cette API.
- [ ] Origine du navigateur présente dans `cors.allowed-origins`.
- [ ] Appels conteneur → machine en `host.docker.internal`, pas `localhost`.
- [ ] Hors du local : HTTPS et `IDENTITY_COOKIE_SECURE=true`.
