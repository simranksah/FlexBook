-- FlexBook Engine — Initial Schema
-- All IDs are UUID. Timestamps are stored without timezone (application enforces UTC).

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ─── Tenants ──────────────────────────────────────────────────────────────────

CREATE TABLE tenants (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    name       TEXT        NOT NULL UNIQUE,
    enabled    BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP   NOT NULL DEFAULT now()
);

-- ─── Resources ────────────────────────────────────────────────────────────────

CREATE TABLE resources (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id  UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name       TEXT        NOT NULL,
    type       TEXT        NOT NULL,   -- e.g. ROOM, DESK, VEHICLE
    capacity   INTEGER     NOT NULL DEFAULT 1 CHECK (capacity > 0),
    enabled    BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX idx_resources_tenant_id ON resources(tenant_id);
CREATE INDEX idx_resources_type      ON resources(type);

-- ─── Bookings ─────────────────────────────────────────────────────────────────

CREATE TABLE bookings (
    id            UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id     UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    resource_id   UUID        NOT NULL REFERENCES resources(id) ON DELETE CASCADE,
    start_time    TIMESTAMP   NOT NULL,
    end_time      TIMESTAMP   NOT NULL,
    status        TEXT        NOT NULL DEFAULT 'CONFIRMED'
                              CHECK (status IN ('CONFIRMED','PENDING','CANCELLED','REJECTED')),
    status_reason TEXT,
    metadata      JSONB       NOT NULL DEFAULT '{}',
    created_at    TIMESTAMP   NOT NULL DEFAULT now(),
    CONSTRAINT bookings_time_check CHECK (end_time > start_time)
);

CREATE INDEX idx_bookings_tenant_id   ON bookings(tenant_id);
CREATE INDEX idx_bookings_resource_id ON bookings(resource_id);
CREATE INDEX idx_bookings_status      ON bookings(status);
-- Partial index for fast overlapping query (excludes terminal statuses)
CREATE INDEX idx_bookings_active_slot ON bookings(resource_id, start_time, end_time)
    WHERE status NOT IN ('CANCELLED', 'REJECTED');

-- ─── Rule Configurations ──────────────────────────────────────────────────────

CREATE TABLE rule_configurations (
    id            UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id     UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    resource_id   UUID        REFERENCES resources(id) ON DELETE CASCADE,  -- NULL = not resource-specific
    resource_type TEXT,                                                      -- NULL = all types
    rule_type     TEXT        NOT NULL,   -- Matches BookingRule.type
    params        JSONB       NOT NULL DEFAULT '{}',
    priority      INTEGER     NOT NULL DEFAULT 0,
    enabled       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX idx_rule_cfg_tenant_id   ON rule_configurations(tenant_id);
CREATE INDEX idx_rule_cfg_resource_id ON rule_configurations(resource_id);

-- ─── Seed data (example tenant + resource + rules) ───────────────────────────

INSERT INTO tenants (id, name) VALUES
    ('00000000-0000-0000-0000-000000000001', 'acme-corp');

INSERT INTO resources (id, tenant_id, name, type, capacity) VALUES
    ('00000000-0000-0000-0001-000000000001', '00000000-0000-0000-0000-000000000001', 'Main Conference Room', 'ROOM', 3),
    ('00000000-0000-0000-0001-000000000002', '00000000-0000-0000-0000-000000000001', 'Desk A1', 'DESK', 1);

-- Tenant-wide time boundary (applies to all resources of acme-corp)
INSERT INTO rule_configurations (tenant_id, rule_type, params, priority) VALUES
    ('00000000-0000-0000-0000-000000000001', 'TIME_BOUNDARY',
     '{"startHour": 8, "endHour": 18}', 10);

-- Room-type approval rule: groups > 10 require approval
INSERT INTO rule_configurations (tenant_id, resource_type, rule_type, params, priority) VALUES
    ('00000000-0000-0000-0000-000000000001', 'ROOM', 'APPROVAL_REQUIRED',
     '{"threshold": 10}', 20);

-- Capacity rule for the specific conference room (no extra params)
INSERT INTO rule_configurations (tenant_id, resource_id, rule_type, params, priority) VALUES
    ('00000000-0000-0000-0000-000000000001',
     '00000000-0000-0000-0001-000000000001',
     'CAPACITY', '{}', 30);
