# Identity Server Architecture Rules

## Package by feature

Organize application code by business or protocol feature rather than putting all controllers at the root package.

Recommended structure:

```text
com.leogouchon.identityserver/
+-- oauth2/
|   +-- TokenController.java
|   +-- OAuth2Service.java
+-- registration/
|   +-- RegistrationController.java
|   +-- dto/
+-- invitation/
+-- token/
+-- user/
+-- security/
```

New controllers should be placed in the package for the feature they expose. Keep related services, DTOs, entities, and repositories close to that feature where practical.

## Thin controllers

Controllers must contain endpoint definitions only:

- Map HTTP routes and bind request parameters, headers, and bodies.
- Delegate validation and business logic to a service.
- Return the service result or perform the minimal HTTP response operation required by the endpoint, such as a redirect.
- Do not access repositories, encode passwords, create tokens, perform authorization checks, or mutate domain entities directly.
- HTTP status mapping for expected errors may remain at the web boundary, preferably through typed exceptions annotated with `@ResponseStatus` or a centralized exception handler.

Each controller should have a dedicated feature service (for example, `TokenController` with `OAuth2Service` and `RegistrationController` with `RegistrationService`). Services own the application flow, validation, persistence coordination, token generation, and integration calls.

Controllers must not catch business exceptions to reimplement service behavior. Service exceptions should carry, or be translated at the web boundary into, the correct HTTP status; the controller simply lets those exceptions propagate or delegates to the centralized exception handling mechanism.

## Object-oriented design

Apply SOLID and object-oriented principles pragmatically:

- **Single Responsibility:** keep controllers, application services, domain entities, repositories, token services, and external integrations responsible for one cohesive concern. Split a service when unrelated use cases or policies begin to accumulate.
- **Encapsulation:** keep mutable state private and protect invariants through constructors or domain methods. Do not expose setters merely to make persistence or tests convenient; use them only when the state transition is valid.
- **Tell, do not ask:** put behavior next to the data it governs. Prefer `invitation.consume()` or `refreshSession.rotate()` over retrieving state in a service and mutating several fields from outside the object when the invariant belongs to the entity.
- **Dependency inversion:** depend on interfaces or domain-level abstractions when an integration, storage implementation, or policy can vary. Inject dependencies through constructors; do not instantiate infrastructure in controllers or services.
- **Composition over inheritance:** compose services from focused collaborators. Use inheritance only for a genuine, substitutable `is-a` relationship.
- **Immutability:** use records or immutable value objects for request, response, and configuration data. Do not return internal mutable collections or entities from application boundaries.
- **Polymorphism:** use a small interface with separate implementations when behavior varies by grant type, backend, token policy, or external provider. Avoid growing conditional chains for behavior that has independent rules.

## Design patterns

Use design patterns to make a demonstrated variation or boundary explicit, not as ceremony:

- Use **Strategy** for interchangeable authentication, token, backend, or provisioning policies.
- Use a **Factory** when creation requires selecting among multiple valid implementations or enforcing construction rules.
- Use an **Adapter** around external clients such as Hubscore provisioning so application services do not depend directly on transport details.
- Use **Repository** abstractions for persistence; keep query and storage details out of controllers and domain objects.
- Use a centralized **exception handler** or typed exceptions for consistent HTTP error mapping.

Prefer the simplest design that preserves these boundaries. Do not introduce an interface, factory, or inheritance hierarchy when there is only one stable implementation and no current variation point.

## Time handling

Never use implicit system time in application code. Pass an explicit timezone or clock to every time read: use `LocalDateTime.now(ZoneOffset.UTC)` for persisted timestamps and expiry comparisons, and `Instant.now(Clock.systemUTC())` for JWT timestamps. Keep database and token time handling consistently in UTC.

## DTOs

Request and response DTOs must be standalone types in a dedicated `dto` package for their feature. Do not define API DTOs as nested records inside controllers.

Use explicit DTO names such as `SignupRequest`, `InvitationRequest`, and `SignupResponse` instead of returning loosely typed maps when the payload has a stable contract.

## OAuth2 boundaries

Keep OAuth2 endpoint mappings in `oauth2.TokenController` and OAuth2 behavior in `oauth2.OAuth2Service`. Access-token audiences must be selected from configured allowed backends, and refresh-token rotation must preserve the original audience.

## Verification

After architectural refactors, run the Maven wrapper test suite:

```text
./mvnw test
```
