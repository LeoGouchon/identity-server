# 0005. Inscription sur invitation et provisioning des applications

- **Statut** : Accepté
- **Date** : 2026-11-27 (documente un choix déjà en place)

## Contexte

Le serveur gère l'identité (e-mail, mot de passe) ; chaque application garde ses propres utilisateurs, rôles et
données (hubscore relie un compte à un joueur). Les applications ne sont pas ouvertes au public : un compte se crée à
l'invitation d'une application, qui doit ensuite connaître ce compte pour lui donner accès.

## Options envisagées

1. **Inscription libre** puis création du compte applicatif au premier appel authentifié : simple, mais n'importe qui
   peut créer un compte, et l'application ne peut pas relier le compte à ses données (un joueur) dès l'inscription.
2. **Invitation créée par l'application, provisioning poussé par le serveur d'identité** vers l'application.

## Décision

Nous retenons l'option 2 :

- **Invitation** : l'application appelle `POST /api/internal/invitations` avec son `clientId`, un `playerId` facultatif
  et l'en-tête `X-Identity-Provisioning-Secret`. L'invitation est liée à ce client OAuth et expire après 7 jours.
- **Secret par client** : `identity.oauth-clients[].provisioning-secret` authentifie la création d'invitation **et**
  l'appel de provisioning vers ce client. Il ne quitte jamais les serveurs.
- **Inscription** : `POST /api/v1/signup` crée le compte (mot de passe BCrypt) et appelle aussitôt le
  `provisioning-url` du client avec `identityUserId`, l'e-mail, le prénom, le nom et le `playerId`.
- **Compte existant** : après connexion, `POST /api/v1/invitations/{token}/claim` provisionne le compte dans
  l'application et consomme l'invitation.
- **Échec du provisioning** : l'inscription échoue (`502`) et la transaction annule la création du compte.

## Conséquences

- **Positives** : chaque application décide qui entre ; elle reçoit le compte avec le contexte voulu (`playerId`) ;
  le serveur d'identité ignore tout des rôles applicatifs.
- **Négatives / coûts** :
    - chaque application expose un endpoint interne de provisioning, au contrat fixé ici ;
    - l'invitation n'est consommée qu'au `claim` : à corriger (voir [../tech-debt.md](../tech-debt.md)) ;
    - un appel HTTP sortant a lieu dans la transaction d'inscription.
- **Suivi** : un changement du contrat de provisioning se coordonne avec hubscore (`IdentityProvisioningController`).
