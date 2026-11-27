## Résumé

<!-- Quoi et pourquoi, en 2-3 lignes. Lien vers l'issue, le besoin ou specs/<feature>/ s'il existe. -->

## Changements

-

## Protocole et clients

<!-- Endpoints, paramètres, claims, redirections, contrat de provisioning ou configuration `identity.*` ajoutés ou
modifiés. Signaler en premier tout changement qui casse kicker-app, hubscore ou un autre client, et toute propriété à
poser sur le VPS avant le déploiement. Supprimer la section si rien ne change. -->

## Plan de test

<!-- Ce que tu as vérifié, pour que le relecteur puisse le reproduire. -->

- [ ] `./mvnw -B test` passe
- [ ] Tests ajoutés : service (unitaire) et endpoint (MockMvc) pour chaque logique touchée
- [ ] `make up` : migrations appliquées, endpoints appelés (cas nominal et erreurs)
- [ ] Flux OAuth2 rejoué en local si le protocole change (login, authorize, token, refresh)
- [ ] Portail : `typecheck` et `build` passent, parcours vérifié (desktop, mobile, thème clair et sombre)

## Checklist

- [ ] Titre de PR au format Conventional Commits (`type(scope): description`), base `dev`
- [ ] Conventions respectées (`docs/conventions/`), aucun nouvel écart listé dans `docs/tech-debt.md`
- [ ] Aucun secret, jeton ou mot de passe en clair (code, logs, configuration d'exemple)
- [ ] Doc mise à jour : `docs/architecture/api.md` si un endpoint change, `docs/architecture/oauth2.md` si le protocole
      change, conventions, process (fichiers ≤ 200 lignes, sommaires à jour)
- [ ] ADR ajouté si la PR contient une décision structurante (`docs/decisions/`)
- [ ] Points de `docs/tech-debt.md` corrigés au passage retirés de la liste
