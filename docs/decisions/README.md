# Décisions d'architecture (ADR)

**Visée** : garder la trace de **pourquoi** le projet est construit ainsi. Un ADR (Architecture Decision Record)
décrit :

- le contexte d'un choix structurant ;
- la décision prise et les options écartées ;
- ses conséquences.

On ne modifie pas un ADR accepté : on en écrit un nouveau qui le **remplace**, et on met à jour le statut de l'ancien.

**Quand écrire un ADR** :

- ajout ou retrait d'une librairie structurante (persistance, sécurité, jetons, tests…) ;
- changement du protocole exposé aux clients (nouveau grant, nouveau claim, format de jeton, gestion des clés) ;
- nouveau pattern imposé à tout le code, ou changement d'un pattern existant ;
- choix d'infrastructure (hébergement, base, CI, déploiement) ;
- toute décision qu'un nouvel arrivant pourrait vouloir remettre en cause sans en connaître le contexte.

Pas d'ADR pour un choix local à une feature ni pour un détail de style : ceux-là vont dans les conventions.

**Comment** :

1. Copier [0000-template.md](0000-template.md) en `NNNN-titre-en-kebab.md`, avec le numéro suivant dans `origin/dev`
   à jour. Si une autre PR prend ce numéro avant la tienne, renuméroter au rebase (voir
   [../process/pull-request.md](../process/pull-request.md)).
2. Statut `Proposé` dans la PR, puis `Accepté` au merge.
3. Ajouter la ligne au sommaire ci-dessous.

Les ADR 0002 à 0005 documentent après coup des choix déjà en place (0004 reprend les règles de l'ancien `AGENTS.md`) :
ils sont acceptés d'emblée.

## Sommaire

| N°                                          | Décision                                                                                              | Statut  |
| ------------------------------------------- | ----------------------------------------------------------------------------------------------------- | ------- |
| [0001](0001-structure-documentation.md)     | Documentation dans `docs/`, sommaires progressifs, 200 lignes visées, `CLAUDE.md` comme point d'entrée IA | Proposé |
| [0002](0002-serveur-oauth2-maison.md)       | Flux authorization code + PKCE codé dans l'application, jetons RS256 (JJWT), session Spring Security  | Accepté |
| [0003](0003-postgresql-flyway.md)           | PostgreSQL, schéma géré par Flyway, Hibernate en `validate`                                           | Accepté |
| [0004](0004-organisation-par-feature.md)    | Packages par feature, controllers minces, un service par controller, DTO `record`, temps en UTC       | Accepté |
| [0005](0005-inscription-sur-invitation.md)  | Inscription sur invitation liée à un client OAuth, provisioning poussé vers l'application aval         | Accepté |
| [0006](0006-workflow-feature.md)            | Workflow feature en quatre phases via le plugin `gouchon`, profil `.claude/workflow.md`, tests par tâche | Proposé |

_À lire si_ :

- **0001** : tu modifies la doc ou les instructions pour les agents ;
- **0002** : tu touches au protocole OAuth2 / OIDC, aux jetons, aux clés ou à la session ;
- **0003** : tu envisages une autre base, un autre outil de migration, ou tu modifies le schéma ;
- **0004** : tu crées un package, un controller, un service ou un DTO ;
- **0005** : tu touches à l'inscription, aux invitations ou au provisioning des applications ;
- **0006** : tu modifies le workflow `/gouchon:feature`, le profil ou la règle de test.
