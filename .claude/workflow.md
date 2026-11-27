# Profil du workflow feature

Lu par les skills du plugin `gouchon` (`/gouchon:feature` et ses phases, voir
[l'ADR 0006](../docs/decisions/0006-workflow-feature.md)). Le déroulé des phases est dans le plugin ; ce fichier dit
seulement ce qui est propre à l'identity server. En cas de conflit avec une skill, **ce profil et les conventions du
projet l'emportent**.

## Projet

- **Stack** : Spring Boot 3.5 (Java 21), Spring Web, Spring Security (form login, session, resource server JWT),
  Spring Data JPA, PostgreSQL 17, Flyway, JJWT, Thymeleaf + JavaMail, Lombok ; tests JUnit 5 et Mockito. Portail de
  connexion dans `frontend/` : React 19, Vite, antd 6, emotion, TanStack Query.
- **Hors du dépôt** :
    - **hubscore**, API aval : resource server qui valide nos jetons (émetteur, signature, `sub`) et expose
      `POST /api/internal/identity-users`, appelé par notre provisioning ; il crée aussi les invitations ;
    - **kicker-app** et les autres fronts, clients OAuth publics : un changement d'endpoint, de paramètre, de claim ou
      de redirection peut les casser.

## Preuves automatiques

- `./mvnw -B test` → `BUILD SUCCESS`, 0 échec, 0 erreur (même commande que la CI ; mocks uniquement, sans Docker).
- Si `frontend/` est touché : `npm --prefix frontend run typecheck` → exit 0, puis `npm --prefix frontend run build`
  → exit 0.

## Tests

- **Règle** : chaque tâche qui crée ou modifie un service, une règle de protocole ou une méthode d'entité qui porte un
  invariant contient son test unitaire (Mockito, sans Spring) ; chaque tâche qui crée ou modifie un endpoint ou une
  règle de `SecurityConfig` contient son test MockMvc (statut nominal, accès, CSRF, validation). Un correctif commence
  par un test qui reproduit le bug. Le test peut s'écrire avant ou après le code, dans la même tâche.
- **Où et comment** : [docs/conventions/testing.md](../docs/conventions/testing.md) (même package que la classe
  testée, noms en camelCase qui décrivent le comportement, valeurs du brief).

## Cadrage

Branches propres au projet :

- **Protocole** : endpoint OAuth2 / OIDC touché, paramètres, claims, redirections, codes d'erreur ; écart à la RFC
  assumé ou non ; clients touchés (kicker-app, autres fronts, hubscore).
- **Sécurité** : public, session (cookie), jeton bearer ou secret interne ; exclusion CSRF ; ce qui est stocké haché ;
  risque d'énumération des comptes ; données personnelles exposées.
- **Données** : nouvelle table ou colonne (migration Flyway), données existantes à migrer, purge des lignes expirées.
- **Configuration** : nouvelle propriété `identity.*` ou variable, valeur par défaut, valeur de production à poser sur
  le VPS avant le déploiement.

**Contrats externes** : le contrat de provisioning et la validation des jetons se lisent dans hubscore
(`IdentityProvisioningController`, `SecurityConfig`) s'il est présent à côté, et les appels des fronts dans kicker-app
(`src/`), mais c'est l'utilisateur qui valide un changement. Un contrat encore « à confirmer » bloque la validation du
brief : avant de conclure, l'utilisateur le confirme, ou il est noté « à adapter côté <projet> » avec le contrat
convenu.

## Plan

- **Ordre des tâches** : migration Flyway → entité (méthodes d'intention et leurs tests) et repository → service et son
  test → DTO `record` → controller, `SecurityConfig` et test MockMvc → portail (hook, page) → doc
  ([docs/process/new-feature.md](../docs/process/new-feature.md)). Recettes : `docs/process/add-an-endpoint.md`,
  `docs/process/add-a-migration.md`, `docs/process/add-an-oauth-client.md`.
- **Structure** : package par feature, controllers minces, un service par controller
  ([docs/conventions/project-structure.md](../docs/conventions/project-structure.md)).
- **Où chercher l'existant** : le package de la feature, `security/` (`TokenService`, `SecurityConfig`), `config/`
  (`IdentityProperties`), `email/`, `frontend/src/hooks/` et `frontend/src/api/constant.ts`.
- **Interdits à vérifier en auto-relecture** : controller qui accède à un repository, encode un mot de passe, crée un
  jeton ou modifie une entité ; DTO imbriqué dans un controller ou `Map` pour un contrat stable ; `@Setter` de classe
  sur une entité ; `now()` sans `ZoneOffset.UTC` ou `Clock.systemUTC()` ; jeton ou secret stocké en clair ; secret
  comparé avec `equals` ; secret, jeton ou mot de passe dans un log ; route ajoutée à `permitAll` ou exclue du CSRF
  sans raison écrite ; migration existante modifiée.

## Vérification en conditions réelles

Appels HTTP sur le serveur lancé en local, puis parcours dans le portail :

1. `make up` (PostgreSQL sur 5434, serveur sur **8081**). Les ports 5434, 8081 et 5180 sont partagés entre worktrees :
   si l'un est pris par un autre checkout, ne change pas de port (l'émetteur et les redirections en dépendent),
   demande à l'utilisateur d'arrêter l'autre (`make stop` dans ce checkout) ; sinon note la vérification « non
   vérifié ». Attendre `GET http://localhost:8081/.well-known/openid-configuration` → `200`.
2. **Endpoints publics** (discovery, JWKS, inscription, mot de passe) : les appeler avec `curl`, vérifier statut,
   corps et cas d'erreur du brief.
3. **Flux OAuth2** : avec un fichier de cookies `curl`, `POST /login` avec un compte du seed de dev
   (`user@gmail.com` / `user`), puis `GET /oauth2/authorize` (PKCE S256, client et `redirect_uri` de
   `config/config.yml`), puis `POST /oauth2/token`. Ces comptes ne servent qu'en local : ne jamais saisir d'autres
   identifiants ni fabriquer de jeton.
4. **Portail** (si `frontend/` est touché) : `make frontend` (port 5180) en arrière-plan, puis parcours dans le
   navigateur intégré d'Orca (skill `orca-cli`) ou avec Claude in Chrome ; thème clair et sombre, largeur mobile,
   états chargement et erreur. Sans navigateur pilotable, le parcours passe dans la checklist de l'utilisateur.
5. **Base** : migrations appliquées (`make logs`) et données écrites par la feature
   (`docker compose exec db psql -U <DB_USER> <DB_NAME>`).
6. **Clients** : un appel qui dépend de hubscore (provisioning) ou d'un front réel va dans la checklist de
   l'utilisateur, avec le résultat attendu.

## Points de revue

- **Protocole** : PKCE S256 obligatoire, `redirect_uri` comparé exactement, `state` renvoyé encodé, code à usage
  unique et court, audience choisie parmi `identity.allowed-ressources` et conservée à la rotation, refresh token
  tourné à chaque usage, claims compatibles avec les clients.
- **Secrets** : jetons stockés hachés, secrets comparés en temps constant, aucun secret ni jeton dans une réponse
  d'erreur ou un log, réponse identique qu'un compte existe ou non.
- **Spring Security** : chaque route nouvelle a une règle d'accès voulue ; exclusion CSRF justifiée ; CORS limité aux
  origines configurées ; cookies `secure` hors local.
- **Données** : transaction autour de chaque écriture multiple ; appel HTTP sortant dans une transaction assumé ;
  migration sans perte et compatible avec les données de production ; horodatages en UTC.
- **Objet** : controller mince ; entité modifiée par une méthode d'intention ; exception typée qui porte son statut ;
  interface seulement face à une variation réelle.
- **Conventions** : écarts listés dans [docs/tech-debt.md](../docs/tech-debt.md) non recopiés, nom pleinement
  qualifié, `ResponseStatusException` dans un nouveau service, `@Value` éparpillé, `System.out`, code commenté.
