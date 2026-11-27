# Tests

Ce qui doit être testé, et comment écrire les tests. La suite tourne avec `./mvnw -B test`, en local comme en CI.

## Fichiers clés

- `src/test/java/com/leogouchon/identityserver/` : tests, même arborescence que `src/main/java`.
- `oauth2/OAuth2ServiceTest.java` : le modèle d'un test de service.
- `pom.xml` : `spring-boot-starter-test` (JUnit 5, Mockito, AssertJ, MockMvc).
- `.github/workflows/tests.yml` : CI sur chaque PR et push vers `dev` et `main`.

**État** : ⚠️ `spring-security-test` n'est pas dans `pom.xml` : à ajouter (scope `test`) avec le premier test de
controller.

## La règle

Tout code nouveau ou modifié est testé dans la même tâche que lui, avant ou après le code :

- **chaque service ou règle** créé ou modifié a un **test unitaire**, sans Spring ;
- **chaque méthode d'entité qui porte un invariant** (`isUsableAt`, cible : `rotate`, `consume`) a son test unitaire ;
- **chaque endpoint** créé ou modifié a un **test MockMvc** avec les filtres de sécurité : statut et corps nominaux,
  validation (`400`), accès (public, `401` sans session ou sans secret), CSRF si la route en dépend ;
- **chaque correctif** commence par un test qui reproduit le bug.

Un bon test échoue quand la règle casse. On vise les comportements du brief et les règles de
[security.md](security.md), pas un pourcentage de couverture (JaCoCo donne un repère, pas un objectif).

Après un refactor d'architecture, toute la suite passe : `./mvnw test`.

## Tests unitaires de service

Sans Spring : on construit le service avec ses dépendances simulées par Mockito, et sa configuration en objet.

```java
@ExtendWith(MockitoExtension.class)
class OAuth2ServiceTest {
    @Mock TokenService tokens;
    @Mock IdentityUserRepository users;
    @Mock RefreshTokenSessionRepository refreshTokens;

    @Test
    void refreshRotatesTokenAndPreservesAudience() {
        RefreshTokenSession session = refreshSession("refresh-token", "web-client", "openid", "default-api",
                LocalDateTime.now(ZoneOffset.UTC).plusDays(1));
        when(refreshTokens.findByTokenHash(hash("refresh-token"))).thenReturn(Optional.of(session));

        Map<String, Object> result = service.token("refresh_token", "web-client", null, null, null, "refresh-token");

        assertNotNull(result.get("refresh_token"));
        assertNotNull(session.getRevokedAt());
        verify(tokens).accessToken(user, "default-api", "openid");
    }
}
```

- Assertions JUnit (`assertEquals`, `assertThrows`, `assertNotNull`), comme le code existant ; AssertJ
  (`assertThat`) est accepté. On ne mélange pas les deux dans une même classe.
- `verify(...)` pour ce qui est écrit ou envoyé, `never()` pour ce qui ne doit pas l'être ; `ArgumentCaptor` ou
  `argThat` pour vérifier un objet enregistré.
- Un cas d'erreur vérifie aussi **l'absence d'effet** (jeton non consommé, rien enregistré, aucun e-mail).
- **Le temps** : les services lisent l'horloge en UTC ([code-style.md](code-style.md#le-temps)), sans `Clock`
  injecté. Un test pose des dates relatives à maintenant (`LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1)` pour un
  jeton expiré) ; une méthode d'entité se teste avec un `now` passé en paramètre.

**État** : ✅ `OAuth2Service`, `RegistrationService`, `PasswordResetService`, `TokenService` ; ⚠️ e-mail
(`PasswordResetEmailSender`, `EmailTemplateRenderer`) non testé.

## Tests de controller

> Cible, à confirmer par le premier test : aucun test de controller n'existe encore. Le premier ajoute
> `spring-security-test` et corrige ce squelette si besoin.

`@WebMvcTest(<Controller>.class)` avec le service simulé par `@MockitoBean`, et `SecurityConfig` importée pour garder
les vraies règles d'accès. `SecurityConfig` a besoin de `TokenService` (décodeur JWT) et d'`IdentityUserRepository`
(`UserDetailsService`) : on les simule aussi.

```java
@WebMvcTest(RegistrationController.class)
@Import(SecurityConfig.class)
class RegistrationControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean RegistrationService registration;
    @MockitoBean TokenService tokenService;
    @MockitoBean IdentityUserRepository users;

    @Test
    void claimRequiresAnAuthenticatedUser() throws Exception {
        mockMvc.perform(post("/api/v1/invitations/{token}/claim", "token"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(registration);
    }
}
```

- **Accès** : les filtres restent actifs (pas de `addFilters = false`). `user("user@example.com")` simule une session,
  `jwt()` un jeton porteur.
- On vérifie le statut, les champs utiles du JSON (`jsonPath`), les redirections (`redirectedUrl`) et l'appel au
  service avec les bons arguments.

**État** : ⚠️ aucun test de controller ni de `SecurityConfig` (routes publiques, CSRF, redirection après connexion).

## Tests d'intégration

Aucun : les tests tournent sur des mocks, sans base. Une migration, une requête ou le flux complet se vérifient sur
`make up` pendant la vérification, et le rapport le dit. Testcontainers est prévu dans
[../tech-debt.md](../tech-debt.md).

## Données de test

- Des valeurs lisibles et celles du brief (`user@example.com`, `web-client`, `default-api`), pas de valeurs
  aléatoires qui masquent l'intention.
- La configuration se construit en objet dans `setUp` (`IdentityProperties` et ses clients), comme dans
  `OAuth2ServiceTest`.
- Un helper utile à une seule classe reste une méthode privée du test (`refreshSession(...)`, `hash(...)`).

## Nommage

- Classe : `<ClasseTestée>Test`, dans le même package que la classe testée.
- Méthode : en **camelCase**, en anglais, qui décrit le comportement attendu, sans préfixe `test` :
  `refreshRotatesTokenAndPreservesAudience`, `requestResetSendsLinkAndDoesNotRevealUnknownAddresses`.
- Le nom dit ce que le test vérifie vraiment. Un test vérifie un comportement ; plusieurs assertions sont permises si
  elles décrivent le même résultat.

**État** : ⚠️ `signupCreatesUserConsumesInvitationAndProvisionsDownstreamUser` vérifie que l'invitation n'est **pas**
consommée.
