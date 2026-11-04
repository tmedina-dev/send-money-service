package com.send_money.wallet;

import com.send_money.directory.UserDirectoryClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WalletService {

    private final WalletRepository wallets;
    private final UserDirectoryClient directory;

    public WalletService(WalletRepository wallets, UserDirectoryClient directory) {
        this.wallets = wallets;
        this.directory = directory;
    }

    public WalletBalanceResponse getBalance(String userIdHeader) {
        long userId = parseUserId(userIdHeader);
        Wallet wallet = wallets.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User wallet not found"));
        // Repository lookup finishes before HTTP I/O, keeping the DB transaction short.
        String userName = directory.getUserName(userId);
        return new WalletBalanceResponse(wallet.getUserId(), userName, wallet.getBalance());
    }

    private long parseUserId(String header) {
        if (header == null || !header.matches("[1-9][0-9]*")) {
            throw invalidUserId();
        }
        try {
            return Long.parseLong(header);
        } catch (NumberFormatException ex) {
            throw invalidUserId();
        }
    }

    private ResponseStatusException invalidUserId() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-User-Id must be a positive integer");
    }
}
