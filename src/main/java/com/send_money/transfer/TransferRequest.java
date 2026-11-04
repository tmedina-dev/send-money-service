package com.send_money.transfer;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import io.swagger.v3.oas.annotations.media.Schema;

public record TransferRequest(
        @NotNull @Positive @Schema(example = "2") Long recipientId,
        @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2)
        @Schema(description = "Positive PHP amount with at most two decimal places", example = "150.00") BigDecimal amount) {
}
