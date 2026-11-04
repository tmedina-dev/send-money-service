-- Existing history remains intact; new API transfers populate both columns.
ALTER TABLE money_transfers ADD COLUMN idempotency_key VARCHAR(64);
ALTER TABLE money_transfers ADD COLUMN sender_balance_after NUMERIC(19, 2)
    CHECK (sender_balance_after >= 0);
ALTER TABLE money_transfers ADD CONSTRAINT money_transfers_sender_idempotency_unique
    UNIQUE (sender_user_id, idempotency_key);
