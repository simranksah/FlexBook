# FlexBook Engine — Test Design

## Philosophy

We test **behaviors, not lines**. Each test asserts a specific outcome that matters to a user or operator:
- "A booking ending exactly at the boundary hour is allowed" (edge case)
- "A group of 10 triggers approval at threshold=10 (inclusive)" (boundary)
- "AutoConfirm at priority 100 overrides ApprovalRequired at priority 50" (composition)

Coverage is a by-product of this approach, not the goal.

---

## Test Layers

### 1. Unit Tests — Rule Implementations

Each `BookingRule` implementation is tested in complete isolation. No Spring context, no DB, no mocks of other rules.

**Pattern:**
```kotlin
val rule = TimeBoundaryRule()
val config = RuleConfig("TIME_BOUNDARY", mapOf("startHour" to 8, "endHour" to 18), priority = 10)
val result = rule.evaluate(context(startHour = 9, endHour = 17), config)
assertTrue(result is RuleResult.Success)
```

**What is covered per rule:**

| Rule | Key Scenarios Tested |
|---|---|
| `TimeBoundaryRule` | Within window → Success; before window → Failure; after window → Failure; end exactly on boundary → Success; missing params → Failure |
| `MaxDurationRule` | Under limit → Success; exactly at limit → Success; over limit → Failure; missing param → Failure |
| `BufferTimeRule` | No adjacent bookings → Success; booking ends in buffer before slot → Failure; booking starts in buffer after slot → Failure; adjacent but outside buffer → Success |
| `CapacityRule` | Below capacity → Success; at capacity → Failure; capacity of 1 (second booking) → Failure |
| `BlackoutPeriodRule` | Outside blackout → Success; on first day of blackout → Failure; on last day → Failure; day before → Success; bad date format → Failure |
| `CooldownRule` | No previous booking → Success; previous booking within cooldown → Failure; null userId skips → Success; missing param → Failure |
| `ApprovalRequiredRule` | Below threshold → Success; at threshold (inclusive) → Transition(PENDING); above → Transition(PENDING); no groupSize in metadata → Success (defaults to 0) |
| `DependentResourceRule` | Required resource booked → Success; required resource missing → Transition(PENDING); null userId skips → Success; invalid UUID → Failure |
| `UserQuotaRule` | Under quota → Success; at quota → Failure; no userId in context → Success (rule skips); missing params → Failure |
| `AutoConfirmRule` | Conditions met → Transition(CONFIRMED); group too large → Success; outside hours → Success; all defaults → Transition(CONFIRMED) |
| `PeakPricingRule` | In peak window → PriceModifier(multiplier); outside window → Success; wrong day → Success; correct day + window → PriceModifier; missing params → Failure |
| `RecurringSlotRule` | Valid config → Success; no recurrence metadata → Success; week exceeds max → Failure; day mismatch → Failure; invalid day string → Failure |

### 2. Service-Level Tests — Rule Composition

`BookingServiceTest` uses Mockito to mock all repositories and `RuleRegistry`. Tests verify that the service correctly orchestrates rules and handles their results.

**Key scenarios:**

| Scenario | Expected Outcome |
|---|---|
| No rules configured | Booking status = CONFIRMED |
| Single constraint passes | Status = CONFIRMED |
| Single constraint fails | `ValidationException` thrown |
| ApprovalRequired triggers | Status = PENDING |
| AutoConfirm (priority 100) + ApprovalRequired (priority 50) both active; small group | Status = CONFIRMED (AutoConfirm wins) |
| AutoConfirm (priority 100) + ApprovalRequired (priority 50); large group beyond AutoConfirm threshold | AutoConfirm returns Success, ApprovalRequired returns PENDING → Status = PENDING |
| PeakPricing rule active, priced resource | `totalPrice = basePrice × multiplier` |
| PeakPricing + unpriced resource | `totalPrice = null` |
| Two PriceModifier rules (2× and 1.5×) | `priceMultiplier = 3.0` (compounded) |
| Tenant not found | `NotFoundException` |
| Resource not found | `NotFoundException` |
| Batch: 3 bookings, 1 fails rule | Returns 3 results: [201, 422, 201] |
| Batch: all fail | Returns 3 results: all 422 |
| CooldownRule passes (no previous booking) | Status = CONFIRMED |
| CooldownRule blocks rebooking within window | `ValidationException` thrown |
| DependentResource passes (required resource booked) | Status = CONFIRMED |
| DependentResource transitions when dependency missing | Status = PENDING |
| Recurring booking creates correct number of instances | 3 instances created |
| Recurring booking preserves day of week | All instances on correct day |

### 3. Integration Tests — End-to-End with Testcontainers

`@SpringBootTest` with a real PostgreSQL container (Testcontainers). Tests exercise the full stack: HTTP → Controller → Service → Engine → DB.

**Setup:** `@Testcontainers` + `@Container` with a `PostgreSQLContainer`. Spring's `DynamicPropertySource` configures the datasource URL.

**Key scenarios:**

| Scenario | Verified Via |
|---|---|
| Full tenant setup (create tenant → resource → rule → booking) | POST then GET, assert response shape |
| Booking rejected by TIME_BOUNDARY | POST booking at 7am, assert 422 + error message |
| Booking routed to PENDING by APPROVAL_REQUIRED | POST with groupSize=15, assert status=PENDING |
| Overlapping booking rejected by CAPACITY | POST 3 bookings on a capacity-1 resource, 3rd → 422 |
| BUFFER_TIME enforced between two consecutive bookings | POST booking at 10:00–11:00, POST at 11:05 with 15-min buffer → 422 |
| Batch 207 partial success | POST batch of 2, one fails time boundary |
| GET /bookings filters work | Create 3 bookings, filter by userId, assert 1 returned |
| Price computed correctly | Priced resource + PeakPricing rule, assert totalPrice in response |

---

## Edge Cases Matrix

| Category | Edge Case | Expected |
|---|---|---|
| Time | Booking ending exactly on boundary hour (18:00:00) | Allowed (TimeBoundaryRule) |
| Time | Zero-duration booking (startTime == endTime) | Rejected by service (`require` guard before rule evaluation) |
| Capacity | Concurrent overlapping bookings at exact capacity | Last one rejected |
| Quota | Same user, booking cancelled then re-books | Cancelled bookings excluded from count |
| Buffer | Booking immediately adjacent (0 minutes gap, bufferMinutes=15) | Rejected |
| Buffer | Booking with exactly 15 minutes gap (bufferMinutes=15) | Allowed |
| Priority | AutoConfirm + ApprovalRequired, equal priority | Non-deterministic (document: don't set equal priorities for conflicting rules) |
| Blackout | Booking that spans blackout boundary (starts before, ends during) | Allowed (start-date keying) |
| Params | Rule config with empty params `{}` | Rule returns descriptive Failure for each missing required param |
| Scoping | Same rule type at tenant-wide + resource-specific scope | Both evaluated; both must pass |
| Price | basePrice rounds down for sub-hour bookings | Intentional (integer division) |
| Batch | Empty bookings list | Returns empty list with 207 |
| Cooldown | Booking exactly at cooldown boundary (24h + 0 min) | Allowed (same as buffer semantics) |
| Cooldown | Same user, different resource | Not blocked (cooldown is per-resource) |
| Cooldown | null userId | Rule passes (cannot verify) |
| Dependent | requiredResourceId is same as booking resource | Allowed (edge case, user is booking both) |
| Dependent | null userId | Rule passes (cannot verify) |
| Recurring | weekCount = 1 | Single booking created |
| Recurring | Base booking startTime day differs from dayOfWeek | First occurrence aligned to correct day |

---

## Running Tests

```bash
# Unit tests only (no DB required)
mvn test

# All tests including integration (requires Docker for Testcontainers)
mvn verify

# Single rule test
mvn test -pl . -Dtest=TimeBoundaryRuleTest

# With coverage report
mvn test jacoco:report
# Report at: target/site/jacoco/index.html
```
