# Glossaire

Le vocabulaire de l'identity server, avec le terme français et son nom dans le code (anglais). C'est la référence pour
les briefs, l'API et les échanges : un terme défini ici ne s'emploie pas dans un autre sens. Les termes partagés avec
hubscore et kicker-app (invitation, joueur) gardent le même sens dans les trois dépôts.

## Fichiers clés

- `src/main/java/com/leogouchon/identityserver/*/` : entités, DTO et propriétés qui portent les noms du code.
- skill `gouchon:cadrer` (plugin `claude-workflow`) : la phase de cadrage lit ce glossaire et le complète.

## Règles

- **Un concept, un terme.** Les synonymes à ne pas employer sont listés sous _À éviter_.
- **Une ou deux phrases** par définition : ce que la chose **est**, pas comment elle est codée.
- **Domaine uniquement** : ici, les notions d'OAuth2 (jeton, client, audience) font partie du domaine ; les notions
  techniques générales (cache, pagination, transaction) n'y vont pas.
- **_À confirmer_** : le terme ou la définition a été déduit du code et attend une validation lors d'un prochain
  cadrage.

## Comptes et connexion

**Compte** (`IdentityUser`, table `identity_user`) :
L'identité d'une personne pour toutes les applications : un e-mail unique, un mot de passe, un prénom et un nom. Son
identifiant (UUID) est le `sub` des jetons. Un compte désactivé (`enabled = false`) ne peut plus se connecter.
_À éviter_ : utilisateur (c'est le compte **dans** une application, géré par celle-ci), membre.

**Connexion** (`/login`) :
La saisie de l'e-mail et du mot de passe dans le portail, qui ouvre une session sur le serveur.
_À éviter_ : login (dans un texte en français), authentification (plus large).

**Session** :
L'état de connexion gardé par le serveur dans un cookie HTTP-only. Tant qu'elle est ouverte, toutes les applications
obtiennent des jetons sans redemander le mot de passe (connexion unique, SSO).

**Déconnexion** (`/connect/logout`) :
La fermeture de la session, demandée par une application qui renvoie ensuite la personne vers sa propre page.

**Portail** (`frontend/`, paquet `auth-portal`) :
Les pages de connexion, d'inscription, de mot de passe oublié et d'invitation servies aux personnes. Ce n'est pas un
client OAuth : il poste directement le formulaire de connexion au serveur.
_À éviter_ : front (ambigu avec les applications clientes).

**Réinitialisation du mot de passe** (`PasswordResetToken`) :
Le changement de mot de passe sans connaître l'ancien, par un lien à usage unique envoyé par e-mail et valable
30 minutes par défaut.
_À éviter_ : récupération de compte.

## Applications et jetons

**Application** :
Un produit qui délègue la connexion à ce serveur, en général un front et son API (kicker-app et hubscore).

**Client** (`IdentityProperties.OAuthClient`, `clientId`) :
Le front d'une application tel que le serveur le connaît : un identifiant, ses adresses de retour autorisées, son nom
affiché, l'URL et le secret de provisioning de son application. Client public : il n'a pas de secret OAuth.
_À éviter_ : application (quand on parle de la configuration), front.

**Adresse de retour** (`redirect_uri`) :
L'URL du client où le serveur renvoie le code d'autorisation. Elle doit être identique, au caractère près, à l'une de
celles configurées.

**Backend** ou **ressource** (`identity.allowed-ressources`, paramètre `resource`) :
Une API qui accepte les jetons de ce serveur. Son nom devient l'**audience** (`aud`) des jetons d'accès émis pour elle.
_À éviter_ : service, serveur de ressources (dans un texte courant).

**Audience** (`aud`) :
Le destinataire d'un jeton : le backend pour un jeton d'accès, le client pour un ID token.

**Code d'autorisation** (`AuthorizationCode`) :
Une valeur à usage unique, valable 2 minutes, que le client échange contre des jetons en prouvant qu'il a lancé la
demande (PKCE).

**Jeton d'accès** (`access_token`) :
Le JWT que le client envoie à un backend pour appeler son API. Il porte le compte (`sub`), l'e-mail, le nom, le scope
et l'audience ; il expire après 10 minutes par défaut.
_À éviter_ : token (dans un texte en français), jeton tout court quand le type compte.

**ID token** (`id_token`) :
Le JWT destiné au client lui-même, pour afficher qui est connecté. Il ne sert jamais à appeler une API.

**Refresh token** et **session de refresh** (`RefreshTokenSession`) :
Une valeur opaque qui permet au client d'obtenir de nouveaux jetons sans reconnexion, valable 30 jours par défaut.
Le serveur n'en garde que l'empreinte (SHA-256), avec le client, le scope et l'audience d'origine.

**Rotation** :
Le remplacement du refresh token à chaque usage : l'ancien est marqué utilisé et révoqué, un nouveau est émis avec la
même audience.

**Révocation** (`/oauth2/revoke`) :
L'invalidation d'un refresh token par le client, avant la déconnexion.

**Clé de signature** (`SigningKey`) :
La paire de clés RSA qui signe les JWT. La clé publique est publiée (JWKS) pour que les backends vérifient les jetons.

**Émetteur** (`identity.issuer`, claim `iss`) :
L'URL publique de ce serveur, écrite dans chaque jeton. Elle doit être identique partout où on la configure.

## Inscription et invitations

**Invitation** (`InvitationToken`) :
Le droit, pour une personne, de rejoindre une application : un jeton à usage unique valable 7 jours, lié à un client
et éventuellement à un joueur. Elle est créée à la demande de l'application.
_À éviter_ : parrainage.

**Inscription** (`signup`) :
La création d'un compte à partir d'une invitation valide.
_À éviter_ : enregistrement.

**Réclamation d'une invitation** (`claim`) :
L'usage d'une invitation par une personne qui a déjà un compte : après connexion, le compte est provisionné dans
l'application et l'invitation est consommée.
_À confirmer_ : le terme français.

**Provisioning** (`provisionUser`) :
L'appel du serveur vers l'application (`provisioning-url`) pour qu'elle crée ou mette à jour son propre utilisateur
lié au compte.

**Secret de provisioning** (`provisioning-secret`, en-tête `X-Identity-Provisioning-Secret`) :
Le secret partagé entre ce serveur et une application. Il authentifie la création d'invitation par l'application et
l'appel de provisioning vers elle. Un secret par application.

**Joueur** (`playerId`) :
Notion de hubscore : la personne dont on enregistre les matchs. Ce serveur ne fait que transmettre son identifiant de
l'invitation au provisioning.
