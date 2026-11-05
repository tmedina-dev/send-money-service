package com.send_money.transaction;

import io.swagger.v3.oas.annotations.media.Schema;

public record TransactionUser(long userId,
        @Schema(description = "Current external profile name; null if unavailable") String name) {
}
