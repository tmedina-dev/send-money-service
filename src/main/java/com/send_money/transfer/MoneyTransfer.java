package com.send_money.transfer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name = "money_transfers")
public class MoneyTransfer {
    @Id
    private UUID id;
    @Column(name = "sender_user_id", nullable = false)
    private long senderId;
    @Column(name = "recipient_user_id", nullable = false)
    private long recipientId;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;
    @Column(name = "sender_balance_after", precision = 19, scale = 2)
    private BigDecimal senderBalanceAfter;

    protected MoneyTransfer() {
    }

    public MoneyTransfer(long senderId, long recipientId, BigDecimal amount, String key, BigDecimal senderBalance,
            OffsetDateTime createdAt) {
        this.id = UUID.randomUUID();
        this.senderId = senderId;
        this.recipientId = recipientId;
        this.amount = amount;
        this.idempotencyKey = key;
        this.senderBalanceAfter = senderBalance;
        this.createdAt = createdAt;
    }

    public boolean matches(TransferRequest request) {
        return recipientId == request.recipientId() && amount.compareTo(request.amount()) == 0;
    }

    public TransferResponse toResponse(String recipientName) {
        return new TransferResponse(id, senderId, recipientId, recipientName, amount, senderBalanceAfter, createdAt);
    }
}
