CREATE TABLE daily_transfer_limits (
    user_id BIGINT PRIMARY KEY REFERENCES wallets(user_id),
    daily_limit NUMERIC(19, 2) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT daily_transfer_limits_nonnegative CHECK (daily_limit >= 0)
);

-- Demo daily outgoing transfer limits in PHP.
INSERT INTO daily_transfer_limits (user_id, daily_limit) VALUES
    (1, 50000.00),
    (2, 50000.00),
    (3, 50000.00),
    (4, 50000.00),
    (5, 50000.00),
    (6, 50000.00),
    (7, 50000.00),
    (8, 50000.00),
    (9, 50000.00),
    (10, 50000.00);
