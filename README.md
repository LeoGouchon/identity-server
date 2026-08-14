# Identity Server

The identity server is a central authentication service for applications. It provides an OIDC-inspired
authorization-code flow with PKCE. It is not a complete, generic OpenID Connect provider.

## Local configuration

The application listens on port `8080` by default (`PORT`). The supplied Docker Compose file publishes it as
`http://localhost:8081`.

### Start local Docker environment

The complete Spring configuration for the local Docker environment is kept in one external file. Create it from the
example, then edit that file to configure the database connection, OAuth clients, scopes, audiences, CORS, issuer, and
provisioning settings:

The `make up` command creates `config/application-dev.yml` from the example when needed. Review that file and adjust its
values before the first start.

Start the identity server and its PostgreSQL database from the `identity-server` directory:

```text
make up
```

The server is available at `http://localhost:8081`. Stop it with `make stop`. To remove the local PostgreSQL data volume
as well, use `make clean`.

Useful commands are `make logs`, `make ps`, `make test`, and `make check`.

`config/application-dev.yml` is ignored by Git and is mounted read-only into the container. It overrides the packaged
defaults in `src/main/resources`. The example file is therefore the only application configuration file that normally
needs to be edited for local Docker work.

Docker Compose still contains the PostgreSQL container's infrastructure settings (database name, database user,
password, port, and volume), because Compose cannot read arbitrary
`spring.*` values from a Spring YAML file. Keep those values aligned with the datasource values in
`config/application-dev.yml`.

The defaults used by the application are:

```text
IDENTITY_ISSUER=http://localhost:8081       # Docker Compose default
# In application.yml:
# identity.oauth-clients[0].client-id=default-web
# identity.oauth-clients[0].redirect-uris[0]=http://localhost:4200/auth/callback
# identity.oauth-clients[0].redirect-uris[1]=https://app.example.com/auth/callback
IDENTITY_ALLOWED_RESSOURCES_0=default-api
IDENTITY_SCOPES_0=openid
IDENTITY_SCOPES_1=profile
IDENTITY_SCOPES_2=email
IDENTITY_ACCESS_TOKEN_TTL_SECONDS=600
IDENTITY_REFRESH_TOKEN_TTL_DAYS=30
```

When both applications run directly on the host, run the identity server on a free port such as `8081`, set its `PORT`
and `IDENTITY_ISSUER` consistently, and leave the downstream API on its default `8080` port. When the identity server
runs in Docker, configure each OAuth client's `provisioning-url` so its container can reach the corresponding downstream
API.

OAuth clients are configured as a YAML list in `src/main/resources/application.yml`:

```yaml
identity:
  oauth-clients:
    - client-id: web-client
      display-name: Web application
      provisioning-url: http://localhost:8080/api/internal/identity-users
      provisioning-secret: change-me-web
      redirect-uris:
        - http://localhost:4200/auth/callback
    - client-id: admin-web
      redirect-uris:
        - http://localhost:5173/auth/callback
```

When configuring through environment variables, use indexed Spring Boot properties such as
`IDENTITY_OAUTH_CLIENTS_0_CLIENT_ID` and
`IDENTITY_OAUTH_CLIENTS_0_REDIRECT_URIS_0`. Additional clients use the next index, for example
`IDENTITY_OAUTH_CLIENTS_1_CLIENT_ID` and
`IDENTITY_OAUTH_CLIENTS_1_REDIRECT_URIS_0`.

`identity.allowed-ressources`, `identity.scopes`, and `identity.cors.allowed-origins` are YAML lists. When using
environment variables, use indexed properties such as `IDENTITY_ALLOWED_RESSOURCES_0`, `IDENTITY_SCOPES_0`, and
`IDENTITY_CORS_ALLOWED_ORIGINS_0`. The first configured resource is used when the authorization request omits `resource`.

Configure browser origins explicitly as a YAML list:

```yaml
identity:
  cors:
    allowed-origins:
      - http://localhost:4200
      - http://localhost:5173
```

## Connection flow

```text
Browser / Web
        |
        | 1. GET /.well-known/openid-configuration
        | 2. GET /oauth2/authorize (authorization code + PKCE)
        v
Identity server -- authenticates through Spring Security form login
        |
        | 3. Redirects to the registered callback with code + state
        |
        | 4. POST /oauth2/token with code + code_verifier
        v
Web receives access token, ID token, and refresh token
        |
        | 5. Authorization: Bearer <access token>
        v
Downstream API -- validates issuer and signature through /oauth2/jwks
             -- maps JWT sub to users.identity_user_id
```

## Discovery and endpoints

The client reads:

```http
GET {identity-issuer}/.well-known/openid-configuration
```

The discovery document advertises:

| Purpose             | Endpoint            |
|---------------------|---------------------|
| Authorization       | `/oauth2/authorize` |
| Token exchange      | `/oauth2/token`     |
| Public signing keys | `/oauth2/jwks`      |
| User information    | `/userinfo`         |
| Logout redirect     | `/connect/logout`   |
| Token revocation    | `/oauth2/revoke`    |

OAuth request parameters use the standard `snake_case` names such as `response_type`, `client_id`,
`redirect_uri`, `code_verifier`, and `grant_type`.

## Authorization-code login with PKCE

The client generates a random `state`, `nonce`, and `codeVerifier`, then derives the S256 `codeChallenge`. For the
default local client, an authorization request looks like:

```text
GET /oauth2/authorize?
  response_type=code&
  client_id=default-web&
  redirect_uri=http%3A%2F%2Flocalhost%3A4200%2Fauth%2Fcallback&
  scope=openid%20profile%20email&
  resource=default-api&
  state=<state>&
  nonce=<nonce>&
  code_challenge=<S256 challenge>&
  code_challenge_method=S256
```

The client ID and redirect URI must exactly match the configured `identity.oauth-clients` list. The user signs in
through Spring Security. The server then creates a one-time authorization code and redirects to:

```text
<redirect_uri>?code=<code>&state=<state>
```

The client must verify `state` and exchange the code:

```http
POST /oauth2/token
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code&client_id=default-web&code=<code>&redirect_uri=<same-uri>&code_verifier=<verifier>
```

Authorization codes expire after two minutes and are removed when exchanged. The redirect URI, client ID, and PKCE
verifier must match the original request. Client authentication is not implemented; PKCE protects this public-client
flow.

The identity server session is stored in an HTTP-only cookie. Consequently, multiple frontends can use the same issuer
and obtain single sign-on through the identity-server session, while each frontend keeps its own client ID, callback
URL, PKCE state, and tokens.

## Tokens

The token response contains:

```json
{
  "access_token": "<JWT>",
  "id_token": "<JWT>",
  "refresh_token": "<opaque token>",
  "token_type": "Bearer",
  "expires_in": 600,
  "scope": "openid profile email"
}
```

- Access tokens are RS256 JWTs whose audience is the requested backend. The configured default is `default-api`;
  applications should configure a meaningful audience for their API.
- ID tokens are RS256 JWTs whose audience is the frontend `clientId`. They contain the user email and the original
  `nonce` when one was supplied.
- The `sub` claim is the `identity_user.id` UUID and is the stable identity used by downstream applications.
- Access tokens expire after `IDENTITY_ACCESS_TOKEN_TTL_SECONDS` (600 seconds by default).
- Refresh tokens are opaque. Only their SHA-256 hashes are stored in `refresh_token_session`.

The client must send the access token to an API. The ID token is for the client application and must not be used as an
API credential.

## Resource-server integration

The downstream API is a stateless resource server. It obtains the public RSA key from:

```http
GET {identity-issuer}/oauth2/jwks
```

Configure the downstream API with:

```properties
identity.issuer=http://localhost:8081
identity.jwk-set-uri=http://localhost:8081/oauth2/jwks
identity.audience=<api-audience>
api.provisioning-secret=change-me-web
```

The corresponding environment variables are `IDENTITY_ISSUER`, `IDENTITY_JWK_SET_URI`, `IDENTITY_AUDIENCE`, and the
API's provisioning-secret variable.

The current downstream resource-server configuration validates the JWT issuer and signature, then maps `sub` to its
local user identity and applies the role stored by that application. Audience enforcement is application-specific and
must be enabled by each resource server if required.

## Multiple backends

Configure additional audiences, for example:

```text
IDENTITY_ALLOWED_RESSOURCES_0=application-api
IDENTITY_ALLOWED_RESSOURCES_1=analytics-api
IDENTITY_ALLOWED_RESSOURCES_2=admin-api
```

Clients select an audience with the `resource` parameter on `/oauth2/authorize`. The authorization code and
refresh-token session retain that audience. An unknown resource is rejected. If `resource` is omitted, the first
configured backend is selected.

## Password reset and SMTP email

The server exposes `POST /api/v1/password/forgot` with `{"email":"user@example.com"}`. It sends a single-use password
reset link through the configured SMTP server. The link expires after `IDENTITY_PASSWORD_RESET_TTL_MINUTES` (30 minutes
by default). The frontend then calls
`POST /api/v1/password/reset` with the email address, token, and new password.

Configure the SMTP connection and sender address in the identity-server environment:

```dotenv
SMTP_HOST=smtp.example.com
SMTP_PORT=587
SMTP_USERNAME=<smtp-username>
SMTP_PASSWORD=<smtp-password>
SMTP_AUTH=true
SMTP_STARTTLS_ENABLE=true
SMTP_SSL_ENABLE=false
SMTP_FROM=Identity Server <no-reply@example.com>
IDENTITY_PASSWORD_RESET_TTL_MINUTES=30
```

After a successful password change, a separate confirmation email is sent to the user.

## Registration and identity provisioning

Registration is invitation-based. The invitation is tied to one OAuth client/application:

1. An authorized internal caller creates an invitation:

   ```http
   POST /api/internal/invitations
   X-Identity-Provisioning-Secret: <shared-secret>
   Content-Type: application/json

   {"clientId": "web-client", "playerId": "<optional application user or player UUID>"}
   ```

2. The identity server creates a seven-day invitation token.
3. The user signs up:

   ```http
   POST /api/v1/signup
   Content-Type: application/json

   {"email": "user@example.com", "password": "<password>", "invitationToken": "<token>"}
   ```

4. The identity server validates the invitation, stores a BCrypt password hash, and creates `identity_user`.
5. The user then logs in through the identity server. The invitation remains pending until this authenticated step.
6. The identity server calls the `provisioning-url` configured on the invitation's OAuth client, normally the downstream
   application's endpoint:

   ```http
   POST /api/internal/identity-users
   X-Identity-Provisioning-Secret: <shared-secret>
   Content-Type: application/json

   {"identityUserId": "<identity UUID>", "email": "user@example.com", "playerId": "<optional UUID>"}
   ```

7. For an existing identity account, the invitation link keeps the invitation token through login and calls
   `POST /api/v1/invitations/{token}/claim`; the same application provisioning endpoint then links the local user.
8. The downstream application creates or updates only its own local user record and optionally links it to application
   data.

Each downstream application has its own `provisioning-secret` under its OAuth client configuration. The same value
authenticates invitation creation for that client and is sent to the downstream provisioning endpoint. Keep all
secrets private and replace the `change-me` defaults outside local development.

## Refresh, revocation, and logout

Refresh tokens are exchanged at `/oauth2/token`:

```http
POST /oauth2/token
Content-Type: application/x-www-form-urlencoded

grant_type=refresh_token&client_id=default-web&refresh_token=<refresh-token>
```

Refresh-token rotation is enforced: the current token is marked used and revoked, and a new token set is issued for the
same audience. Revoke a refresh token with:

```http
POST /oauth2/revoke
Content-Type: application/x-www-form-urlencoded

token=<refresh-token>
```

RP-initiated logout is available through:

```http
GET /connect/logout?client_id=<client-id>&post_logout_redirect_uri=<registered-uri>&state=<state>
```

The redirect URI must be registered for the client, the identity-provider HTTP session is invalidated, and `state` is
returned to the client. If no redirect URI is supplied, the server redirects to `/`.

The client should revoke its refresh token through `/oauth2/revoke` before redirecting to `/connect/logout`, then remove
all locally stored tokens. Access tokens are JWTs and cannot be invalidated server-side by this endpoint.

## Data model and development data

- `identity_user`: email, BCrypt password hash, enabled flag, and identity UUID.
- `invitation_token`: one-time invitation, optional application UUID, expiry, and consumption timestamp.
- `refresh_token_session`: hashed refresh token, client, scope, audience, expiry, and rotation/revocation state.
- `signing_key`: RSA key pair used to sign JWTs. A key is generated on first startup and reused from the database on
  later starts.

Flyway creates the schema from `src/main/resources/db/migration`. The development seed in
`src/main/resources/db/dev-data/V9999__seed_dev_data.sql` contains:

- `admin@gmail.com` / `admin`
- `user@gmail.com` / `user`
- `dev-invitation-token`, for local development

These credentials and tokens are for local development only.

## Implementation boundaries

- Frontend clients, scopes, backend audiences, CORS origins, token lifetimes, and provisioning settings are configurable
  through environment variables.
- The in-memory authorization-code store is local to one instance and is not suitable for a multi-instance deployment.
- The RSA private key is stored in the database and should be protected with database access controls and proper key
  management in production.
- HTTPS, secure cookie settings, rate limiting, email verification, client authentication, audience enforcement in
  downstream APIs, and full OIDC conformance still need to be addressed before exposing the service publicly.
