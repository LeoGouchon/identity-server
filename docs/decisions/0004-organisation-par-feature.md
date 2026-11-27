# 0004. Organisation par feature et controllers minces

- **Statut** : Accepté
- **Date** : 2026-11-27 (reprend les règles de l'ancien `AGENTS.md`)

## Contexte

Les premiers controllers vivaient à la racine du package, avec leur logique (accès aux repositories, hachage des mots
de passe, création de jetons). Un refactor a regroupé le code par feature et vidé les controllers ; ses règles étaient
écrites dans `AGENTS.md`, en anglais, hors de toute doc. Elles sont reprises ici et détaillées dans
[../conventions/](../conventions/README.md).

## Options envisagées

1. **Packages par couche** (`controller/`, `service/`, `repository/`) : familier, mais une feature est éparpillée dans
   tout l'arbre.
2. **Packages par feature, couches à plat**, comme hubscore mais sans ses sous-dossiers ni ses interfaces
   systématiques.

## Décision

Nous retenons l'option 2 :

- **Packages par feature ou protocole** : `oauth2`, `registration`, `password_reset`, `invitation`, `token`, `user`,
  `security`, `email`, `config`. Le controller, son service, ses DTO, entités et repositories restent ensemble quand
  c'est possible.
- **Controllers minces** : ils lient la route, les paramètres et le corps, appellent **un** service dédié
  (`TokenController` → `OAuth2Service`) et renvoient son résultat ou une redirection. Pas de repository, de hachage,
  de jeton, de contrôle de droits ni de modification d'entité dans un controller.
- **Erreurs** : le service lève une exception typée qui porte son statut (`@ResponseStatus`) ou que traduit un handler
  centralisé ; le controller ne l'attrape pas.
- **DTO** : `record` autonomes dans le package `dto` de la feature, nommés `SignupRequest`, `SignupResponse`… Pas de
  `record` imbriqué dans un controller, pas de `Map` pour un contrat stable.
- **Conception** : SOLID appliqué avec pragmatisme ; entités qui protègent leurs invariants par des méthodes
  d'intention ; interface, fabrique ou héritage seulement face à une variation réelle (pas d'interface pour un service
  à implémentation unique, contrairement à hubscore).
- **Temps** : jamais d'horloge implicite. `LocalDateTime.now(ZoneOffset.UTC)` pour la base et les expirations,
  `Instant.now(Clock.systemUTC())` pour les JWT.
- **OAuth2** : routes dans `oauth2.TokenController`, comportement dans `oauth2.OAuth2Service` ; audience choisie parmi
  les backends configurés et conservée à la rotation du refresh token.

## Conséquences

- **Positives** : une feature se lit dans un seul package ; les controllers sont triviaux à relire ; les services se
  testent sans Spring.
- **Négatives / coûts** :
    - les services restent des classes concrètes : un test les construit avec des mocks de leurs dépendances ;
    - plusieurs écarts subsistent (entités à setters, `ResponseStatusException`, réponses en `Map`) : voir
      [../tech-debt.md](../tech-debt.md).
- **Suivi** : `./mvnw test` après chaque refactor d'architecture.
