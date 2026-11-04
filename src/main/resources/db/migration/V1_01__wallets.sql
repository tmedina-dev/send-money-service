CREATE TABLE wallets (
    user_id BIGINT PRIMARY KEY,
    balance NUMERIC(19, 2) NOT NULL CHECK (balance >= 0)
);

-- User IDs reference the external directory; no local user profiles are stored.
-- Deterministic mock balances for development, in PHP.
INSERT INTO wallets (user_id, balance) VALUES
    (1, 10000.00),
    (2, 5000.00),
    (3, 2500.00),
    (4, 7500.00),
    (5, 12000.00),
    (6, 3000.00),
    (7, 8500.00),
    (8, 15000.00),
    (9, 4500.00),
    (10, 20000.00);
