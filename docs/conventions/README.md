# Conventions

**Visée** : les règles à suivre pour **tout nouveau code** et pour le code qu'on modifie. Elles partent des
recommandations de Spring Boot et Spring Security, des RFC OAuth 2.0, et des règles d'architecture du projet
([ADR 0004](../decisions/0004-organisation-par-feature.md)), qui vivaient auparavant dans `AGENTS.md`.

Chaque fichier suit le même schéma :

- la règle et sa justification en une phrase ;
- un exemple, tiré du code quand il est conforme, sinon la cible ;
- l'**état dans le code** : ✅ appliqué, ou ⚠️ écart listé dans [../tech-debt.md](../tech-debt.md).

**En cas de conflit** : la convention l'emporte sur le code existant. On ne recopie pas un pattern listé en dette. Si
une règle gêne réellement, on la change ici (dans la même PR, avec un ADR si elle est structurante) plutôt que de la
contourner.

**Quand ajouter un fichier ici** : quand un nouveau domaine de règles apparaît (cache, tâches planifiées, nouvelle
intégration…). Une règle isolée va dans le fichier existant le plus proche.

## Sommaire

- [code-style.md](code-style.md) : style Java, injection, configuration, Lombok, nommage, imports, temps en UTC,
  exceptions, logs, langue.
  _À lire si_ : tu écris du code Java, quel qu'il soit.
- [project-structure.md](project-structure.md) : packages par feature, controllers minces, un service par
  controller, DTO, ressources, tests en miroir.
  _À lire si_ : tu crées un fichier, un package, un controller ou un DTO, ou tu te demandes où ranger du code.
- [object-design.md](object-design.md) : SOLID, encapsulation, « tell, don't ask », patterns (Strategy, Factory,
  Adapter, Repository) et quand ne pas les utiliser.
  _À lire si_ : tu crées une entité, un service, une interface ou une intégration externe.
- [api-design.md](api-design.md) : routes, paramètres OAuth2, DTO JSON, validation, statuts HTTP, erreurs,
  compatibilité avec les clients.
  _À lire si_ : tu crées ou modifies un endpoint, un DTO ou un claim de jeton.
- [security.md](security.md) : règles propres à un serveur d'identité (jetons hachés, secrets, mots de passe,
  énumération, `redirect_uri`, PKCE, audience, routes publiques, CSRF).
  _À lire si_ : tu touches à un jeton, un secret, un mot de passe, une route ou à `SecurityConfig`. **Toujours** en
  revue.
- [persistence.md](persistence.md) : entités, repositories, transactions, migrations Flyway, seed de dev, UTC.
  _À lire si_ : tu touches à une entité, une requête, une migration ou une écriture en base.
- [testing.md](testing.md) : ce qui doit être testé, tests unitaires de service, tests MockMvc, nommage.
  _À lire si_ : tu écris du code (chaque tâche de logique a son test) ou tu relis des tests.
- [frontend.md](frontend.md) : le portail `frontend/` (React, antd, TanStack Query), aligné sur kicker-app.
  _À lire si_ : tu touches à une page, un hook ou un style du portail.
- [git-workflow.md](git-workflow.md) : branches, Conventional Commits, PR vers `dev`, release vers `main`.
  _À lire si_ : tu crées une branche, un commit ou une PR.
