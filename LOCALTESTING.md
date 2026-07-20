# FlexBook — Local API Testing Guide

## Prerequisites

- PostgreSQL running locally on port 5432
- A database named `flexbook` (or whatever `FLEXBOOK_DB_URL` points to)
- Java 17+ and Maven installed

## Setup

```bash
# 1. Create the database
createdb flexbook

# 2. Set env vars (optional — defaults: localhost:5432/flexbook, user/pass = flexbook)
export FLEXBOOK_DB_URL=jdbc:postgresql://localhost:5432/flexbook
export FLEXBOOK_DB_USER=flexbook
export FLEXBOOK_DB_PASS=flexbook

# 3. Start the app (Flyway auto-creates schema + seed data)
mvn spring-boot:run
```

The app starts on **http://localhost:8080**.

The seed data (from `V1__initial_schema.sql`) creates one tenant with one room + one desk and a few pre-configured rules. All IDs below use the seed UUIDs.

---

## API Reference — All Endpoints with curl

### 1. Tenants

#### Create tenant
```bash
curl -s -X POST http://localhost:8080/tenants \
  -H "Content-Type: application/json" \
  -d '{"name": "my-company"}' | jq
```

#### Get tenant
```bash
curl -s http://localhost:8080/tenants/00000000-0000-0000-0000-000000000001 | jq
```

---

### 2. Resources

#### Create resource
```bash
curl -s -X POST http://localhost:8080/tenants/00000000-0000-0000-0000-000000000001/resources \
  -H "Content-Type: application/json" \
  -d '{"name": "Meeting Room B", "type": "ROOM", "capacity": 6, "basePricePerHour": 15000}' | jq
```

#### Get resource
```bash
curl -s http://localhost:8080/tenants/00000000-0000-0000-0000-000000000001/resources/00000000-0000-0001-0000-000000000001 | jq
```

---

### 3. Rules

#### Create rule
Rule types and their required params:

| Rule Type | Description | Params |
|-----------|-------------|--------|
| `TIME_BOUNDARY` | Restricts bookings to a time window (e.g. 8 AM – 6 PM). Rejects out-of-hours. | `{"startHour": 8, "endHour": 18}` |
| `MAX_DURATION` | Caps booking length in minutes. Rejects if exceeded. | `{"maxMinutes": 240}` |
| `BUFFER_TIME` | Enforces a gap between consecutive bookings for the same resource. Queries DB for overlap. | `{"bufferMinutes": 15}` |
| `CAPACITY` | Ensures group size doesn't exceed the resource's capacity. Reads capacity from the Resource entity. | `{}` |
| `BLACKOUT_PERIOD` | Rejects bookings that fall within a date range (e.g. holidays). | `{"startDate": "2025-12-25", "endDate": "2026-01-03"}` |
| `APPROVAL_REQUIRED` | If group size exceeds a threshold, transitions the booking to PENDING (requires manual approval). | `{"threshold": 10}` |
| `USER_QUOTA` | Limits how many active bookings a user can have within a rolling window. Queries DB for count. | `{"maxBookings": 2, "periodDays": 7}` |
| `AUTO_CONFIRM` | Automatically confirms bookings that meet conditions (small group, business hours). Use with higher priority to override APPROVAL_REQUIRED. | `{"maxGroupSize": 5, "startHour": 9, "endHour": 17}` |
| `PEAK_PRICING` | Applies a price multiplier during peak hours (optionally on specific days). | `{"multiplier": 2.0, "startHour": 17, "endHour": 21}` |
| `COOLDOWN` | Prevents a user from booking the same resource again within N hours after a booking ends. Queries DB. | `{"cooldownHours": 24}` |
| `DEPENDENT_RESOURCE` | Requires that another specific resource is also booked in the same slot (e.g. projector with room). Queries DB. | `{"requiredResourceId": "00000000-0000-0001-0000-000000000002"}` |
| `RECURRING_SLOT` | Validates recurrence metadata — day alignment and max weeks. The service layer generates individual instances. | `{"maxWeeks": 12}` |

```bash
# TIME_BOUNDARY — bookings only allowed 8:00–18:00
curl -s -X POST http://localhost:8080/tenants/00000000-0000-0000-0000-000000000001/rules \
  -H "Content-Type: application/json" \
  -d '{"ruleType": "TIME_BOUNDARY", "params": {"startHour": 8, "endHour": 18}, "priority": 10}' | jq

# APPROVAL_REQUIRED — groups > 10 need approval (becomes PENDING)
curl -s -X POST http://localhost:8080/tenants/00000000-0000-0000-0000-000000000001/rules \
  -H "Content-Type: application/json" \
  -d '{"ruleType": "APPROVAL_REQUIRED", "params": {"threshold": 10}, "priority": 50}' | jq

# AUTO_CONFIRM — small groups (≤5) during business hours skip approval (higher priority)
curl -s -X POST http://localhost:8080/tenants/00000000-0000-0000-0000-000000000001/rules \
  -H "Content-Type: application/json" \
  -d '{"ruleType": "AUTO_CONFIRM", "params": {"maxGroupSize": 5, "startHour": 9, "endHour": 17}, "priority": 100}' | jq

# PEAK_PRICING — 2× multiplier 17:00–21:00
curl -s -X POST http://localhost:8080/tenants/00000000-0000-0000-0000-000000000001/rules \
  -H "Content-Type: application/json" \
  -d '{"ruleType": "PEAK_PRICING", "params": {"multiplier": 2.0, "startHour": 17, "endHour": 21}, "priority": 5}' | jq

# MAX_DURATION — 4-hour max
curl -s -X POST http://localhost:8080/tenants/00000000-0000-0000-0000-000000000001/rules \
  -H "Content-Type: application/json" \
  -d '{"ruleType": "MAX_DURATION", "params": {"maxMinutes": 240}, "priority": 10}' | jq

# Resource-specific rule example — buffer time only for the conference room
curl -s -X POST http://localhost:8080/tenants/00000000-0000-0000-0000-000000000001/rules \
  -H "Content-Type: application/json" \
  -d '{"ruleType": "BUFFER_TIME", "params": {"bufferMinutes": 30}, "priority": 10, "resourceId": "00000000-0000-0001-0000-000000000001"}' | jq

# Resource-type rule — all ROOMs have capacity constraint
curl -s -X POST http://localhost:8080/tenants/00000000-0000-0000-0000-000000000001/rules \
  -H "Content-Type: application/json" \
  -d '{"ruleType": "CAPACITY", "params": {}, "priority": 10, "resourceType": "ROOM"}' | jq
```

#### List rules
```bash
curl -s http://localhost:8080/tenants/00000000-0000-0000-0000-000000000001/rules | jq
```

#### Delete rule
```bash
curl -s -X DELETE http://localhost:8080/tenants/00000000-0000-0000-0000-000000000001/rules/<RULE_UUID> | jq
```

---

### 4. Bookings

#### Create single booking

```bash
# Simple booking that passes all rules (within 8-18, small group)
curl -s -X POST http://localhost:8080/tenants/00000000-0000-0000-0000-000000000001/bookings \
  -H "Content-Type: application/json" \
  -d '{
    "resourceId": "00000000-0000-0001-0000-000000000001",
    "startTime": "2026-06-15T10:00:00",
    "endTime": "2026-06-15T11:30:00",
    "userId": "user-42"
  }' | jq
```

```bash
# Booking with metadata (groupSize triggers rules)
curl -s -X POST http://localhost:8080/tenants/00000000-0000-0000-0000-000000000001/bookings \
  -H "Content-Type: application/json" \
  -d '{
    "resourceId": "00000000-0000-0001-0000-000000000001",
    "startTime": "2026-06-15T10:00:00",
    "endTime": "2026-06-15T12:00:00",
    "userId": "user-99",
    "metadata": {"groupSize": 12}
  }' | jq
# → might return PENDING (requires approval for group > 10)
```

```bash
# Booking during peak hours → price multiplier applied
curl -s -X POST http://localhost:8080/tenants/00000000-0000-0000-0000-000000000001/bookings \
  -H "Content-Type: application/json" \
  -d '{
    "resourceId": "00000000-0000-0001-0000-000000000001",
    "startTime": "2026-06-15T18:00:00",
    "endTime": "2026-06-15T19:00:00",
    "userId": "user-7"
  }' | jq
# → priceMultiplier: 2.0, totalPrice: 20000 (10000 base × 2 hrs × 2.0)
```

```bash
# Booking that violates TIME_BOUNDARY (starts at 7 AM)
curl -s -X POST http://localhost:8080/tenants/00000000-0000-0000-0000-000000000001/bookings \
  -H "Content-Type: application/json" \
  -d '{
    "resourceId": "00000000-0000-0001-0000-000000000001",
    "startTime": "2026-06-15T07:00:00",
    "endTime": "2026-06-15T08:00:00",
    "userId": "user-1"
  }' | jq
# → HTTP 422, error message from TIME_BOUNDARY rule
```

#### Batch bookings
```bash
curl -s -X POST http://localhost:8080/tenants/00000000-0000-0000-0000-000000000001/bookings/batch \
  -H "Content-Type: application/json" \
  -d '{
    "bookings": [
      {
        "resourceId": "00000000-0000-0001-0000-000000000001",
        "startTime": "2026-06-16T09:00:00",
        "endTime": "2026-06-16T10:00:00",
        "userId": "user-1"
      },
      {
        "resourceId": "00000000-0000-0001-0000-000000000001",
        "startTime": "2026-06-16T07:00:00",
        "endTime": "2026-06-16T08:00:00",
        "userId": "user-1"
      }
    ]
  }' | jq
# → HTTP 207: first succeeds (201), second fails (422 - time boundary)
```

#### Recurring bookings
```bash
curl -s -X POST http://localhost:8080/tenants/00000000-0000-0000-0000-000000000001/bookings/recurring \
  -H "Content-Type: application/json" \
  -d '{
    "booking": {
      "resourceId": "00000000-0000-0001-0000-000000000001",
      "startTime": "2026-06-15T10:00:00",
      "endTime": "2026-06-15T11:00:00",
      "userId": "user-1"
    },
    "recurrence": {
      "dayOfWeek": "MONDAY",
      "weekCount": 4
    }
  }' | jq
# → HTTP 207: 4 items, each independently evaluated
```

#### Query bookings
```bash
# All bookings for a tenant
curl -s "http://localhost:8080/tenants/00000000-0000-0000-0000-000000000001/bookings" | jq

# Filtered by resource
curl -s "http://localhost:8080/tenants/00000000-0000-0000-0000-000000000001/bookings?resourceId=00000000-0000-0001-0000-000000000001" | jq

# Filtered by status
curl -s "http://localhost:8080/tenants/00000000-0000-0000-0000-000000000001/bookings?status=PENDING" | jq

# Filtered by user + time range + paginated
curl -s "http://localhost:8080/tenants/00000000-0000-0000-0000-000000000001/bookings?userId=user-1&from=2026-06-01T00:00:00&to=2026-06-30T23:59:59&page=0&size=10" | jq
```

#### Get single booking
```bash
curl -s http://localhost:8080/tenants/00000000-0000-0000-0000-000000000001/bookings/<BOOKING_UUID> | jq
```

#### Cancel booking
```bash
curl -s -X DELETE http://localhost:8080/tenants/00000000-0000-0000-0000-000000000001/bookings/<BOOKING_UUID> | jq
```

---

## Seed Data Quick Reference

The seed data provides a ready-to-use tenant with resources and rules:

| Entity | ID | Name |
|--------|----|------|
| Tenant | `00000000-0000-0000-0000-000000000001` | acme-corp |
| Room | `00000000-0000-0001-0000-000000000001` | Main Conference Room (cap: 3, ₹100/hr) |
| Desk | `00000000-0000-0001-0000-000000000002` | Desk A1 (cap: 1, unpriced) |

Pre-configured rules on the seed tenant:
1. **TIME_BOUNDARY** (priority 10) — 08:00–18:00 only
2. **APPROVAL_REQUIRED** (priority 20, type=ROOM) — groups > 10 require approval
3. **CAPACITY** (priority 30, resource=Room) — enforces room capacity
