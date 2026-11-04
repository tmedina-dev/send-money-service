 

CREATE TABLE money_transfers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sender_user_id BIGINT NOT NULL REFERENCES wallets(user_id),
    recipient_user_id BIGINT NOT NULL REFERENCES wallets(user_id),
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'PHP',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT money_transfers_positive_amount CHECK (amount > 0),
    CONSTRAINT money_transfers_different_users CHECK (sender_user_id <> recipient_user_id),
    CONSTRAINT money_transfers_php_currency CHECK (currency = 'PHP')
);

-- Supports outgoing history/daily totals and incoming history, respectively.
CREATE INDEX money_transfers_sender_history_idx
    ON money_transfers (sender_user_id, created_at DESC, id DESC);
CREATE INDEX money_transfers_recipient_history_idx
    ON money_transfers (recipient_user_id, created_at DESC, id DESC);

