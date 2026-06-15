# FlexBook Engine — Decision Log

## D1 — Language & Framework: Kotlin + Spring Boot

**Decision:** Kotlin 1.9 on Spring Boot 3.4 / JVM 17.

**Rationale:**
- Kotlin's null safety makes illegal-state bugs (null `tenantId`, missing `resourceId`) compile-time errors instead of NPEs.
- Sealed classes model `RuleResult` exhaustively — the Kotlin compiler enforces that every case is handled in `when` expressions.
- Data classes eliminate boilerplate for DTOs and engine types.
- Spring Boot's DI + `@Component` scanning is the mechanism that makes the rule registry extensible without a factory registry or explicit wiring.

**Trade-off:** JVM startup time is ~2s. For a booking platform this is acceptable; if sub-100ms cold starts were required we'd consider Quarkus or a compiled target.

---

## D2 — Database: PostgreSQL with Flyway

**Decision:** PostgreSQL 16 as the primary store, Flyway for schema migrations, Hibernate in `validate` mode.

**Rationale:**
- PostgreSQL's `JSONB` type stores rule parameters without schema migrations when adding rule types. Queries on JSONB content (e.g. find all rules with `startHour < 9`) remain possible if needed.
- Flyway as the single source of truth for schema prevents drift between environments. Hibernate `validate` mode catches mismatches at startup rather than silently altering tables.
- The partial index `WHERE status NOT IN ('CANCELLED', 'REJECTED')` on bookings keeps the overlapping-booking count query fast even as cancelled bookings accumulate.

**Trade-off:** JSONB params lose compile-time type safety. Each rule's `evaluate()` method validates its own params and returns a descriptive `Failure` result. This is documented in TESTDESIGN.md with specific missing-param test cases.

---

## D3 — Rule Conflict Resolution: First Transition Wins (Priority-Based)

**Decision:** When multiple rules produce `Transition` results (e.g. `AUTO_CONFIRM` → CONFIRMED and `APPROVAL_REQUIRED` → PENDING), the **first** Transition seen wins. Since rules are evaluated in `priority DESC` order, this means the highest-priority rule's Transition takes effect.

**Rationale:** The PRD states "higher priority rule wins on conflict." The "most restrictive wins" alternative (always prefer PENDING) would make `AUTO_CONFIRM` unable to override approval regardless of how the tenant configures priorities, breaking the intended semantics.

**Implication for tenants:** To use `AUTO_CONFIRM` to bypass approval for small groups, configure `AUTO_CONFIRM` at a higher priority than `APPROVAL_REQUIRED`. The priority is an explicit tenant choice, not an engine-level assumption.

---

## D4 — `userId` as Tenant-Provided String

**Decision:** `userId` in `BookingRequest` is an optional `String`. No authentication system is built.

**Rationale:** The PRD scopes this to a booking backend. Auth is a cross-cutting concern that varies by tenant (OAuth, API keys, SSO). Accepting a string identifier lets tenants pass their own user IDs (UUIDs, email hashes, employee numbers) without coupling FlexBook to a specific auth system.

**Trade-off:** The engine trusts the `userId` supplied in the request. In production, this would be validated against a JWT or session token by a gateway layer upstream. For `USER_QUOTA` rules, a missing `userId` causes the rule to pass (skip) with a note in the rule docs — the alternative (reject) would break unrelated bookings for resources that happen to have a quota rule configured.

---

## D5 — Pricing in Smallest Currency Unit

**Decision:** `base_price_per_hour` and all stored prices are `BIGINT` in the smallest currency unit (paise, cents, øre). The `price_multiplier` is stored as `DOUBLE PRECISION` since it's a computation input, not a final monetary value.

**Rationale:** Floating-point arithmetic on money is a well-known source of rounding errors (0.1 + 0.2 ≠ 0.3 in IEEE 754). Storing in smallest units allows all arithmetic to be integer until the final display layer.

**Trade-off:** The API consumer must know the currency to interpret the amounts. A future `currency` field on `Tenant` would complete this.

---

## D6 — Price Applied Based on Booking Start Time

**Decision:** `PEAK_PRICING` applies the multiplier if the booking's **start time** falls in the peak window, not based on overlap duration.

**Rationale:** Prorating a multiplier across a window boundary (e.g., 50% of a booking is peak, 50% is off-peak) requires two price rows or a split computation. For V1 this complexity is not warranted. The trade-off (a 4pm–7pm booking billed at off-peak even though 40% is peak) is explicit, documented, and reversible.

---

## D7 — Rules That Need DB Access Are Spring Beans With Injected Repositories

**Decision:** `BUFFER_TIME` and `USER_QUOTA` rules have `BookingRepository` injected directly, rather than receiving pre-computed values in `BookingContext.metadata`.

**Rationale:** Pre-computing all possible rule inputs in `BookingService` would couple the service to every rule's implementation (it would need to know which rules are active and what they need). Injecting the repository into rules that need it keeps each rule self-contained and removes the service as a mediator of rule-specific queries.

**Trade-off:** Rules become Spring beans with dependencies, not pure functions. This is tested by mocking the repository in rule unit tests and verified to work with the Spring DI context.

---

## D8 — Batch Returns 207 with Per-Item Status

**Decision:** `POST .../bookings/batch` always returns HTTP 207 Multi-Status with a `httpStatus` field per item.

**Rationale:** The PRD requires "independent validation" and "partial success (some instances may pass while others fail)." A 200 with mixed results is misleading. A 422 on any failure would make the entire batch atomic. 207 is the standard HTTP status for partial success and clearly communicates the mixed-result contract to clients.

---

## D9 — `BLACKOUT_PERIOD` Keyed on Booking Start Date

**Decision:** A booking is blocked by a blackout period if its **start date** (not end date) falls within `[startDate, endDate]`.

**Rationale:** A booking that starts before a blackout and ends during it (e.g., overnight) is arguably allowed since the user committed before the blackout started. Start-date keying is simpler to explain to tenants ("no new bookings starting during this period"). A more restrictive interpretation (block if any part overlaps) is a one-line change if required.

---

## D10 — Cooldown Rule Design

**Decision:** `COOLDOWN` reuses the same `countCooldownConflicts` query pattern as `BUFFER_TIME`, checking if any booking by the same user on the same resource ended within `cooldownHours` before the new booking's start time.

**Rationale:** The cooldown period should be measured from the end of the previous booking to the start of the new one. This prevents a user from finishing a session and immediately starting another on the same resource. If we keyed on the previous booking's start time instead, a multi-hour booking would create an unfairly long cooldown.

---

## D11 — Dependent Resource Rule Transitions to PENDING (Not Failure)

**Decision:** When the required dependent resource is missing, `DEPENDENT_RESOURCE` returns `Transition(PENDING)` rather than `Failure`.

**Rationale:** A missing dependent resource is not a hard violation — it may be an oversight the user can correct, or an admin may manually confirm the booking with the dependency resolved out-of-band. Routing to PENDING (pending manual review) is more forgiving than hard-rejecting. Tenants who want a hard block can combine it with another rule.

---

## D12 — Recurring Slot as Service-Layer Mode, Not Pure Rule

**Decision:** `RECURRING_SLOT` is implemented as a constraint rule (validates recurrence metadata) combined with a service-layer method that generates individual instances. The rule validates the recurrence config, and the service handles instance generation.

**Rationale:** Pure rule evaluation (returning Success/Failure/Transition) cannot express "create N child bookings." Adding a new result type would couple the engine to the concept of recurrence. By treating recurrence as a service-layer concern that reuses the existing batch validation logic, the engine stays clean and the feature is built on top of existing primitives.

**Trade-off:** The `POST /tenants/{id}/bookings/recurring` endpoint is separate from the single-booking endpoint. An alternative would be to detect recurrence in `createBooking` and branch, but that would couple the single-booking path to recurrence logic.

---

## D13 — AI Tooling

Built with **Claude Code (claude-sonnet-4-6)** as a development partner. AI assisted with:
- Scaffolding project structure and rule implementations
- Drafting documentation for terminology consistency
- Writing test cases (reviewed for coverage and correctness)

All generated code was reviewed for correctness, security (JPQL parameterization, no PII logging), and PRD alignment. Architecture decisions (D1–D12) were reasoned independently and reflect deliberate trade-offs.
