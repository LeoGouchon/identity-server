# Architecture

**Visée** : décrire comment le code fonctionne **aujourd'hui** : flux, responsabilités et fichiers qui font foi.
Ce dossier ne fixe pas de règles (c'est le rôle de [conventions/](../conventions/README.md)). Il explique ce qui
existe, pour qu'on puisse le modifier sans le casser.

**Quand ajouter un fichier ici** : quand un mécanisme transverse (utilisé par plusieurs features ou par les
applications clientes) mérite une explication : un nouveau grant, une intégration externe, une tâche planifiée, un
nouveau type de jeton. Une feature isolée n'a pas sa place ici.

## Sommaire

- [overview.md](overview.md) : stack, packages par feature, couches, parcours d'une requête, gestion des erreurs.
  _À lire si_ : tu découvres le projet ou tu cherches où se trouve quelque chose.
- [oauth2.md](oauth2.md) : discovery, JWKS, authorize (PKCE), token, refresh, contenu des jetons, userinfo, revoke,
  logout, connexion unique.
  _À lire si_ : tu touches au protocole, aux jetons, à la clé de signature ou à un client OAuth.
- [security.md](security.md) : `SecurityConfig` (routes, CSRF, form login, session), resource server JWT, CORS,
  secrets de provisioning.
  _À lire si_ : tu ajoutes une route, changes un accès, la connexion, les cookies ou le CORS.
- [registration.md](registration.md) : invitations, inscription, `claim` et provisioning vers les applications.
  _À lire si_ : tu touches à la création de compte, aux invitations ou au contrat de provisioning.
- [password-reset.md](password-reset.md) : mot de passe oublié, réinitialisation, envoi des e-mails (SMTP,
  Thymeleaf).
  _À lire si_ : tu touches à la réinitialisation, à un e-mail ou à la configuration SMTP.
- [persistence.md](persistence.md) : tables, entités, migrations Flyway, seed de dev, horodatage UTC.
  _À lire si_ : tu ajoutes une table, une colonne, une requête ou une migration.
- [api.md](api.md) : liste de tous les endpoints avec leur accès, leurs paramètres et leur réponse, format d'erreur.
  _À lire si_ : tu ajoutes ou modifies un endpoint, ou tu cherches le contrat d'un appel.
- [frontend.md](frontend.md) : le portail `frontend/` (connexion, inscription, invitation, mot de passe oublié).
  _À lire si_ : tu modifies une page du portail ou un appel qu'il fait au serveur.
