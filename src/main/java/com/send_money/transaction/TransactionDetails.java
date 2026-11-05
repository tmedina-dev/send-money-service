package com.send_money.transaction;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TransactionDetails(UUID transferId, TransactionSummary.Direction direction,
        TransactionUser sender, TransactionUser recipient, BigDecimal amount, OffsetDateTime createdAt) {
}
