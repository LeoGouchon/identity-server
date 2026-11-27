# 0002. Serveur OAuth2 codé dans l'application

- **Statut** : Accepté
- **Date** : 2026-11-27 (documente un choix déjà en place)

## Contexte

Plusieurs applications (kicker-app et son API hubscore, d'autres à venir) doivent partager les mêmes comptes et une
connexion unique. Chaque front est un client public (SPA) : il ne peut pas garder de secret. Chaque API doit valider
les jetons sans appeler le serveur à chaque requête, et un jeton doit viser une seule API.

## Options envisagées

1. **Fournisseur hébergé** (Auth0, Keycloak, Cognito) : complet, mais un service de plus à opérer ou à payer, et
   l'inscription sur invitation avec provisioning ([0005](0005-inscription-sur-invitation.md)) reste à coder.
2. **Spring Authorization Server** : conforme OIDC, mais lourd à configurer pour un besoin restreint (un seul grant
   utile, des clients publics).
3. **Flux codé dans l'application** : authorization code + PKCE (S256) et refresh token, sur Spring Security pour la
   connexion et la session, JJWT pour signer les jetons.

## Décision

Nous retenons l'option 3 :

- **Connexion** : formulaire de Spring Security (`POST /login`, posté par le portail `frontend/`), session dans un
  cookie HTTP-only. Cette session donne la connexion unique entre tous les clients.
- **Flux** : `/oauth2/authorize` (code + PKCE S256 obligatoire, `redirect_uri` comparé exactement à la liste du
  client), `/oauth2/token` (`authorization_code` et `refresh_token`), `/oauth2/revoke`, `/userinfo`,
  `/connect/logout`, discovery et JWKS. Aucun client n'est authentifié : PKCE protège l'échange du code.
- **Jetons** : access token et ID token JWT RS256 ; refresh token opaque, stocké haché (SHA-256) et tourné à chaque
  usage.
- **Audience** : le paramètre `resource` (RFC 8707) choisit l'API parmi `identity.allowed-ressources` ; la session de
  refresh garde cette audience.
- **Clé** : une paire RSA 2048 générée au premier démarrage et gardée en base (`signing_key`).
- **Périmètre** : ce n'est pas un fournisseur OpenID Connect complet ni générique.

## Conséquences

- **Positives** : peu de code, entièrement lisible et testé dans `OAuth2ServiceTest` ; un seul service à déployer.
- **Négatives / coûts** :
    - la conformité aux RFC est à notre charge (format des erreurs, scopes, rejeu de refresh token : voir
      [../tech-debt.md](../tech-debt.md)) ;
    - les codes d'autorisation sont en mémoire : une seule instance possible ;
    - la clé privée est en base, sans rotation ;
    - chaque API cliente doit valider l'émetteur, la signature et, si elle le souhaite, l'audience.
- **Suivi** : un changement de protocole (nouveau grant, nouveau claim, rotation de clé) passe par un nouvel ADR.
