# Authentication setup

This document explains how to connect a website, the identity server, and a backend API.
The example uses a fictional website called **Acme Recipes**:

| Component | Example URL | Purpose |
| --- | --- | --- |
| Website | `https://recipes.acme.test` | Browser application where the user logs in |
| Identity server | `https://login.acme.test` | Owns users, passwords, sessions, and tokens |
| Backend API | `https://api.acme.test` | Serves protected application data |
| Frontend client ID | `acme-recipes-web` | Identifies the website to the identity server |
| Backend audience | `acme-recipes-api` | Identifies which API an access token is for |

The login flow is OAuth 2.0 Authorization Code with PKCE:

```text
Browser -> identity server /oauth2/authorize
        <- redirect to website with a one-time code
Browser -> identity server /oauth2/token (code + PKCE verifier)
        <- access token, ID token, and refresh token
Browser -> backend API with Authorization: Bearer <access token>
Backend -> identity server /oauth2/jwks to validate the token signature
```

The website must send the **access token** to the backend. The ID token is for the website's
own login/session display and must not be used as an API credential.

## 1. Choose shared values

The identity server and backend must agree on these values:

```text
IDENTITY_ISSUER=https://login.acme.test
IDENTITY_JWK_SET_URI=https://login.acme.test/oauth2/jwks
IDENTITY_AUDIENCE=acme-recipes-api
BACKEND_PROVISIONING_SECRET=<long-random-secret>
```

`IDENTITY_ISSUER` is part of the JWT and must be exactly the same URL everywhere. Do not use
`http://localhost:8081` in one service and `https://login.acme.test` in another.

Generate a different provisioning secret for each environment. It is shared only between the
identity server and the backend; never put it in frontend JavaScript.

## 2. Configure the identity server

Set these environment variables for the identity-server process:

```dotenv
# Public URL that is written into token issuer claims.
IDENTITY_ISSUER=https://login.acme.test

# Exact browser origins allowed to call the identity server.
# Separate multiple origins with |.
IDENTITY_CORS_ALLOWED_ORIGINS=https://recipes.acme.test

# client-id=redirect-uri. Separate clients with ; and callback URLs with |.
IDENTITY_OAUTH_CLIENTS=acme-recipes-web=https://recipes.acme.test/auth/callback

# The audience/resource values that the identity server is allowed to issue.
IDENTITY_ALLOWED_BACKENDS=acme-recipes-api

# URL called after a user signs up, so the backend can create its local user record.
IDENTITY_PROVISIONING_URL=http://backend:8080/api/internal/identity-users
IDENTITY_PROVISIONING_SECRET=<same-long-random-secret-as-backend>

# Recommended outside local development.
IDENTITY_COOKIE_SECURE=true
IDENTITY_COOKIE_SAME_SITE=LAX
```

The value of `IDENTITY_OAUTH_CLIENTS` is an exact allow-list. For example, this callback is
different and will be rejected:

```text
https://recipes.acme.test/auth/callback/
```

The trailing slash does not match the registered callback without a trailing slash.

For a local website running at `http://localhost:4200`, register a local callback explicitly:

```dotenv
IDENTITY_CORS_ALLOWED_ORIGINS=http://localhost:4200
IDENTITY_OAUTH_CLIENTS=acme-recipes-web=http://localhost:4200/auth/callback
```

Start the identity server and check that its discovery document is available:

```http
GET https://login.acme.test/.well-known/openid-configuration
```

It should advertise `/oauth2/authorize`, `/oauth2/token`, and `/oauth2/jwks`.

## 3. Configure the backend API

Set these values in the backend environment:

```dotenv
IDENTITY_ISSUER=https://login.acme.test
IDENTITY_JWK_SET_URI=https://login.acme.test/oauth2/jwks
IDENTITY_AUDIENCE=acme-recipes-api
BACKEND_PROVISIONING_SECRET=<same-long-random-secret-as-identity-server>
```

The corresponding Spring properties are:

```properties
identity.issuer=${IDENTITY_ISSUER}
identity.jwk-set-uri=${IDENTITY_JWK_SET_URI}
identity.audience=${IDENTITY_AUDIENCE:acme-recipes-api}
backend.provisioning-secret=${BACKEND_PROVISIONING_SECRET}
```

For each API request, the backend:

1. Reads the bearer JWT from the `Authorization` header.
2. Downloads the public RSA key from `IDENTITY_JWK_SET_URI` and validates the signature.
3. Validates the token issuer against `IDENTITY_ISSUER`.
4. Uses the JWT `sub` value to find `users.identity_user_id`.
5. Applies the role stored in the backend database.

The backend is stateless and does not receive or store the user's password.

`IDENTITY_AUDIENCE` documents the API audience used by the identity server and selected by the
website's `resource` parameter. The current backend security configuration validates the issuer
and signature; it does not yet enforce the `aud` claim itself. Keep the audience configured and
use a separate audience per API so tokens are still issued for the intended resource.

## 4. Configure the Acme Recipes website

The website should use the discovery document rather than hard-coding every endpoint. Its
OAuth client settings are conceptually:

```text
issuer:                 https://login.acme.test
client_id:              acme-recipes-web
redirect_uri:           https://recipes.acme.test/auth/callback
scope:                  openid profile email
resource:               acme-recipes-api
code_challenge_method:  S256
```

When the user clicks **Log in**, the website generates a random `state`, `nonce`, and PKCE
`code_verifier`, then redirects to:

```text
https://login.acme.test/oauth2/authorize?
  response_type=code&
  client_id=acme-recipes-web&
  redirect_uri=https%3A%2F%2Frecipes.acme.test%2Fauth%2Fcallback&
  scope=openid%20profile%20email&
  resource=acme-recipes-api&
  state=<state>&
  nonce=<nonce>&
  code_challenge=<S256(code_verifier)>&
  code_challenge_method=S256
```

At `/auth/callback`, the website must verify `state` and exchange the one-time code:

```http
POST https://login.acme.test/oauth2/token
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code&
client_id=acme-recipes-web&
code=<code>&
redirect_uri=https%3A%2F%2Frecipes.acme.test%2Fauth%2Fcallback&
code_verifier=<original-code-verifier>
```

Then call the API with the returned access token:

```http
GET https://api.acme.test/api/v1/me
Authorization: Bearer <access_token>
```

Refresh tokens are exchanged at `/oauth2/token` with `grant_type=refresh_token`. The identity
server rotates refresh tokens, so the website must replace the old refresh token with the new
one returned by every refresh operation.

## 5. User provisioning and signup

The identity server owns the identity user. The backend owns the application user and role.
After signup, the identity server calls:

```http
POST https://api.acme.test/api/internal/identity-users
X-Identity-Provisioning-Secret: <shared-secret>
Content-Type: application/json

{
  "identityUserId": "8d7c2b4d-3c85-4a10-9bf2-123456789abc",
  "email": "alex@example.test",
  "playerId": null
}
```

The backend creates or updates the local `users` row and links it to `identityUserId`. Keep this
endpoint private and protect it with the provisioning secret; it is not a browser endpoint.

## 6. Local Docker setup

The Compose files in this repository expose these host ports:

| Service | Host URL |
| --- | --- |
| Backend API | `http://localhost:8080` |
| Identity server | `http://localhost:8081` |
| Identity-server database | `localhost:5434` |
| Backend database | `localhost:5433` |

For a local browser website at `http://localhost:4200`, use values like:

```dotenv
# identity-server/.env or Compose environment
IDENTITY_ISSUER=http://localhost:8081
IDENTITY_CORS_ALLOWED_ORIGINS=http://localhost:4200
IDENTITY_OAUTH_CLIENTS=acme-recipes-web=http://localhost:4200/auth/callback
IDENTITY_ALLOWED_BACKENDS=acme-recipes-api
IDENTITY_PROVISIONING_SECRET=replace-this-in-local-development

# When the identity server runs in Docker and the backend is exposed on the host:
IDENTITY_PROVISIONING_URL=http://host.docker.internal:8080/api/internal/identity-users
```

The backend container must also be able to reach the identity server. On Docker Desktop, use
`host.docker.internal` for that container-to-host connection, while keeping the issuer consistent
with the URL used in the JWTs. If both applications are placed on one Docker network, use the
identity-server service name for network traffic and configure the public issuer separately only
if the application supports that deployment topology.

For the least surprising local setup, run both Spring applications directly on the host and use
`http://localhost:8081` as the issuer. If using two separate Compose projects, verify connectivity
from inside each container before testing login.

## Troubleshooting checklist

- `redirect_uri` is an exact match, including scheme, port, path, and trailing slash.
- `resource` is present and appears in `IDENTITY_ALLOWED_BACKENDS`.
- The backend's issuer is identical to the JWT `iss` claim.
- The backend can reach `/oauth2/jwks`.
- The website sends the access token, not the ID token.
- `IDENTITY_PROVISIONING_SECRET` and `BACKEND_PROVISIONING_SECRET` are identical.
- A browser origin is present in `IDENTITY_CORS_ALLOWED_ORIGINS`.
- Docker-to-host calls use `host.docker.internal`, not `localhost`.
- HTTPS is enabled and `IDENTITY_COOKIE_SECURE=true` outside local development.
