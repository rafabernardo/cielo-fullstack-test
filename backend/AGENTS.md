# Backend — Engineering Guide

## Project Overview

### Tech stack

| Concern     | Choice                                                        |
|-------------|---------------------------------------------------------------|
| Language    | Java 21                                                       |
| Framework   | Spring Boot (Web MVC, Data JPA, Validation, RestClient)       |
| Database    | H2 (in-memory for dev and tests), schema via JPA `ddl-auto`   |
| HTTP client | Spring `RestClient`                                           |
| Errors      | RFC 9457 `ProblemDetail` (native Spring)                      |
| Tests       | JUnit 5, Mockito, MockMvc, `MockRestServiceServer`            |
| Build       | Maven Wrapper (`./mvnw`)                                      |

### Architecture

Layered architecture following SOLID. Requests flow in one direction:

```
HTTP ─▶ controller ─▶ service ─▶ repository ─▶ database
                         │
                         └────▶ <External>Gateway (interface) ◀── <External>HttpGateway ─▶ external system
```

- **Controllers** speak HTTP. **Services** hold business rules. **Domain entities** guard their own state.
- Abstractions exist only at boundaries with external systems: services depend on a gateway interface, never on an HTTP client.
- The database is the source of consistency (unique constraints, optimistic locking), not in-memory locks.

## Folder Structure

```
src/main/java/<base-package>
├── Application.java
├── controller/              REST endpoints; HTTP ⇄ DTO mapping only
├── service/                 business rules, transactions, orchestration
├── domain/                  JPA entities, enums, embeddables; state transitions live here
├── repository/              Spring Data JPA interfaces
├── dto/                     API request/response records (+ Bean Validation)
├── integration/
│   └── <external>/          gateway interface (port), HTTP implementation,
│                            external contract records, client interceptors
├── config/                  @ConfigurationProperties records and generic beans
└── exception/               custom exceptions + global exception handler

src/test/java/<base-package>
├── controller/              MockMvc / @SpringBootTest flow tests
├── service/                 unit tests with mocked gateways/repositories
└── integration/<external>/  gateway tests with MockRestServiceServer
```

Test packages mirror main packages. One top-level class per file.

## Dependency Rules (who may import whom)

| Package       | May import                                                        | Must NOT import                                       |
|---------------|-------------------------------------------------------------------|-------------------------------------------------------|
| `controller`  | `service`, `dto`, `domain`, `exception`                           | `repository`, `integration`, `config`                 |
| `service`     | `domain`, `repository`, `exception`, gateway interfaces + their contract records | `controller`, `dto`, gateway implementations, HTTP clients, `config` |
| `domain`      | JDK, `jakarta.persistence`, `exception`                           | any other project package; any Spring class           |
| `repository`  | `domain`, Spring Data                                             | everything else                                       |
| `dto`         | `domain` (for mapping factories), `jakarta.validation`            | `service`, `repository`, `integration`                |
| `integration` | `config`, `exception`                                             | `controller`, `service`, `repository`, `dto`, `domain` |
| `config`      | nothing from the project (JDK and Spring only)                    | any other project package                             |
| `exception`   | JDK, Spring Web (`ProblemDetail`, `@RestControllerAdvice`)        | `service`, `repository`, `integration`                |

Rules of thumb:

- Dependencies point **inward**: controller → service → domain. Nothing depends on `controller`.
- `domain` is the most stable package: it knows nothing about HTTP, DTOs, or external systems.
- Services receive plain parameters or domain objects and return domain objects; controllers map to/from DTOs.
- No cyclic dependencies between packages.
- Each gateway implementation builds its own `RestClient` from the injected `RestClient.Builder` and its `*Properties`; `config` never wires integration classes.
- Gateway implementations are named `*HttpGateway`; services never reference them.

These rules are enforced by `ArchitectureTest` (ArchUnit) and run with `./mvnw test`.

## Architecture Rules

1. **Interfaces only at real boundaries.** A gateway interface exists for each external system so it can be replaced in tests. Classes with a single implementation (services, mappers, helpers) do not get an interface.
2. **Rich domain, thin services.** State changes are entity methods (e.g. `entity.approve()`, `entity.reject(reason)`). Invalid transitions throw `IllegalStateException`. Services orchestrate; they never assign state fields directly.
3. **Single responsibility per service.** One service per use case or cohesive group of use cases; split when a class gains a second reason to change.
4. **Duplicates fail on a natural key.** A write that must not happen twice is guarded by a `UNIQUE` constraint on what makes it unique in the domain (e.g. one lock row per installment), never by a client-generated key. The guard is committed before any side effect, so concurrent duplicates fail on the constraint; the conflict response points to the resource that already holds it (e.g. `operationIds`).
5. **No remote calls inside a database transaction.** Calls to gateways happen outside `@Transactional` boundaries; persist before and after in separate short transactions.
6. **Optimistic locking** (`@Version`) on entities that can be updated concurrently; on conflict, reload and return the current state.
7. **Configuration** comes from `@ConfigurationProperties` records grouped by prefix. No `@Value` scattered across classes. No hard-coded URLs, timeouts, or secrets.
8. **External calls always have timeouts** (connect and read), configured per gateway.
9. **Status timestamps** for stateful entities: `createdAt` records the request and `updatedAt` the last change, set by the entity's own transition methods. This covers the history while an entity has a single transition (e.g. `PENDING` → final); once the lifecycle grows, record each transition as an event (state, timestamp, detail), fed by the existing status-changed event.
10. **Message integrity** (not implemented; the simulator verifies no signature): if added, outbound calls are signed by a client interceptor (e.g. HMAC signature header), never inside services.

## Code Standards and Code Style

### Naming

- Classes `PascalCase`, methods/fields `camelCase`, constants `UPPER_SNAKE_CASE`, packages lowercase singular.
- Suffix by role: `*Controller`, `*Service`, `*Repository`, `*Gateway`, `*Request`, `*Response`, `*Properties`, `*Config`, `*Exception`.
- Methods are verbs (`create`, `find`, `approve`); booleans read as predicates (`isPending`, `hasExpired`).
- Names reveal intent; no abbreviations except well-known ones (`id`, `url`, `dto`).
- Comments, commit messages, and docs are in English.

### Java

- Use Java 21 features where they simplify: `record` for DTOs, external contracts, and configuration; `switch` expressions; pattern matching; `var` only when the type is obvious from the right-hand side.
- **Constructor injection only**, fields `private final`. No field injection, no Lombok.
- Money is `BigDecimal`; dates are `LocalDate`; timestamps are `Instant`; identifiers are `UUID`.
- Collections exposed by entities are unmodifiable views.
- No `null` returns from services: return `Optional` for "may not exist", or throw a specific exception.
- Small methods (≈20 lines) with one level of abstraction each.
- No dead code, no commented-out code, no `TODO`s without an issue reference.

### Formatting

- Indentation with **tabs**.
- One blank line between methods; no wildcard imports; no unused imports.
- Line length ≈ 120 characters.

### Testing

- Every main flow and every error scenario has at least one test.
- Duplicate-guard and concurrency rules have dedicated tests.
- Test names describe behavior (e.g. `rejectsRepeatedRequestPointingToOperationThatHoldsItsInstallments`).
- Arrange / Act / Assert blocks separated by blank lines.
- No `@Disabled`, no skipped tests, no tests without assertions.

## API Layer

- Base path: `/api`. JSON only. Field names in `camelCase`. Resource paths are plural nouns (`/api/<resources>/{id}`).
- Controllers: validate input (`@Valid`), call one service method, map the result to a response DTO. No business logic, no repository access, no try/catch.
- Request DTOs are `record`s with Bean Validation annotations; response DTOs are `record`s with a `static from(Entity)` factory.
- Entities are never serialized directly.

### Status codes

| Situation                                         | Status        |
|---------------------------------------------------|---------------|
| Read succeeded                                    | `200`         |
| Resource created synchronously                    | `201` + `Location` header |
| Request accepted, processing continues async      | `202`         |
| Async resource still processing                   | `200` with current state + `Retry-After` header |
| Delete / action with no body                      | `204`         |

### Headers

- `Retry-After` (seconds) when the client should poll again.

## Error Handling

- All errors are returned as **`ProblemDetail`** (`application/problem+json`) by a single `@RestControllerAdvice` (`ApiExceptionHandler`).
- Every problem includes `type`, `title`, `status`, `detail`, `instance`, and a machine-readable `code` property the client can switch on.
- Services throw specific exceptions; they never build HTTP responses. Controllers never catch exceptions.
- Stack traces and internal messages never reach the client. Unexpected exceptions return a generic `500` and are logged at `ERROR` with the stack trace.

### Exception mapping

| Exception category                      | HTTP  | `code` example            |
|-----------------------------------------|-------|---------------------------|
| `MethodArgumentNotValidException`       | `400` | `VALIDATION_ERROR` (with `errors` listing field messages) |
| Resource not found                      | `404` | `<RESOURCE>_NOT_FOUND`    |
| Conflict with current state             | `409` | `<RESOURCE>_CONFLICT`     |
| Business rule violation                 | `422` | `<RULE>_VIOLATED`         |
| External system bad/unexpected response | `502` | `<EXTERNAL>_BAD_RESPONSE` |
| External system unavailable / timeout   | `503` | `<EXTERNAL>_UNAVAILABLE`  |
| Any other `Exception`                   | `500` | `INTERNAL_ERROR`          |

### Integration errors

- The gateway implementation is the **only** place that translates external HTTP outcomes into exceptions:
  - `2xx` → contract record.
  - `5xx`, timeouts, connection refused (`ResourceAccessException`) → `*UnavailableException`, carrying the external error message when present.
  - Unexpected `4xx` or unparseable body → `*BadResponseException`.
- A failed outbound call after a local write leaves the record in an explicit failure state (e.g. `FAILED`) and releases what it held, so the client can safely send a new request.
- When a read depends on an external refresh that fails, return the last known state with a flag indicating it may be stale, instead of failing the whole request.
- Integration failures are logged at `WARN` with correlation identifiers; never log secrets or sensitive personal/financial data.
