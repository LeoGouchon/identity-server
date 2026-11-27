# 0006. Workflow feature en quatre phases avec le plugin `gouchon`

- **Statut** : Proposé
- **Date** : 2026-11-27

## Contexte

kicker-app et hubscore mènent leurs features avec un workflow en quatre phases : le besoin est construit avec le
développeur avant le code, le plan est écrit et validé, la vérification apporte des preuves, et chaque étape laisse un
fichier dans `specs/<feature>/` qui permet de reprendre après une coupure. Ce workflow vit dans un plugin Claude Code
privé, `gouchon` (dépôt `LeoGouchon/claude-workflow`), qui sert à tous les projets. L'identity server n'avait que les
règles d'`AGENTS.md`, sans process.

## Options envisagées

1. **Pas de workflow outillé** : la checklist `docs/process/new-feature.md` seule.
2. **Copier les skills** dans ce dépôt : elles divergeraient des autres projets.
3. **Le plugin `gouchon`**, avec un profil propre à l'identity server.

## Décision

Nous retenons l'option 3 :

- `.claude/settings.json` déclare le marketplace `claude-workflow` et active `gouchon@claude-workflow` ;
- `.claude/workflow.md` est le **profil** du projet : preuves automatiques (`./mvnw -B test`, et `typecheck` / `build`
  du portail s'il est touché), règle de test, branches de cadrage (protocole, sécurité, données, configuration, clients
  touchés), ordre des tâches, vérification par appels HTTP sur `make up` et parcours dans le portail, points de revue ;
- **règle de test** : chaque tâche qui touche un service ou une règle a son test unitaire, chaque endpoint son test
  MockMvc ; le test s'écrit dans la même tâche, avant ou après le code (pas de TDD imposé) ;
- les fichiers `specs/<feature>/` sont commités dans la branche de la feature et gardés après le merge ;
- **vérification en conditions réelles** : l'agent rejoue le flux OAuth2 en local avec les comptes du seed de dev,
  jamais avec d'autres identifiants.

## Conséquences

- **Positives** : même workflow que kicker-app et hubscore ; chaque PR arrive avec son besoin, son plan, ses tests et
  ses preuves.
- **Négatives / coûts** :
    - le plugin s'installe sur chaque machine (accès Git au dépôt privé requis) ;
    - quatre fichiers par feature, même petite ;
    - le premier test MockMvc demandera d'ajouter `spring-security-test`.
- **Suivi** : une évolution du workflow se fait dans le dépôt du plugin ; une évolution propre à l'identity server,
  dans le profil.
