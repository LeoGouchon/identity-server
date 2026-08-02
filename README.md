# Identity Server

The identity server is the central authentication service for the applications in this repository. It is an OIDC-inspired implementation built for the current Hubscore web client; it is not a complete, generic OpenID Connect provider.

## Connection flow

```text
Browser / Web
        |
        | 1. Discover provider metadata
        | 2. Redirect to /oauth2/authorize (code + PKCE)
        v
Identity server  -- authenticates the user with Spring Security form login
        |
        | 3. Redirects to the registered callback with code + state
        |
        | 4. POST /oauth2/token with code + code_verifier
        v
Web receives access token, ID token and refresh token
        |
        | 5. API calls: Authorization: Bearer <access token>
        v
Backend API  -- validates issuer/signature through /oauth2/jwks
             -- maps JWT sub to users.identity_user_id
```

### 1. Discovery

The client reads:

```text
GET {identity-issuer}/.well-known/openid-configuration
```

The discovery document advertises these endpoints:

| Purpose | Endpoint |
| --- | --- |
| Authorization | `/oauth2/authorize` |
| Token exchange | `/oauth2/token` |
| Public signing keys | `/oauth2/jwks` |
| User information | `/userinfo` |
| Logout redirect | `/connect/logout` |
| Token revocation | `/oauth2/revoke` |

The default local issuer is `http://localhost:8081` (`IDENTITY_ISSUER`).

### 2. Authorization-code login with PKCE

The client creates a random `state`, `nonce`, and PKCE `code_verifier`, then derives the S256 `code_challenge`. It redirects the browser to `/oauth2/authorize` with:

```text
response_type=code
client_id=hubscore-web
redirect_uri=http://localhost:4200/auth/callback
scope=openid profile email hubscore.read
resource=hubscore-api
state=<state>
nonce=<nonce>
code_challenge=<S256 challenge>
code_challenge_method=S256
```

The server accepts the configured frontend clients and their callback URLs. The local default is the `hubscore-web` client with these callback URLs:

- `http://localhost:4200/auth/callback`
- `https://app.example.com/auth/callback`

The user is authenticated by Spring Security. On success, the server creates a short-lived, one-time authorization code and redirects to the callback:

```text
<redirect_uri>?code=<code>&state=<state>
```

The client must verify `state`, then exchange the code server-to-server or from the browser with:

```http
POST /oauth2/token
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code&client_id=hubscore-web&code=<code>&redirect_uri=<same-uri>&code_verifier=<verifier>
```

The code expires after two minutes and is removed when exchanged. The redirect URI, client ID, and PKCE verifier must match the original request.

### Single sign-on across frontends

All frontends must use the same identity-server issuer, for example `https://identity.example.com`. A frontend does not need to share its access token or local storage with another frontend:

1. Frontend A redirects the browser to the identity server and the user signs in.
2. The identity server stores the authenticated session in its HTTP-only session cookie.
3. Frontend B redirects the browser to the same `/oauth2/authorize` endpoint with its own `client_id`, callback URL, `state`, `nonce`, and PKCE challenge.
4. Because the browser already has an identity-server session, the identity server skips the login prompt and immediately returns an authorization code to Frontend B.
5. Frontend B exchanges that code and receives tokens for itself and its selected backend.

The browser only sends the session cookie to the identity-server origin during the redirect. The cookie does not need to be readable by the frontends. This is why the flow works across `app-one.example.com`, `app-two.example.com`, and `app-three.example` while keeping the cookie `HttpOnly`.

Register all frontend clients with `IDENTITY_OAUTH_CLIENTS`. Entries are separated by `;`, and multiple callback URLs for one client are separated by `|`:

```text
IDENTITY_OAUTH_CLIENTS=app-one-web=https://app-one.example.com/auth/callback;app-two-web=https://app-two.example.com/auth/callback;app-three-web=https://app-three.example.com/auth/callback
```

Each frontend must use its own `client_id` and an exact registered `redirect_uri`. The authorization code is bound to both values, preventing one frontend from redeeming another frontend's code.

### 3. Tokens

The token response contains:

```json
{
  "access_token": "<JWT>",
  "id_token": "<JWT>",
  "refresh_token": "<opaque token>",
  "token_type": "Bearer",
  "expires_in": 600,
  "scope": "openid profile email hubscore.read"
}
```

- The access token is an RS256 JWT for the requested backend audience. The default is `hubscore-api`.
- The ID token is an RS256 JWT for audience `hubscore-web` and includes the user email and the original `nonce`.
- The subject (`sub`) is the `identity_user.id` UUID. This is the stable identity shared with downstream applications.
- The default access-token lifetime is 10 minutes (`IDENTITY_ACCESS_TOKEN_TTL_SECONDS`).
- Refresh tokens are opaque values. Only their SHA-256 hashes are stored in `refresh_token_session`.

### 4. Hubscore API validation

Hubscore is a stateless resource server. It obtains the public RSA key from:

```text
GET {identity-issuer}/oauth2/jwks
```

For every bearer token it validates the signature and issuer, then uses `sub` to find `Users.identity_user_id`. The Hubscore user determines application-specific data such as role and player association; the identity server remains the owner of credentials and authentication state.

Configure Hubscore with matching values:

```properties
identity.issuer=http://localhost:8081
identity.jwk-set-uri=http://localhost:8081/oauth2/jwks
identity.audience=hubscore-api
```

The current Hubscore resource-server configuration validates the issuer and JWT signature. The intended audience is `hubscore-api`; clients should only send the access token to the Hubscore API, not the ID token.

### Multiple backends

Configure the allowed backend audiences with a comma-separated list:

```properties
identity.allowed-backends=hubscore-api,analytics-api,admin-api
```

The equivalent environment variable is:

```text
IDENTITY_ALLOWED_BACKENDS=hubscore-api,analytics-api,admin-api
```

Clients select a backend with the OAuth `resource` parameter on `/oauth2/authorize`. The authorization code binds that audience, and refresh-token rotation continues issuing tokens for the same audience. If `resource` is omitted, the first configured backend is used, preserving the default Hubscore flow. An unknown resource is rejected with `400 Invalid resource`.

The frontend and backend are separate concerns: a frontend client can request a token for any allowed backend, then call only that backend with the resulting access token. If one frontend needs to call several backends, it should request a separate authorization flow/token for each resource.

## Registration and identity provisioning

Registration is invitation-based:

1. Hubscore calls `POST /api/internal/invitations` with `X-Identity-Provisioning-Secret` and a `playerId`.
2. The identity server creates a seven-day invitation token.
3. The user submits `POST /api/v1/signup` with `email`, `password`, and `invitationToken`.
4. The identity server validates and consumes the invitation, stores a BCrypt password hash, and creates `identity_user`.
5. It calls Hubscore's `POST /api/internal/identity-users` with the identity user ID, email, and optional player ID.
6. Hubscore creates or updates its local `Users` record and links it to the invited player.

Both internal endpoints use the same `HUBSCORE_PROVISIONING_SECRET`, sent as `X-Identity-Provisioning-Secret`. Keep this value private and use a strong value outside local development.

## Refresh and logout

Refresh with:

```http
POST /oauth2/token
Content-Type: application/x-www-form-urlencoded

grant_type=refresh_token&client_id=hubscore-web&refresh_token=<refresh-token>
```

Refresh-token rotation is enforced: the old session is marked used and revoked, and a new access-token/ID-token/refresh-token set is issued. A refresh token can also be revoked with `POST /oauth2/revoke`.

`GET /connect/logout` currently performs a redirect to `post_logout_redirect_uri` (or `/`). It does not yet provide a full OIDC logout session implementation.

## Data model

- `identity_user`: email, BCrypt password hash, enabled flag, and identity UUID.
- `invitation_token`: one-time signup invitation, optional Hubscore player UUID, expiry, and consumption timestamp.
- `refresh_token_session`: hashed refresh token, client, scope, expiry, and rotation/revocation state.
- `signing_key`: RSA key pair used to sign JWTs. The key is generated on first startup and reused from the database on later starts.

Flyway creates the schema from `src/main/resources/db/migration`. The development seed in `src/main/resources/db/dev-data/V9999__seed_dev_data.sql` contains:

- `admin@gmail.com` / `admin`
- `user@gmail.com` / `user`
- `dev-invitation-token`, linked to the seeded Hubscore player from [`hubscore`'s development seed](../hubscore/src/main/resources/db/dev-data/V9999__seed_dev_data.sql)

These credentials and tokens are for local development only.

## Important implementation boundaries

- Backend audiences and frontend clients are configurable through environment variables.
- Client authentication is not implemented for the token endpoint; PKCE protects the authorization-code exchange.
- The in-memory authorization-code store is local to one instance and is not suitable for a multi-instance deployment.
- The RSA private key is stored in the database and should be protected with database access controls and a proper secret/key-management strategy in production.
- HTTPS, secure cookie settings, rate limiting, email verification, and a complete OIDC conformance surface still need to be addressed before exposing this service publicly.
