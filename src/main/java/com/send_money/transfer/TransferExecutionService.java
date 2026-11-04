package com.send_money.transfer;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
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
    private final DailyTransferLimitRepository limits;
    private final Clock clock;
    private static final ZoneId DAILY_LIMIT_ZONE = ZoneId.of("Asia/Manila");

    public TransferExecutionService(WalletRepository wallets, TransferRepository transfers,
            DailyTransferLimitRepository limits, Clock clock) {
        this.wallets = wallets;
        this.transfers = transfers;
        this.limits = limits;
        this.clock = clock;
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
        // Capture one timestamp so the check and history row use the same calendar day.
        OffsetDateTime createdAt = OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        checkDailyLimit(senderId, request.amount(), createdAt);
        sender.debit(request.amount());
        recipient.credit(request.amount());
        MoneyTransfer transfer = transfers.saveAndFlush(new MoneyTransfer(senderId, recipientId,
                request.amount(), key, sender.getBalance(), createdAt));
        return new Result(transfer.toResponse(recipientName), true);
    }

    private Wallet lock(long userId) {
        return wallets.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User wallet not found"));
    }

    private void checkDailyLimit(long senderId, BigDecimal amount, OffsetDateTime createdAt) {
        DailyTransferLimit limit = limits.findForTransfer(senderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Daily transfer limit configuration missing"));
        var date = createdAt.atZoneSameInstant(DAILY_LIMIT_ZONE).toLocalDate();
        OffsetDateTime start = date.atStartOfDay(DAILY_LIMIT_ZONE).toOffsetDateTime();
        OffsetDateTime end = date.plusDays(1).atStartOfDay(DAILY_LIMIT_ZONE).toOffsetDateTime();
        BigDecimal sent = transfers.totalSent(senderId, start, end);
        if ((sent == null ? BigDecimal.ZERO : sent).add(amount).compareTo(limit.getAmount()) > 0) {
            throw new DailyTransferLimitExceededException();
        }
    }

    public record Result(TransferResponse response, boolean created) {
    }
}
