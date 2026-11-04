CREATE TABLE daily_transfer_limits (
    user_id BIGINT PRIMARY KEY REFERENCES wallets(user_id),
    daily_limit NUMERIC(19, 2) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT daily_transfer_limits_nonnegative CHECK (daily_limit >= 0)
   
);

 
