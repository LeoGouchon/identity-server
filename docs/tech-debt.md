# Dette technique

Écarts connus entre le code et les [conventions](conventions/README.md), bugs repérés et code mort. Relevé du
2026-11-27, par lecture du code (les points « à confirmer » n'ont pas encore été reproduits).

**Règles** :

- on ne recopie jamais un pattern listé ici ;
- une PR qui corrige un point le retire de la liste ;
- une PR qui découvre un écart sans le corriger l'ajoute ;
- les chemins Java sont relatifs à `src/main/java/com/leogouchon/identityserver/` ;
- les correctifs de code se font dans des PR `fix` ou `refactor` dédiées, pas dans une PR de doc.

**Priorités** : **P1** = bug ou risque (sécurité, données), **P2** = écart de convention qui se propage, **P3** =
nettoyage.

## P1 : bugs et risques

| Sujet | Où | Action |
| --- | --- | --- |
| Invitation non consommée à l'inscription : tant qu'elle n'est pas réclamée (`claim`), le même lien crée d'autres comptes, chacun provisionné avec le même `playerId`. Le test `signupCreatesUserConsumesInvitation…` vérifie l'inverse de ce que dit son nom | `registration/RegistrationService.signup` | Consommer l'invitation à l'inscription (ou la réserver à un e-mail) ; renommer le test |
| Inscription sans validation : e-mail ni vérifié ni contrôlé (format, `null`), mot de passe sans longueur minimale alors que la réinitialisation en exige 8 | `registration/dto/SignupRequest`, `RegistrationController.signup` | Jakarta Validation (`@Valid`, `@Email`, `@Size(min = 8)`), règle de mot de passe commune |
| `email_verified: true` dans les jetons et `/userinfo` alors qu'aucune adresse n'est vérifiée | `security/TokenService`, `oauth2/OAuth2Service.userinfo` | `false` (ou claim retiré) tant que la vérification n'existe pas, en coordination avec hubscore |
| `config/config.example.yml` charge `classpath:db/dev-data` : une configuration de production copiée de l'exemple crée les comptes `admin@gmail.com` / `admin`. _À confirmer_ sur le VPS | `config/config.example.yml`, `db/dev-data/V9999__seed_dev_data.sql` | Seed activé par une variable explicite, désactivé par défaut |
| `state` concaténé sans encodage dans la redirection d'`/oauth2/authorize` : paramètres injectables, `redirect_uri` qui contient déjà une query cassée | `oauth2/OAuth2Service.authorize` | `UriComponentsBuilder`, comme `logout` |
| Rotation du refresh token non atomique (deux `save` sans `@Transactional` ni verrou) : deux appels simultanés avec le même jeton peuvent réussir tous les deux. _À confirmer_ | `oauth2/OAuth2Service.refresh` | `@Transactional` et verrou (optimiste ou `UPDATE … WHERE used_at IS NULL`) ; test |
| Backend par défaut (requête sans `resource`) tiré d'un `Set` non ordonné : avec plusieurs backends configurés, ce n'est pas forcément le premier | `oauth2/OAuth2Service` (constructeur, `toUnmodifiableSet`) | Garder l'ordre de la configuration (`List` ou `LinkedHashSet`) ; test |
| La réinitialisation du mot de passe ne révoque ni les sessions ni les refresh tokens existants : un tiers déjà connecté le reste | `password_reset/PasswordResetService.resetPassword` | Révoquer les sessions de refresh du compte ; à décider pour la session web |
| `scope` demandé jamais comparé aux scopes configurés : il est recopié tel quel dans les jetons | `oauth2/OAuth2Service.authorize` | Refuser un scope inconnu (`invalid_scope`) |
| Décodeur JWT du resource server sans contrôle d'émetteur ni d'audience : un ID token (audience = client) est accepté par `/userinfo` et `POST /api/v1/invitations/{token}/claim`. _À confirmer_ | `security/SecurityConfig.jwtDecoder` | Valider `iss` et l'audience (ou le type de jeton) |
| Secret de provisioning comparé avec `String.equals` (comparaison non constante) | `registration/RegistrationService.createInvitation` | `MessageDigest.isEqual` |
| Échec SMTP pendant la demande de réinitialisation : erreur `500` pour une adresse connue, `200` pour une inconnue, ce qui révèle les comptes. _À confirmer_ | `password_reset/PasswordResetService.requestReset` | Journaliser l'échec d'envoi, réponse identique |
| `.env` suivi par Git malgré `.gitignore` (identifiants de la base). _À confirmer_ : valeurs de dev uniquement | `.env` | `git rm --cached .env`, ajouter un `.env.example` |

## P2 : migration vers les conventions

| Sujet | Où | Action |
| --- | --- | --- |
| `@Setter` de classe sur les entités ; l'état change depuis les services (`setUsedAt`, `setRevokedAt`, `setExpiresAt`) | `user/IdentityUser`, `invitation/InvitationToken`, `token/RefreshTokenSession`, `security/SigningKey` | Méthodes d'intention (`invitation.consume(now)`, `session.rotate(now)`, `session.revoke(now)`), comme `PasswordResetToken` |
| HTTP dans les services (`ResponseStatusException`) ; `OAuthException` imbriquée dans `OAuth2Service` | `RegistrationService`, `PasswordResetService`, `OAuth2Service` | Exceptions typées par feature, annotées `@ResponseStatus` (ou handler centralisé) |
| Erreurs OAuth2 sans corps `{"error": "..."}` (RFC 6749) : le client ne reçoit pas le code (`invalid_grant`…) ; erreurs d'`/oauth2/authorize` jamais renvoyées au `redirect_uri` | `OAuth2Service.OAuthException`, `TokenController` | Handler OAuth2 qui produit le format standard |
| Réponses en `Map<String, Object>` pour des contrats stables (jetons, userinfo, discovery, JWKS) | `oauth2/OAuth2Service`, `oauth2/TokenController` | DTO `record` dans `oauth2/dto/` (noms JSON en `snake_case`) |
| Configuration mutable et dispersée : `IdentityProperties` en classe à setters ; `identity.issuer`, `access-token-ttl-seconds`, `refresh-token-ttl-days`, `auth-frontend-url` lus par `@Value`, avec une valeur par défaut dupliquée | `config/IdentityProperties`, `TokenService`, `OAuth2Service`, `RegistrationService`, `SecurityConfig`, `email/SmtpEmailClient` | `record` `@ConfigurationProperties` validé et injecté |
| Aucun corps de requête validé (`@Valid` absent) | `*/dto/`, controllers | Contraintes sur les `record`, `@Valid @RequestBody` |
| Logique de redirection après connexion (invitation) écrite dans la configuration de sécurité ; le controller de logout instancie `SecurityContextLogoutHandler` | `security/SecurityConfig.securityFilterChain`, `oauth2/TokenController.logout` | `AuthenticationSuccessHandler` dédié ; logout délégué |
| Provisioning appelé directement avec `RestClient` depuis le service, sans Adapter autour du client externe | `registration/RegistrationService.provisionUser` | Adapter `ProvisioningClient` dans `registration/` |
| `orElseThrow()` sans argument : une absence (compte supprimé, session périmée) sort en `500` | `OAuth2Service.authorize`, `userinfo`, `authorizationCode` ; `RegistrationService.authenticatedUser` | Exception typée avec son statut |
| Jetons d'invitation stockés en clair, alors qu'ils permettent de créer un compte pendant 7 jours | `invitation/InvitationToken`, `RegistrationService` | Stocker le SHA-256 et chercher par le hash, comme les refresh tokens |
| Provisioning : une erreur réseau (`ResourceAccessException`) n'est pas attrapée et sort en `500` au lieu de `502` | `registration/RegistrationService.provisionUser` | Traiter `RestClientException` |
| `/connect/logout` exige une session : sans session, l'application est renvoyée vers la page de connexion au lieu de son `post_logout_redirect_uri` | `security/SecurityConfig` | Route accessible sans session ; test MockMvc |
| Codes d'autorisation en mémoire : jamais purgés une fois expirés, perdus au redémarrage, incompatibles avec plusieurs instances | `OAuth2Service.codes` | Purge planifiée (`@EnableScheduling` est déjà actif) ; stockage partagé avant toute 2ᵉ instance |
| Rotation des refresh tokens sans détection de rejeu : un jeton déjà utilisé est refusé, mais la session qui en est issue reste valide | `OAuth2Service.refresh` | Révoquer la chaîne au rejeu (OAuth 2.1). À décider |
| `/login` exclu du CSRF (connexion forcée possible) ; `claim` exclu du CSRF alors qu'il est authentifié par cookie (atténué par `SameSite=Lax`). _À confirmer_ | `security/SecurityConfig` | Jeton CSRF sur le formulaire, ou exception justifiée par écrit |
| Clé privée de signature en clair (base64) dans `signing_key`, `kid` figé `identity-rsa-1`, aucune rotation ; `TokenService` gère la clé **et** émet les jetons | `security/TokenService`, `V2__create_signing_key.sql` | ADR : stockage chiffré ou coffre, `kid` par clé, rotation ; extraire la gestion de clé |
| Seed de dev en migration versionnée `V9999` : toute nouvelle migration est ignorée sur une base locale qui l'a chargé | `db/dev-data/V9999__seed_dev_data.sql` | `R__seed_dev_data.sql` idempotent (ou callback `afterMigrate`) |
| Tests : aucun test de controller ni de `SecurityConfig` (routes, CSRF, redirections) ; `spring-security-test` absent ; e-mail non testé | `src/test/`, `pom.xml` | Voir [conventions/testing.md](conventions/testing.md) |

## P3 : nettoyage et nommage

| Sujet | Où | Action |
| --- | --- | --- |
| Noms pleinement qualifiés dans le code (`java.util.UUID`, `java.net.URLEncoder`…) | `TokenService`, `SecurityConfig`, `OAuth2Service`, `RegistrationService`, `PasswordResetService`, tests | Imports |
| Package `password_reset` en snake_case, seul écart au nommage Java des packages | `password_reset/` | Garder ; renommer (`passwordreset`) seulement dans une PR dédiée |
| CORS déclaré deux fois, avec des règles différentes (MVC : toutes méthodes et en-têtes ; Security : `GET`/`POST`/`OPTIONS`, `Content-Type` et `Authorization`) | `IdentityServerApplication.addCorsMappings`, `SecurityConfig.corsConfigurationSource` | Garder la configuration de Spring Security |
| Deux hachages SHA-256 de jeton (base64url pour les refresh tokens, hexadécimal pour la réinitialisation) | `OAuth2Service.hash`, `PasswordResetService.hash` | Un utilitaire commun au prochain usage |
| Propriété `cookie.secure` sans usage | `config/config.example.yml` | Supprimer |
| Messages de l'API en français (la convention les veut en anglais) ; le portail ne les affiche pas | `password_reset/PasswordResetService` | Messages en anglais, textes français dans le portail |
| E-mail de réinitialisation : « Ce lien expire dans 30 minutes » écrit en dur, quelle que soit `token-ttl-minutes` | `src/main/resources/templates/email/password-reset.html` | Passer la durée au template |
| Invitation de dev `dev-invitation-token` valable 30 jours après la création de la base locale | `db/dev-data/V9999__seed_dev_data.sql` | Avec le seed répétable (P2) |
| Lignes expirées jamais purgées (refresh tokens, jetons de réinitialisation, invitations) | tables correspondantes | Tâche planifiée |
| Dépendances redondantes : `spring-boot-starter-jdbc`, `spring-security-crypto`, `junit-jupiter-engine`, `mockito-core` (déjà apportées par les starters) | `pom.xml` | Retirer |
| `package.json` racine avec description et script `test` factices (il sert seulement à semantic-release) | `package.json` | Nettoyer |
| Portail : `chart.js` et `chartjs-plugin-annotation` enregistrés sans aucun graphique (copiés de kicker-app) | `frontend/src/main.tsx`, `frontend/package.json` | Supprimer |
| Portail : écarts aux conventions de kicker-app : `React.ReactNode` et `interface` (`ThemeProvider`), `style={{}}` (`LoginPage`, `ForgotPasswordPage`, `SignupPage`, `Footer`), pages réparties entre `src/modules/` et `src/pages/`, pages qui importent le style d'une autre (`SignupPage`, `InvitationCompletePage` → `LoginPage.style`), `ForgotPassword.Style.tsx`, import interne `antd/es/...`, `fetch` hors hook (`InvitationCompletePage`), `useEffect` aux dépendances incomplètes (`SignupPage`) | `frontend/src/` | Voir [conventions/frontend.md](conventions/frontend.md) |
| Portail : jeton d'invitation lu sous trois noms (`invitation`, `invitationToken`, `token`) | `frontend/src/modules/`, `RegistrationService.invitationResponse`, `SecurityConfig` | Un seul nom de paramètre |

## Outillage

| Sujet | Action |
| --- | --- |
| Tests uniquement sur mocks : ni les migrations, ni `SecurityConfig`, ni le flux complet ne sont testés | ADR à écrire : test d'intégration `@SpringBootTest` avec Testcontainers PostgreSQL |
| Portail sans lint, sans test et absent de la CI | ESLint, `typecheck` et `build` en CI, comme kicker-app |
| Aucun formateur ni analyse statique Java | Spotless (ou Checkstyle) en CI |
| La CI ne construit pas l'image Docker sur les PR | Ajouter un `docker build` au workflow de tests |
| Limite de 200 lignes contrôlée uniquement pour les écritures de Claude Code | Le hook vit dans le plugin `gouchon` : ajouter un contrôle en CI pour `docs/**/*.md` |
| Déploiement du portail (`frontend/`) non décrit dans le dépôt. _À confirmer_ | Le documenter dans [process/release.md](process/release.md) |
