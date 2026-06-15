-- V2: Add user tracking and pricing fields

-- Resources: base price for PeakPricingRule
ALTER TABLE resources
    ADD COLUMN base_price_per_hour BIGINT;   -- smallest currency unit (paise, cents); NULL = unpriced

-- Bookings: user tracking for UserQuota/Cooldown rules
ALTER TABLE bookings
    ADD COLUMN user_id TEXT;

-- Bookings: pricing result fields
ALTER TABLE bookings
    ADD COLUMN base_price      BIGINT,           -- computed from resource.base_price_per_hour × duration
    ADD COLUMN price_multiplier DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    ADD COLUMN total_price     BIGINT;            -- = base_price × price_multiplier

-- Index for user quota queries
CREATE INDEX idx_bookings_user_quota
    ON bookings(tenant_id, user_id, start_time)
    WHERE status NOT IN ('CANCELLED', 'REJECTED');

-- Update seed conference room with a base price (10,000 paise = ₹100/hr)
UPDATE resources
SET base_price_per_hour = 10000
WHERE id = '00000000-0000-0000-0001-000000000001';
