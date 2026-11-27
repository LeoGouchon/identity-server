# Ajouter un endpoint

Créer ou modifier une route, du service jusqu'à la doc. Les règles sont dans
[../conventions/api-design.md](../conventions/api-design.md) et
[../conventions/security.md](../conventions/security.md) ; cette recette donne l'ordre et un squelette.

## Fichiers clés

- `src/main/java/com/leogouchon/identityserver/<feature>/` : controller, service, entités, repositories et `dto/` de la
  feature (exemple complet : `password_reset/`).
- `security/SecurityConfig.java` : règles d'accès, exclusions CSRF, CORS.
- `oauth2/TokenController.java` et `oauth2/OAuth2Service.java` : seul endroit pour un endpoint OAuth2.
- [../architecture/api.md](../architecture/api.md) : la liste des endpoints à tenir à jour.

## Checklist

1. [ ] **Contrat** écrit (dans le brief) : chemin, méthode, paramètres, corps, réponse, erreurs, mode d'accès (public,
       session du portail, jeton bearer, secret de provisioning) et clients appelants.
2. [ ] **Feature** : le controller va dans le package de la feature qu'il expose, avec **un** service dédié (classe
       concrète, pas d'interface sans variation). Un endpoint OAuth2 va dans `TokenController` / `OAuth2Service`,
       avec les noms de paramètres standard en `snake_case` (`client_id`, `redirect_uri`).
3. [ ] **Entité** : un changement d'état passe par une méthode d'intention qui garde l'invariant, avec son test.
4. [ ] **Service** : repositories, encodeur de mot de passe, jetons, appels externes et contrôles de droits ici ;
       `@Transactional` autour des écritures ; temps lu avec `LocalDateTime.now(ZoneOffset.UTC)` ; erreur attendue =
       exception typée de la feature.
5. [ ] **Test du service** : un test par règle et par cas d'erreur.
6. [ ] **DTO** : `record` autonomes dans `<feature>/dto/` (`XxxRequest`, `XxxResponse`), validés par Jakarta
       Validation. Pas de `Map` pour un contrat stable, pas de `record` imbriqué dans le controller.
7. [ ] **Controller** mince : route, `@Valid @RequestBody`, paramètres, retour du service. Une redirection se fait
       avec l'URL calculée par le service (`response.sendRedirect(service.xxx(...))`). Pas de `try/catch`.
8. [ ] **Accès dans `SecurityConfig`** : la dernière règle est `anyRequest().permitAll()`, donc **une route non
       déclarée est publique**. Une route authentifiée s'ajoute à la liste `authenticated()`. Une route interne
       (appelée par une application) va sous `/api/internal/**`, protégée par le secret de son client, comparé en
       temps constant.
9. [ ] **CSRF et CORS** : un `POST` appelé par le portail avec le cookie de session est soumis au CSRF ; l'exclure
       (`csrf.ignoringRequestMatchers`) seulement avec une justification écrite dans
       [../architecture/security.md](../architecture/security.md). CORS n'autorise que `GET`, `POST` et `OPTIONS`
       et les en-têtes `Content-Type` et `Authorization` : une autre méthode ou un autre en-tête appelé depuis un
       navigateur s'ajoute à `corsConfigurationSource`.
10. [ ] **Erreur nouvelle** : exception typée annotée `@ResponseStatus` dans le package de la feature
        ([../conventions/api-design.md](../conventions/api-design.md#erreurs)) ; jamais de secret ni de détail interne
        dans le message, et un message générique quand la réponse pourrait révéler un compte.
11. [ ] **Test MockMvc** : nominal, validation (`400`), accès refusé sans authentification, chaque règle de droit.
12. [ ] **Doc** : ligne dans [../architecture/api.md](../architecture/api.md) ;
        [../architecture/security.md](../architecture/security.md) si l'accès, le CSRF ou le CORS change ;
        [../architecture/oauth2.md](../architecture/oauth2.md) pour un endpoint OAuth2.
13. [ ] **Clients** : si le contrat d'un endpoint existant change, vérifier l'usage dans kicker-app, hubscore et le
        portail (`frontend/src/api/constant.ts`, `frontend/src/hooks/`), et prévoir la compatibilité.

## Squelette

Exemple fictif : un utilisateur connecté change son nom (`PUT /api/v1/me/name`), dans la feature `user`.

Entité (la règle vit ici) :

```java
public void rename(String firstName, String lastName) {
    if (firstName == null || firstName.isBlank() || lastName == null || lastName.isBlank()) {
        throw new IllegalArgumentException("First name and last name are required");
    }
    this.firstName = firstName.trim();
    this.lastName = lastName.trim();
}
```

DTO (`user/dto/`) :

```java
public record RenameRequest(@NotBlank @Size(max = 100) String firstName,
                            @NotBlank @Size(max = 100) String lastName) {
}

public record AccountResponse(UUID id, String email, String firstName, String lastName) {
}
```

Exception typée (cible, voir [../tech-debt.md](../tech-debt.md) pour l'existant) :

```java
@ResponseStatus(HttpStatus.NOT_FOUND)
public class IdentityUserNotFoundException extends RuntimeException {
    public IdentityUserNotFoundException(UUID id) {
        super("Identity user not found: " + id);
    }
}
```

Service (la résolution de l'utilisateur reprend celle de `RegistrationService.authenticatedUser` : `sub` du `Jwt`,
sinon e-mail de la session) :

```java
@Service
public class AccountService {
    private final IdentityUserRepository users;

    public AccountService(IdentityUserRepository users) {
        this.users = users;
    }

    @Transactional
    public AccountResponse rename(Authentication authentication, RenameRequest request) {
        UUID id = subject(authentication);
        IdentityUser user = users.findById(id).orElseThrow(() -> new IdentityUserNotFoundException(id));
        user.rename(request.firstName(), request.lastName());
        return new AccountResponse(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName());
    }
}
```

Controller, mince comme `PasswordResetController` :

```java
@RestController
@RequestMapping("/api/v1/me")
public class AccountController {
    private final AccountService accounts;

    public AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @PutMapping("/name")
    public AccountResponse rename(@Valid @RequestBody RenameRequest request, Authentication authentication) {
        return accounts.rename(authentication, request);
    }
}
```

Accès (`SecurityConfig`) : ajouter `"/api/v1/me/**"` à la règle `authenticated()`, et `PUT` à
`corsConfigurationSource` si le portail l'appelle.

Test MockMvc, filtres de sécurité actifs :

```java
@WebMvcTest(AccountController.class)
@Import(SecurityConfig.class)
class AccountControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean AccountService accounts;
    @MockitoBean JwtDecoder jwtDecoder;               // replaces the bean built from TokenService
    @MockitoBean IdentityUserRepository users;        // used by SecurityConfig.userDetailsService

    @Test
    void renameRejectsAnonymousRequest() throws Exception {
        mockMvc.perform(put("/api/v1/me/name").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Ada\",\"lastName\":\"Lovelace\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(accounts);
    }

    @Test
    void renameRejectsBlankName() throws Exception {
        mockMvc.perform(put("/api/v1/me/name").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"\",\"lastName\":\"Lovelace\"}"))
                .andExpect(status().isBadRequest());
    }
}
```

> Aucun test de ce type n'existe encore : ce squelette est la cible. Il demande d'ajouter `spring-security-test`
> (scope `test`) au `pom.xml` pour `jwt()` et `user()`. Le premier test confirme la mise en place (statut exact
> d'une requête anonyme : `401` ou redirection `302` vers le portail, selon l'en-tête `Accept`) et corrige cette
> recette si besoin.
