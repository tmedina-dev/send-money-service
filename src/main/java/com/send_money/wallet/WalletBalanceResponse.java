package com.send_money.wallet;

import java.math.BigDecimal;
import io.swagger.v3.oas.annotations.media.Schema;

public record WalletBalanceResponse(
        @Schema(description = "Current user's external ID", example = "1") long userId,
        @Schema(description = "Name from the external directory", example = "Leanne Graham") String name,
        @Schema(description = "Current wallet balance in PHP", example = "10000.00") BigDecimal balance) {
}
