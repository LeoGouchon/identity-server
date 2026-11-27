# Release

Livrer en production le contenu de `dev`, via une PR `dev` → `main`. Le merge dans `main` déclenche la version, le
changelog, l'image Docker et le déploiement de l'API.

## Fichiers clés

- `.github/workflows/release.yml` : `npx semantic-release` à chaque push sur `main` (sauf commit `chore(release)`).
- `.releaserc` : version calculée depuis les commits, `pom.xml` mis à jour (`mvn versions:set`), `CHANGELOG.md`,
  commit `chore(release): <version>`, tag `v<version>`, release GitHub.
- `.github/workflows/composeAndDeploy.yml` : image `identity-server:latest` poussée sur Docker Hub, puis sur le VPS
  `docker compose pull` et `up -d` du service `identity-backend`, et appel de
  `https://api.auth.leogouchon.com/.well-known/openid-configuration`.
- `Dockerfile` : build Maven (tests sautés) puis image `eclipse-temurin:21`.

> La configuration de production (fichier `config.yml`, `.env`, `docker-compose.yml` du VPS) est sur le serveur, pas
> dans ce dépôt. Les secrets GitHub (`DOCKERHUB_*`, `VPS_*`, `RELEASE_TOKEN`) sont dans les réglages du dépôt.
>
> Le déploiement du portail (`frontend/`) n'est décrit nulle part dans le dépôt. _À confirmer_ : hébergement,
> déclenchement et variable `VITE_IDENTITY_URL` de production.

## Préparer

- [ ] `dev` contient uniquement des PR mergées et vérifiées, CI verte.
- [ ] Sur `dev` à jour : `./mvnw -B test` passe, ainsi que `npm --prefix frontend run build` si le portail a changé.
- [ ] Test rapide sur `make up` et `make frontend` : discovery, connexion avec un compte du seed, flux complet
      (autorisation, échange du code, refresh), mot de passe oublié, inscription sur invitation.
- [ ] **Migrations** de la release relues : elles s'appliqueront sur les données de production au démarrage.
- [ ] **Clé de signature** : la table `signing_key` doit survivre au déploiement. Sans elle, le serveur génère une
      nouvelle clé et **tous les jetons émis deviennent invalides** (déconnexion de toutes les applications). Une
      sauvegarde de la base de production existe avant toute migration risquée.
- [ ] **Configuration** : nouvelle propriété `identity.*`, nouveau client OAuth, `redirect_uri`, origine CORS ou
      audience ajoutés à la configuration du VPS **avant** le déploiement, sinon le serveur ne démarre pas ou refuse
      le client.
- [ ] **Ordre** : le serveur d'identité se déploie **avant** l'API ou le front qui dépendent d'un nouveau
      comportement (claim, endpoint, client). Un contrat cassé (claim retiré, contrat de provisioning changé) est
      coordonné avec les releases de hubscore et de kicker-app.

## Ouvrir la PR

- **Base et source** : base `main`, source `dev`.
- **Titre** : `chore(release): <AAAA-MM-JJ>` (la version, elle, est calculée par semantic-release).
- **Description** : la liste des PR incluses depuis la dernière release, regroupées par type (`feat`, `fix`, autres),
  avec les migrations, les changements de configuration et les clients à prévenir.

```bash
gh pr create --base main --head dev --title "chore(release): 2026-11-27"
```

## Merger

- **Sans squash** (« Rebase and merge » ou merge commit) : `main` doit rester un ancêtre de `dev`, et les messages
  Conventional Commits de chaque PR doivent arriver sur `main`, car semantic-release les lit pour calculer la version.
- Ne jamais committer directement sur `main`. Un correctif urgent passe par une branche `fix/…` depuis `dev`, puis par
  une release.

## Après le merge

- [ ] Les workflows `Release new version` et `CI/CD Backend Docker Deploy` sont verts ; l'appel final à la discovery
      répond.
- [ ] Le commit `chore(release): <version>` créé sur `main` est ramené dans `dev` (PR `main` → `dev`), pour que
      `pom.xml` et `CHANGELOG.md` restent alignés.
- [ ] Vérifier la production : connexion depuis kicker-app, refresh d'une session ouverte, appel à hubscore avec le
      nouveau jeton.
- [ ] En cas de régression : revert de la PR fautive sur `dev`, puis nouvelle release. Une migration déjà appliquée ne
      se défait pas par un revert : écrire une migration corrective.
