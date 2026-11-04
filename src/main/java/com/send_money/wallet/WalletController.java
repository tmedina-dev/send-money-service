package com.send_money.wallet;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallet")
@Tag(name = "Wallet", description = "Current user's wallet balance")
public class WalletController {

    private final WalletService wallets;

    public WalletController(WalletService wallets) {
        this.wallets = wallets;
    }

    @GetMapping(produces = "application/json")
    @Operation(summary = "Get your wallet balance", description = "Combines the local balance with the external user's name. Returns only the header user's wallet.")
    @ApiResponse(responseCode = "200", description = "Wallet balance and external user name")
    public ResponseEntity<WalletBalanceResponse> getBalance(
            @RequestHeader(name = "X-User-Id", required = false) String userId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(wallets.getBalance(userId));
    }
}
