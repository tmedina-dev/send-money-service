package com.send_money.transaction;

import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transactions")
@Tag(name = "Transactions", description = "Your sent and received transfer history. All amounts are PHP.")
public class TransactionController {
    private final TransactionService transactions;

    public TransactionController(TransactionService transactions) {
        this.transactions = transactions;
    }

    @GetMapping(produces = "application/json")
    @Operation(summary = "List your transaction history", description = "Returns sent and received transfers, newest first, with stable ID ordering for equal timestamps. The recipient field identifies the other participant (sender for received transfers). External names are null when unavailable.")
    @ApiResponse(responseCode = "200", description = "Paginated transaction history; items is empty if there are no matching records")
    public ResponseEntity<TransactionHistoryResponse> history(
            @RequestHeader(name = "X-User-Id", required = false) String userId,
            @Parameter(description = "Page number starting at 1", example = "1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Page size, from 1 to 100", example = "20") @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(transactions.history(userId, page, size));
    }

    @GetMapping(value = "/{id}", produces = "application/json")
    @Operation(summary = "Get transaction details", description = "Only the sender or recipient may view a transfer. Missing and unrelated transfers both return 404. Profile names are null when unavailable.")
    @ApiResponse(responseCode = "200", description = "Transfer details with participant names")
    public ResponseEntity<TransactionDetails> details(
            @RequestHeader(name = "X-User-Id", required = false) String userId, @PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(transactions.details(userId, id));
    }
}
