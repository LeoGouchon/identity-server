# Inscription, invitations et provisioning

Un compte se crée uniquement sur invitation d'une application, puis le serveur prévient cette application pour
qu'elle crée son propre utilisateur ([ADR 0005](../decisions/0005-inscription-sur-invitation.md)).

## Fichiers clés

- `registration/RegistrationController.java` : les quatre routes.
- `registration/RegistrationService.java` : invitation, inscription, `claim`, provisioning.
- `registration/dto/` : `InvitationRequest`, `InvitationResponse`, `SignupRequest`, `SignupResponse`.
- `invitation/InvitationToken.java`, `InvitationTokenRepository.java` : l'invitation en base.
- `user/IdentityUser.java` : le compte créé.
- `security/SecurityConfig.java` : redirection après connexion quand le formulaire porte une invitation.

## Séquence complète

```text
Application (ex. hubscore)
  1. POST /api/internal/invitations  (secret du client) ─► { token, invitationUrl, ... }
  2. envoie invitationUrl à la personne : <portail>/signup?invitationToken=<jeton>
Personne, dans le portail
  3. GET  /api/v1/invitations/{token}  ─► nom de l'application
  4a. Nouveau compte : POST /api/v1/signup ─► compte créé + provisioning immédiat
  4b. Compte existant : POST /login (champ invitation) ─► <portail>/invitation/complete
      ─► POST /api/v1/invitations/{token}/claim ─► provisioning + invitation consommée
  5. le portail renvoie vers applicationUrl
```

## Créer une invitation

`POST /api/internal/invitations`, en-tête `X-Identity-Provisioning-Secret`, corps `{"clientId": "...", "playerId":
"<UUID facultatif>"}`.

1. Le `clientId` doit être un client configuré (`400 Unknown application` sinon).
2. Le secret doit être égal au `provisioning-secret` de ce client (`401 Invalid secret`).
3. Le client doit avoir un `provisioning-url` (`400`).
4. Le jeton vaut deux UUID concaténés ; l'invitation est liée au client, au `playerId` et expire dans **7 jours**.

## Consulter une invitation

`GET /api/v1/invitations/{token}` (public) : `400 Invalid invitation` si le jeton est inconnu, `400 Invitation expired
or used` s'il est expiré ou consommé. Sinon :

```json
{"token": "...", "clientId": "kicker-client", "applicationName": "...",
 "invitationUrl": "<portail>/signup?invitationToken=...", "applicationUrl": "https://app.example/"}
```

- `applicationName` : `display-name` du client, ou son `client-id` à défaut.
- `invitationUrl` : `identity.auth-frontend-url` (sans `/` final) + `/signup?invitationToken=<jeton encodé>`.
- `applicationUrl` : schéma et hôte de la **première** `redirect-uri` du client, suivis de `/` ; `null` sans URI.

## S'inscrire

`POST /api/v1/signup` (public, transactionnel), corps `{"email", "password", "invitationToken", "firstName",
"lastName"}` :

1. invitation inconnue (`400`), expirée ou consommée (`400`) ;
2. e-mail déjà utilisé, sans tenir compte de la casse (`409 Email already exists`) ;
3. création de l'`IdentityUser` (UUID généré, mot de passe BCrypt, prénom et nom) ;
4. provisioning immédiat vers l'application de l'invitation ;
5. réponse `{"id": "<UUID>", "email": "..."}`.

L'invitation **n'est pas consommée** ici : elle le sera au `claim`. Aucune validation du format de l'e-mail ni de la
longueur du mot de passe (voir [../tech-debt.md](../tech-debt.md)).

## Réclamer une invitation (`claim`)

`POST /api/v1/invitations/{token}/claim`, authentifié (session ou bearer JWT) : vérifie l'invitation, retrouve
l'utilisateur connecté, le provisionne dans l'application, puis enregistre `used_at`. Réponse :
`InvitationResponse`. Le portail l'appelle depuis `/invitation/complete`, après la connexion.

## Provisioning sortant

`provisionUser` appelle le `provisioning-url` du client de l'invitation avec `RestClient` :

```http
POST <provisioning-url>
X-Identity-Provisioning-Secret: <provisioning-secret du client>
Content-Type: application/json

{"identityUserId": "<UUID>", "email": "...", "firstName": "...", "lastName": "...", "playerId": "<UUID ou null>"}
```

- Réponse `401` de l'application → `502 Bad Gateway` (« Downstream provisioning rejected the configured secret »).
- Toute autre erreur HTTP → `502` (« Downstream provisioning failed »).
- Une erreur réseau (`ResourceAccessException`) n'est pas attrapée et sort en `500`.
- Lors de l'inscription, l'appel a lieu dans la transaction : un échec annule la création du compte.

Côté hubscore, l'endpoint est `POST /api/internal/identity-users` (`IdentityProvisioningController`), qui crée ou
met à jour l'utilisateur et le relie au joueur.

## Configuration utilisée

| Propriété                                       | Rôle                                              |
| ----------------------------------------------- | ------------------------------------------------- |
| `identity.oauth-clients[].client-id`            | client auquel l'invitation est liée               |
| `identity.oauth-clients[].display-name`         | nom affiché dans le portail                       |
| `identity.oauth-clients[].provisioning-url`     | endpoint de provisioning de l'application         |
| `identity.oauth-clients[].provisioning-secret`  | secret reçu et envoyé                             |
| `identity.oauth-clients[].redirect-uris[0]`     | base de `applicationUrl`                          |
| `identity.auth-frontend-url`                    | base de `invitationUrl`                           |
