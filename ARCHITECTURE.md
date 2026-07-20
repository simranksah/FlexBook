# FlexBook Engine — Architecture

## 1. Overview

FlexBook is a **multi-tenant booking platform** where each tenant configures their own booking rules by composing
extensible primitives. The central design challenge is that adding a new rule type must require only writing a new
class — no modifications to the engine, service layer, or data model.

```
┌──────────────────────────────────────────────────────┐
│                    HTTP API (REST)                    │
│  POST /tenants  POST /tenants/{id}/resources          │
│  POST /tenants/{id}/rules                             │
│  POST /tenants/{id}/bookings  (single + batch)        │
│  GET  /tenants/{id}/bookings  (filtered + paginated)  │
└───────────────────────┬──────────────────────────────┘
                        │
┌───────────────────────▼──────────────────────────────┐
│                  Service Layer                        │
│  BookingService  TenantService  ResourceService       │
│  RuleService                                          │
└─────────────┬─────────────────────┬──────────────────┘
              │                     │
┌─────────────▼──────┐  ┌──────────▼──────────────────┐
│   Rule Engine      │  │   Spring Data JPA            │
│   RuleRegistry     │  │   Repositories               │
│   BookingRule impls│  │   PostgreSQL + Flyway        │
└────────────────────┘  └─────────────────────────────┘
```

---

## 2. Rule Engine Design

### 2.1 Strategy Pattern + Registry

The engine uses the **Strategy Pattern** where each rule type is an independent implementation of `BookingRule`:

```kotlin
interface BookingRule {
    val type: String   // matched against rule_configurations.rule_type
    fun evaluate(context: BookingContext, config: RuleConfig): RuleResult
}
```

`RuleRegistry` is a Spring `@Component` that autowires **all** `BookingRule` beans via constructor injection and indexes
them by `type`. New rules are discovered automatically at startup — no explicit registration required.

### 2.2 Rule Result Types

```
RuleResult
├── Success          — rule passed, continue evaluation
├── Failure(msg)     — reject booking immediately (short-circuit)
├── Transition(status, reason)  — request a status change (PENDING or CONFIRMED)
└── PriceModifier(multiplier)   — apply a compounded price multiplier
```

### 2.3 Evaluation Algorithm

Rules are fetched ordered by `priority DESC`. The engine iterates them once:

```
for each rule (highest priority first):
  match result:
    Failure      → throw ValidationException  (stop)
    Transition   → record if no prior Transition seen  ("first wins" = highest priority wins)
    PriceModifier → multiply into accumulator
    Success      → no-op, continue

final status = recorded Transition.status OR CONFIRMED (if no Transition)
total price  = basePrice × accumulatedMultiplier
```

**Key property**: `Transition` uses "first wins" (highest-priority Transition takes effect). This allows
`AUTO_CONFIRM` (configured at priority 100) to override `APPROVAL_REQUIRED` (priority 50) by being evaluated first. The
tenant controls this via the `priority` field when creating rules.

### 2.4 Rule Scoping

Rules are applied at three levels, merged into a single ordered list per booking evaluation:

| Scope             | Condition                                     | Example                                      |
|-------------------|-----------------------------------------------|----------------------------------------------|
| Resource-specific | `resourceId = :id`                            | "Only Meeting Room A has 15-min buffer"      |
| Resource-type     | `resourceType = :type`                        | "All ROOMs require approval for groups > 10" |
| Tenant-wide       | `resourceId IS NULL AND resourceType IS NULL` | "All resources: 08:00–18:00 only"            |

All three scopes are fetched in one query, ordered by priority. Higher-specificity rules don't automatically override
lower-specificity ones — the tenant controls priority explicitly.

---

## 3. Data Model

```
tenants
  id, name (UNIQUE), enabled, created_at

resources
  id, tenant_id →tenants, name, type, capacity, base_price_per_hour, enabled, created_at

rule_configurations
  id, tenant_id →tenants
  resource_id →resources (NULL = not resource-specific)
  resource_type (NULL = all types)
  rule_type, params (JSONB), priority, enabled, created_at

bookings
  id, tenant_id →tenants, resource_id →resources
  user_id (tenant-provided identifier)
  start_time, end_time
  status (CONFIRMED|PENDING|CANCELLED|REJECTED), status_reason
  metadata (JSONB), base_price, price_multiplier, total_price
  created_at
```

### JSONB for Rule Parameters

`rule_configurations.params` is stored as `JSONB`. This avoids schema migrations when adding new rule types with
different parameter shapes. The trade-off (no compile-time safety) is mitigated by validation in each rule's
`evaluate()` method, which returns a descriptive `Failure` if required params are missing or malformed.

### Pricing in Smallest Currency Unit

`base_price_per_hour` and all price fields are stored as `BIGINT` in the smallest currency unit (paise, cents, etc.) to
avoid floating-point money errors. The `price_multiplier` is `DOUBLE PRECISION` since it's used only for computation,
not storage of final amounts.

---

## 4. API Design

### Tenant Isolation

Every endpoint is scoped to a tenant via the URL path (`/tenants/{tenantId}/...`). The service layer always filters by
`tenantId`, preventing cross-tenant data leakage.

### Booking Endpoints

| Method | Path                                 | Description                     |
|--------|--------------------------------------|---------------------------------|
| POST   | `/tenants`                           | Create tenant                   |
| POST   | `/tenants/{id}/resources`            | Create resource                 |
| POST   | `/tenants/{id}/rules`                | Create rule configuration       |
| GET    | `/tenants/{id}/rules`                | List all rules                  |
| DELETE | `/tenants/{id}/rules/{ruleId}`       | Remove a rule                   |
| POST   | `/tenants/{id}/bookings`             | Create single booking           |
| POST   | `/tenants/{id}/bookings/batch`       | Batch create (207 Multi-Status) |
| POST   | `/tenants/{id}/bookings/recurring`   | Recurring bookings (207 Multi-Status) |
| GET    | `/tenants/{id}/bookings`             | Query with filters + pagination |
| GET    | `/tenants/{id}/bookings/{bookingId}` | Get single booking              |
| DELETE | `/tenants/{id}/bookings/{bookingId}` | Cancel booking                  |

### Batch / 207 Multi-Status

`POST .../bookings/batch` evaluates each booking independently. A failure in one item does not affect others. The
response is always HTTP 207 with per-item `httpStatus` (201/400/404/422/500). This satisfies the PRD requirement for
independent validation with partial success.

### Query Filters (GET /bookings)

Supports `resourceId`, `userId`, `status`, `from`, `to` (ISO 8601), `page`, `size`. Implemented via JPA
`Specification` (Criteria API) for composable, type-safe predicate building without string concatenation.

---

## 5. Rule Catalog

| # | Rule Type             | Category     | DB Access    | Key Params                                 |
|---|-----------------------|--------------|--------------|--------------------------------------------|
| 1 | `TIME_BOUNDARY`       | Constraint   | No           | startHour, endHour                         |
| 2 | `MAX_DURATION`        | Constraint   | No           | maxMinutes                                 |
| 3 | `BUFFER_TIME`         | Constraint   | Yes          | bufferMinutes                              |
| 4 | `CAPACITY`            | Constraint   | Pre-computed | (none — uses capacity from Resource)       |
| 5 | `BLACKOUT_PERIOD`     | Constraint   | No           | startDate, endDate                         |
| 6 | `COOLDOWN`            | Constraint   | Yes          | cooldownHours                              |
| 7 | `APPROVAL_REQUIRED`   | Conditional  | No           | threshold                                  |
| 8 | `DEPENDENT_RESOURCE`  | Conditional  | Yes          | requiredResourceId                         |
| 9 | `USER_QUOTA`          | Conditional  | Yes          | maxBookings, periodDays                    |
| 10| `AUTO_CONFIRM`        | Modification | No           | maxGroupSize, startHour, endHour           |
| 11| `PEAK_PRICING`        | Modification | No           | multiplier, startHour, endHour, daysOfWeek |
| 12| `RECURRING_SLOT`      | Bonus        | No           | maxWeeks, dayOfWeek, weekCount (metadata)  |

Rules that need DB access (`BUFFER_TIME`, `COOLDOWN`, `DEPENDENT_RESOURCE`, `USER_QUOTA`) are Spring beans with
`BookingRepository` injected. The registry holds them as fully-wired beans.

### Recurring Slot Design

`RECURRING_SLOT` is unique — it is a constraint rule that validates recurrence metadata on the booking request (day
alignment, max week count). The actual recurring instance generation happens at the service layer:

1. `POST /tenants/{id}/bookings/recurring` accepts a `RecurringBookingRequest` with a base booking + recurrence pattern
2. `BookingService.createRecurringBookings` generates individual `BookingRequest` instances for each week
3. Each instance is validated independently via `createBatchBookings`, enabling partial success
4. The 207 Multi-Status response communicates per-item results to the client

---

## 6. Extensibility

Adding rule #10 requires exactly:

1. Create `com.flexbook.engine.impl.MyNewRule.kt` implementing `BookingRule` with `@Component`
2. Insert a row in `rule_configurations` with `rule_type = 'MY_NEW_RULE'`

No changes to: engine, registry, service, repository, API, or migration scripts.
