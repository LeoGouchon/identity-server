# Développer une nouvelle feature

Le parcours complet, du besoin au merge dans `dev`. Chaque étape renvoie vers la doc qui détaille les règles.
Coche mentalement chaque case : une case sautée finit en dette.

## Avec Claude Code : `/gouchon:feature`

La skill `/gouchon:feature` (plugin `claude-workflow`, profil du projet dans `.claude/workflow.md`) déroule ce
parcours en quatre phases. Chacune produit un fichier dans `specs/<feature>/`, que tu valides avant la phase
suivante :

1. **Cadrer** (`/gouchon:cadrer`) : tours de questions jusqu'à ce que le besoin soit tranché (protocole, clients
   touchés, sécurité, données, configuration) ; glossaire et ADR mis à jour au passage → `1-brief.md`.
2. **Planifier** (`/gouchon:planifier`) : tâches vérifiables tirées du brief, chacune avec ses tests → `2-plan.md`.
3. **Implémenter** (`/gouchon:implementer`) : exécution tâche par tâche, changement minimal, `./mvnw -B test` à
   chaque tâche → `3-execution.md`.
4. **Vérifier** (`/gouchon:verifier`) : tests, critères d'acceptation, revue indépendante, flux réel sur `make up`
   (et le portail si touché), dette reportée → `4-verification.md`, avec la checklist à faire toi-même.

**Une session par phase** : quand une phase est validée et commitée, ferme la session (ou `/clear`) et relance
`/gouchon:feature` dans une nouvelle. Relancer `/gouchon:feature` reprend à la première phase non validée, y compris
depuis une autre machine après un `git push`. Fonctionnement détaillé : [specs/README.md](../../specs/README.md).
Pourquoi : [ADR 0006](../decisions/0006-workflow-feature.md).

Les sections suivantes restent la checklist de référence, avec ou sans l'agent.

## 1. Créer la branche

La branche vient en premier : le cadrage y commite déjà le brief, le glossaire et les ADR.

```bash
git fetch origin
git switch -c feat/<description-kebab> origin/dev
```

Dans un worktree Orca, renommer la branche `<utilisateur>/<nom>` avant de commencer
(`git branch -m feat/<description-kebab>`). Convention :
[../conventions/git-workflow.md](../conventions/git-workflow.md).

## 2. Cadrer

- [ ] Reformuler le besoin en une ou deux phrases : quel endpoint ou quel écran du portail, pour qui (navigateur
      anonyme, utilisateur connecté par session, porteur d'un jeton, application avec son secret de provisioning).
- [ ] **Protocole** : endpoint OAuth2 / OIDC touché ? Écart à la RFC assumé ou corrigé ? Un nouveau grant, claim ou
      format de jeton demande un ADR ([0002](../decisions/0002-serveur-oauth2-maison.md)).
- [ ] **Clients touchés** : kicker-app (client OAuth), hubscore (resource server, endpoint de provisioning), autres
      applications. Un claim, un paramètre ou le contrat de provisioning qui change est une rupture à coordonner.
- [ ] **Sécurité** : route publique, session ou jeton ; exclusion CSRF justifiée ; secret haché ou comparé en temps
      constant ; message générique quand une réponse pourrait révéler un compte.
- [ ] **Données** : nouvelle table ou colonne, données existantes à migrer, jetons à purger.
- [ ] **Configuration** : nouvelle propriété `identity.*` → valeur par défaut, `config/config.example.yml`, et
      configuration du VPS avant le déploiement.
- [ ] Repérer l'existant réutilisable : services de la feature, `email/`, `config/IdentityProperties`, tests voisins.
- [ ] Une décision structurante ? → ADR dans `docs/decisions/` (copier `0000-template.md`) **avant** de coder.
- [ ] Employer les termes de [../glossary.md](../glossary.md). Un nouveau terme y est ajouté.

## 3. Implémenter, dans cet ordre

1. **Migration** Flyway si le schéma change : [add-a-migration.md](add-a-migration.md).
2. **Entité et repository** : changement d'état par une méthode d'intention (`consumeAt(now)`), **avec son test** ;
   voir [../conventions/persistence.md](../conventions/persistence.md).
3. **Service** de la feature (classe concrète, une par controller) : règles, transaction, exceptions typées, temps en
   UTC ; **avec son test unitaire**.
4. **DTO** `record` de requête et de réponse dans `<feature>/dto/`, validés.
5. **Controller** mince et règle d'accès dans `SecurityConfig` ; **avec son test MockMvc**. Voir
   [add-an-endpoint.md](add-an-endpoint.md).
6. **Portail** (`frontend/`) si un écran change : constante dans `src/api/constant.ts`, hook TanStack Query dans
   `src/hooks/`, page et route. Voir [../conventions/frontend.md](../conventions/frontend.md).

Pendant l'implémentation, respecter [code-style.md](../conventions/code-style.md),
[project-structure.md](../conventions/project-structure.md), [object-design.md](../conventions/object-design.md),
[api-design.md](../conventions/api-design.md), [security.md](../conventions/security.md) et
[testing.md](../conventions/testing.md).

## 4. Vérifier

- [ ] `./mvnw -B test` passe (`BUILD SUCCESS`, 0 échec, 0 erreur).
- [ ] Portail touché : `npm --prefix frontend run typecheck` et `npm --prefix frontend run build` passent.
- [ ] Test manuel sur `make up` (et `make frontend` pour le portail) :
    - [ ] le serveur démarre (migrations appliquées, `ddl-auto: validate` sans erreur) ;
    - [ ] la discovery répond : `curl http://localhost:8081/.well-known/openid-configuration` ;
    - [ ] chaque endpoint touché répond comme le brief (cas nominal et erreurs) ;
    - [ ] route protégée : refusée sans session ni jeton ;
    - [ ] flux touché rejoué de bout en bout avec un compte du seed de dev (connexion, autorisation, échange du code,
          refresh, inscription sur `dev-invitation-token`) ;
    - [ ] écran du portail : clair et sombre, largeur mobile, états chargement, vide et erreur.
- [ ] Aucun controller qui accède à un repository, aucun secret ou jeton en clair dans un log ou une réponse, aucun
      `now()` sans fuseau.

## 5. Documenter

- [ ] Endpoint ajouté ou modifié → [../architecture/api.md](../architecture/api.md).
- [ ] Comportement OAuth2 changé → [../architecture/oauth2.md](../architecture/oauth2.md).
- [ ] Route publique, exclusion CSRF, CORS ou cookie changé →
      [../architecture/security.md](../architecture/security.md).
- [ ] Nouvelle convention ou convention modifiée → `docs/conventions/`.
- [ ] Nouvelle propriété de configuration → `config/config.example.yml` et
      [../getting-started.md](../getting-started.md).
- [ ] Nouveau terme ou sens précisé → [../glossary.md](../glossary.md).
- [ ] Dette repérée mais non traitée (dont les commentaires `shortcut:`) → [../tech-debt.md](../tech-debt.md) ; dette
      traitée au passage → la retirer.
- [ ] Chaque fichier touché reste sous 200 lignes (ou l'exception est assumée dans `.claude/doc-length-ignore.txt`),
      et chaque nouveau fichier est ajouté au sommaire de son dossier.

## 6. Proposer

- [ ] Commits au format Conventional Commits.
- [ ] PR vers `dev` avec le template rempli. Suivre [pull-request.md](pull-request.md).
- [ ] Avec Claude Code : faire relire la branche avant de demander une relecture humaine. Avec `/gouchon:feature`, la
      revue de la phase Vérifier en tient lieu ; sans, ou si du code a changé depuis, lancer `/code-review`.
