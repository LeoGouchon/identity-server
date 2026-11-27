# OAuth2 et OIDC

Le flux authorization code + PKCE, le refresh token et les endpoints OIDC tels qu'ils sont codés. Ce n'est pas un
fournisseur OpenID Connect complet ([ADR 0002](../decisions/0002-serveur-oauth2-maison.md)).

## Fichiers clés

- `oauth2/TokenController.java` : les routes.
- `oauth2/OAuth2Service.java` : discovery, JWKS, codes, émission, rotation, révocation, logout.
- `security/TokenService.java` : clé RSA et construction des JWT.
- `token/RefreshTokenSession.java` : session de refresh en base.
- `config/IdentityProperties.java` : `oauth-clients`, `allowed-ressources`, `scopes`.

## Vue d'ensemble du flux

```text
Front (client public)
  1. GET  /.well-known/openid-configuration
  2. GET  /oauth2/authorize (code + PKCE)  ── pas de session ──► portail /login ─► POST /login
  3. redirection vers redirect_uri?code=<code>&state=<state>
  4. POST /oauth2/token (code + code_verifier) ─► access token, ID token, refresh token
API cliente
  5. reçoit Authorization: Bearer <access token>, valide la signature avec /oauth2/jwks
```

## Configuration au démarrage

Le constructeur d'`OAuth2Service` lit `IdentityProperties` et refuse de démarrer sans au moins un backend
(`allowed-ressources`), un scope et un client. Chaque client doit avoir un `client-id` unique et au moins une
`redirect-uri` ; les valeurs sont `trim()`. Le **backend par défaut** est le premier élément d'un `Set` non ordonné
(`toUnmodifiableSet`) : avec plusieurs backends, il n'est pas garanti que ce soit le premier de la liste.

## Discovery et JWKS

- `GET /.well-known/openid-configuration` : `issuer`, les URL des endpoints (bâties sur `identity.issuer`),
  `response_types_supported: [code]`, `grant_types_supported: [authorization_code, refresh_token]`,
  `code_challenge_methods_supported: [S256]`, `scopes_supported` (la configuration),
  `resource_indicators_supported: true`.
- `GET /oauth2/jwks` : une seule clé RSA (`kid` `identity-rsa-1`, `alg` RS256). La paire est générée au premier
  démarrage par `TokenService.initializeKey` (`@PostConstruct`), enregistrée dans `signing_key`, puis relue aux
  démarrages suivants.

## Authorize

`GET /oauth2/authorize` exige une session (sinon Spring Security redirige vers le portail, voir
[security.md](security.md)) :

```text
response_type=code & client_id=<client> & redirect_uri=<uri enregistrée> & scope=openid profile email
& resource=<backend> & state=<state> & nonce=<nonce> & code_challenge=<S256> & code_challenge_method=S256
```

- `response_type` doit valoir `code`, `redirect_uri` doit figurer **exactement** dans la liste du client (barre
  oblique finale comprise), `code_challenge_method` doit valoir `S256` : sinon `invalid_request`.
- `resource` absent ou vide → backend par défaut ; inconnu → `invalid_target`.
- `state`, `nonce` et `code_challenge` sont obligatoires ; `scope` vaut `openid` par défaut et n'est pas comparé aux
  scopes configurés.
- Le code est un UUID gardé **en mémoire** (`ConcurrentHashMap`) deux minutes, avec l'utilisateur, le client, l'URI,
  le scope, le nonce, le challenge et l'audience. Redirection : `redirect_uri + "?code=" + code + "&state=" + state`.

## Token

`POST /oauth2/token`, corps `application/x-www-form-urlencoded`, sans authentification du client :

```text
grant_type=authorization_code & client_id=<client> & code=<code> & redirect_uri=<même uri> & code_verifier=<v>
grant_type=refresh_token & client_id=<client> & refresh_token=<jeton>
```

- **authorization_code** : le code est retiré de la mémoire (usage unique, même en cas d'échec), puis on vérifie
  l'expiration, le client, l'URI et `base64url(SHA-256(code_verifier)) == code_challenge`. Sinon `invalid_grant`.
- **refresh_token** : la session est cherchée par le hachage du jeton ; elle doit appartenir au client, ne pas être
  révoquée, ni utilisée, ni expirée. Elle est alors marquée `used_at` et `revoked_at`, et une nouvelle session est
  créée avec **la même audience** et le même scope (rotation). Le nonce n'est pas repris.
- Autre `grant_type` : `unsupported_grant_type`.

Réponse :

```json
{"access_token": "<JWT>", "id_token": "<JWT>", "refresh_token": "<opaque>", "token_type": "Bearer",
 "expires_in": 600, "scope": "openid profile email"}
```

## Contenu des jetons

| Jeton         | Contenu                                                                                         | Durée                         |
| ------------- | ----------------------------------------------------------------------------------------------- | ----------------------------- |
| Access token  | `iss`, `sub` (UUID de `identity_user`), `aud` (le backend), `scope`, `email`, `email_verified`, `given_name`, `family_name` | `identity.access-token-ttl-seconds` (600 s) |
| ID token      | `iss`, `sub`, `aud` (le `client_id`), `email`, `email_verified`, `nonce`, `given_name`, `family_name` | même durée que l'access token |
| Refresh token | opaque (`UUID.UUID`), seul son SHA-256 (base64url) est stocké dans `refresh_token_session`      | `identity.refresh-token-ttl-days` (30 j) |

`email_verified` vaut toujours `true` ; `given_name` / `family_name` ne sont ajoutés que s'ils existent. Les JWT sont
signés RS256 avec l'en-tête `kid: identity-rsa-1` et des dates lues sur `Clock.systemUTC()`.

## Userinfo, révocation, logout

- `GET /userinfo` : accepte un bearer JWT (le `sub` du jeton) ou la session (e-mail de l'utilisateur connecté) ;
  renvoie `sub`, `email`, `email_verified` et les noms s'ils existent.
- `POST /oauth2/revoke` (`token=<refresh token>`) : marque la session `revoked_at` si elle existe ; rien sinon. Un
  access token ne peut pas être révoqué.
- `GET /connect/logout?client_id=&post_logout_redirect_uri=&state=` : sans URI, redirige vers `/` ; avec une URI,
  elle doit être enregistrée pour le client (`invalid_request` sinon) et `state` est ajouté encodé. La session HTTP
  est invalidée (`SecurityContextLogoutHandler`) avant la redirection.

Le client est censé révoquer son refresh token, puis appeler `/connect/logout`, puis effacer ses jetons locaux.

## Connexion unique

La session du serveur (cookie HTTP-only) est partagée par tous les clients : un utilisateur déjà connecté qui arrive
sur `/oauth2/authorize` depuis un autre front reçoit directement un code. Chaque front garde son `client_id`, son
`redirect_uri`, son PKCE et ses jetons.

Écarts connus (format des erreurs, `state` non encodé, scope non vérifié, rejeu, codes en mémoire) :
[../tech-debt.md](../tech-debt.md).
Intégrer une application : [../process/add-an-oauth-client.md](../process/add-an-oauth-client.md).
