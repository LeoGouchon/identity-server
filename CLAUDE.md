# CLAUDE.md

Identity server : serveur d'authentification Spring Boot 3.5 (Java 21) partagé par les applications Gouchon. Il gère
les comptes, la connexion unique (session Spring Security) et un flux OAuth 2.0 authorization code + PKCE qui émet des
jetons JWT RS256 ; il crée les comptes sur invitation et les provisionne dans l'application aval (hubscore). Base
PostgreSQL migrée par Flyway. Portail de connexion React dans `frontend/`. Déploiement Docker sur un VPS.

## Avant de coder

La documentation du projet est dans `docs/`. Son sommaire est importé ci-dessous :

@docs/README.md

1. Lis le sommaire ci-dessus, puis ouvre **uniquement** le `README.md` du ou des dossiers utiles à ta tâche.
2. Dans ce dossier, charge seulement les fichiers dont la ligne « À lire si » correspond à ce que tu fais.
3. Pour une nouvelle feature, une évolution significative ou un correctif, utilise `/gouchon:feature` (plugin
   `claude-workflow`, profil du projet dans `.claude/workflow.md`) : cadrer, planifier, implémenter, vérifier, avec un
   fichier par phase dans `specs/<feature>/` (voir `specs/README.md`). La checklist de référence reste
   `docs/process/new-feature.md`.
4. Si le code existant contredit une convention, **la convention l'emporte** pour le nouveau code. L'écart est
   probablement déjà listé dans `docs/tech-debt.md` ; ajoute-le s'il n'y est pas.

## Commandes

```bash
./mvnw -B test                           # toute la suite (mocks, sans Docker) : à lancer avant toute PR
make up                                  # crée config/config.yml si absent, démarre PostgreSQL + serveur sur 8081
make frontend                            # portail Vite sur http://localhost:5180
make logs                                # logs du serveur
make test                                # tests dans Docker + résumé de couverture JaCoCo
npm --prefix frontend run typecheck      # vérification TypeScript du portail
npm --prefix frontend run build          # build du portail
```

## Règles non négociables

- **Package par feature** : `oauth2/`, `registration/`, `password_reset/`, `invitation/`, `token/`, `user/`,
  `security/`, `email/`, `config/`. Controller, service, DTO, entités et repositories d'une feature restent ensemble.
- **Controllers minces** : ils lient la route, les paramètres, les en-têtes et le corps, délèguent à **leur** service
  (`TokenController` → `OAuth2Service`) et renvoient son résultat ou une redirection. Jamais de repository, d'encodage
  de mot de passe, de création de jeton, de contrôle de droits ni de modification d'entité dans un controller.
- **Erreurs** : le service lève une exception typée qui porte son statut (`@ResponseStatus`) ou que traduit un handler
  centralisé. Le controller n'attrape pas une exception métier pour refaire le travail du service.
- **DTO** : `record` autonomes dans `<feature>/dto/`, nommés `SignupRequest`, `SignupResponse`… Jamais de `record`
  imbriqué dans un controller, ni de `Map` pour un contrat stable.
- **Objet** : entités qui protègent leurs invariants par des méthodes d'intention (`token.consumeAt(now)`), pas de
  `@Setter` de classe ; injection par constructeur ; configuration et DTO immuables. Interface, fabrique ou héritage
  seulement face à une variation réelle (Strategy, Adapter autour d'un client externe).
- **Temps** : jamais d'horloge implicite. `LocalDateTime.now(ZoneOffset.UTC)` pour la base et les expirations,
  `Instant.now(Clock.systemUTC())` pour les JWT. Tout en UTC.
- **OAuth2** : routes dans `oauth2.TokenController`, comportement dans `oauth2.OAuth2Service`. L'audience d'un access
  token est choisie parmi `identity.allowed-ressources` ; la rotation du refresh token conserve l'audience d'origine.
- **Secrets** : jetons stockés hachés, secrets comparés en temps constant, rien de secret dans un log, une réponse
  d'erreur ou le portail. Toute route nouvelle a une règle explicite dans `SecurityConfig`.
- **Base de données** : tout changement de schéma passe par une nouvelle migration Flyway `V<n>__<description>.sql`.
  On ne modifie jamais une migration déjà mergée.
- **Tests** : chaque service ou règle créé ou modifié a un test unitaire (JUnit 5, Mockito), chaque endpoint créé ou
  modifié un test MockMvc (statut, accès, CSRF). `./mvnw -B test` après tout refactor d'architecture. Détail dans
  `docs/conventions/testing.md`.
- **Portail** : mêmes conventions que kicker-app (`docs/conventions/frontend.md`) ; la connexion reste un POST de
  formulaire natif vers `/login`.
- **Langue** : code, commentaires, messages d'erreur de l'API et commits en anglais ; textes du portail, e-mails et
  documentation en français.
- **Git** : branche `<type>/<kebab>` depuis `dev`, Conventional Commits, PR vers `dev`. Ne jamais pousser sur `main`.

## Règles de la documentation

- **200 lignes visées** par fichier `.md` de `docs/` et pour ce `CLAUDE.md`. Au-delà, le hook du plugin `gouchon` le
  signale. Tu **proposes** alors le découpage à l'utilisateur avec `AskUserQuestion` : « Découper maintenant », « Pas
  maintenant » ou « Ne plus demander pour ce fichier ». Tu ne découpes jamais sans son accord. Les exceptions
  assumées sont listées dans `.claude/doc-length-ignore.txt`.
- **Découpage** : `x.md` devient un dossier `x/` avec un `README.md` (visée et sommaire) et des sous-fichiers.
  Le sommaire parent pointe ensuite vers `x/README.md`.
- **Sommaires** : chaque dossier de `docs/` a un `README.md`. Chaque entrée de sommaire donne un lien, une
  description et une ligne « À lire si ». Un nouveau fichier doit y être ajouté.
- **Mise à jour** : une PR qui change une convention, un process, l'architecture, un endpoint ou le protocole met à
  jour la doc dans la même PR. Une décision structurante donne lieu à un ADR (`docs/decisions/`).
