package com.send_money.transfer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TransferResponse(UUID transferId, long senderId, long recipientId, String recipientName,
        BigDecimal amount, BigDecimal senderBalance, OffsetDateTime createdAt) {
}
