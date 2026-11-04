package com.send_money.transfer;

import com.send_money.common.CurrentUserService;
import com.send_money.directory.UserDirectoryClient;
import com.send_money.wallet.WalletRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@Validated
public class TransferService {
    private static final Logger log = LoggerFactory.getLogger(TransferService.class);
    private final CurrentUserService currentUser;
    private final WalletRepository wallets;
    private final UserDirectoryClient directory;
    private final TransferExecutionService execution;

    public TransferService(CurrentUserService currentUser, WalletRepository wallets,
            UserDirectoryClient directory, TransferExecutionService execution) {
        this.currentUser = currentUser;
        this.wallets = wallets;
        this.directory = directory;
        this.execution = execution;
    }

    public TransferExecutionService.Result send(String header, @NotNull @Valid TransferRequest request, String key) {
        long senderId = currentUser.requireWallet(header).getUserId();
        if (key == null || !key.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing or invalid Idempotency-Key");
        }
        if (senderId == request.recipientId()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot transfer to yourself");
        }
        if (!wallets.existsById(request.recipientId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipient wallet not found");
        }
        // External validation finishes before the transactional service locks wallets.
        String recipientName = directory.getRecipientName(request.recipientId());
        TransferExecutionService.Result result = execution.execute(senderId, request, key, recipientName);
        // The transactional proxy has committed before this receipt is logged.
        log.info("money_transfer transferId={} senderId={} recipientId={} replay={}",
                result.response().transferId(), senderId, request.recipientId(), !result.created());
        return result;
    }
}
