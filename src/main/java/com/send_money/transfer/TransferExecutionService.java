package com.send_money.transfer;

import java.math.BigDecimal;
import com.send_money.wallet.Wallet;
import com.send_money.wallet.WalletRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TransferExecutionService {
    private static final BigDecimal MAX_BALANCE = new BigDecimal("99999999999999999.99");
    private final WalletRepository wallets;
    private final TransferRepository transfers;

    public TransferExecutionService(WalletRepository wallets, TransferRepository transfers) {
        this.wallets = wallets;
        this.transfers = transfers;
    }

    @Transactional
    public Result execute(long senderId, TransferRequest request, String key, String recipientName) {
        long recipientId = request.recipientId();
        // Stable lock ordering prevents opposing transfers from locking wallets in reverse order.
        Wallet first = lock(Math.min(senderId, recipientId));
        Wallet second = lock(Math.max(senderId, recipientId));
        Wallet sender = senderId == first.getUserId() ? first : second;
        Wallet recipient = recipientId == first.getUserId() ? first : second;

        MoneyTransfer existing = transfers.findBySenderIdAndIdempotencyKey(senderId, key).orElse(null);
        if (existing != null) {
            if (!existing.matches(request)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key reused with a different payload");
            }
            return new Result(existing.toResponse(recipientName), false);
        }
        if (sender.getBalance().compareTo(request.amount()) < 0) {
            throw new InsufficientBalanceException();
        }
        if (recipient.getBalance().add(request.amount()).compareTo(MAX_BALANCE) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Recipient balance exceeds supported range");
        }
        sender.debit(request.amount());
        recipient.credit(request.amount());
        MoneyTransfer transfer = transfers.saveAndFlush(new MoneyTransfer(senderId, recipientId,
                request.amount(), key, sender.getBalance()));
        return new Result(transfer.toResponse(recipientName), true);
    }

    private Wallet lock(long userId) {
        return wallets.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User wallet not found"));
    }

    public record Result(TransferResponse response, boolean created) {
    }
}
