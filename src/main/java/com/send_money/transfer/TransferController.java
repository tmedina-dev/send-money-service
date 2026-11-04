package com.send_money.transfer;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transfers")
@Tag(name = "Transfers", description = "Send PHP to an eligible recipient")
public class TransferController {
    private final TransferService transfers;

    public TransferController(TransferService transfers) {
        this.transfers = transfers;
    }

    @PostMapping(consumes = "application/json", produces = "application/json")
    @Operation(summary = "Send money", description = "Validates the recipient, balance, and configured daily outgoing limit (Asia/Manila), then atomically updates wallets and money_transfers. Reusing a key with the same payload returns the original receipt without counting against the limit again.")
    @ApiResponse(responseCode = "201", description = "Transfer committed")
    @ApiResponse(responseCode = "200", description = "Existing transfer returned for an idempotent retry")
    public ResponseEntity<TransferResponse> send(
            @RequestHeader(name = "X-User-Id", required = false) String userId,
            @Parameter(required = true, description = "Unique retry key per sender; 1-64 letters, digits, dots, underscores or hyphens", example = "transfer-001")
            @RequestHeader(name = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody TransferRequest request) {
        TransferExecutionService.Result result = transfers.send(userId, request, key);
        return ResponseEntity.status(result.created() ? 201 : 200).cacheControl(CacheControl.noStore()).body(result.response());
    }
}
