package com.send_money.transaction;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

public record TransactionSummary(UUID transferId, Direction direction,
        @Schema(description = "Other participant: the recipient for SENT transfers, or the sender for RECEIVED transfers") TransactionUser recipient,
        BigDecimal amount, OffsetDateTime createdAt) {
    public enum Direction { SENT, RECEIVED }
}
