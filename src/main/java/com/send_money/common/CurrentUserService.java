package com.send_money.common;

import com.send_money.wallet.Wallet;
import com.send_money.wallet.WalletRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CurrentUserService {

    private final WalletRepository wallets;

    public CurrentUserService(WalletRepository wallets) {
        this.wallets = wallets;
    }

    public Wallet requireWallet(String header) {
        if (header == null || !header.matches("[1-9][0-9]*")) {
            throw invalidUserId();
        }
        long userId;
        try {
            userId = Long.parseLong(header);
        } catch (NumberFormatException ex) {
            throw invalidUserId();
        }
        return wallets.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User wallet not found"));
    }

    private ResponseStatusException invalidUserId() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-User-Id must be a positive integer");
    }
}
