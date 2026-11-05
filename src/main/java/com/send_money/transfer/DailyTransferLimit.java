package com.send_money.transfer;

import java.math.BigDecimal;
import jakarta.persistence.*;

@Entity
@Table(name = "daily_transfer_limits")
public class DailyTransferLimit {
    @Id
    @Column(name = "user_id")
    private Long userId;
    @Column(name = "daily_limit", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    protected DailyTransferLimit() {
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
