CREATE TABLE wallets (
    user_id BIGINT PRIMARY KEY,
    balance NUMERIC(19, 2) NOT NULL CHECK (balance >= 0)
);

-- User IDs reference the external directory; no local user profiles are stored.
-- Deterministic mock balances for development, in PHP.
INSERT INTO wallets (user_id, balance) VALUES
    (1, 150000.00),
    (2, 5020100.00),
    (3, 200500.00),
    (4, 756000.00),
    (5, 192000.00),
    (6, 321000.00),
    (7, 8500025.11),
    (8, 1500200.00),
    (9, 450000.00),
    (10, 250000.00);
