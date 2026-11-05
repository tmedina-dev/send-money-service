package com.send_money.transaction;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.send_money.common.CurrentUserService;
import com.send_money.directory.UserDirectoryClient;
import com.send_money.transfer.MoneyTransfer;
import com.send_money.transfer.TransferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TransactionService {
    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);
    private final CurrentUserService currentUser;
    private final TransferRepository transfers;
    private final UserDirectoryClient directory;

    public TransactionService(CurrentUserService currentUser, TransferRepository transfers, UserDirectoryClient directory) {
        this.currentUser = currentUser;
        this.transfers = transfers;
        this.directory = directory;
    }

    public TransactionHistoryResponse history(String header, int page, int size) {
        long userId = currentUser.requireWallet(header).getUserId();
        if (page < 1 || size < 1 || size > 100 || ((long) page - 1) * size > Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination parameters");
        }
        var result = transfers.findHistory(userId,
                PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        Set<Long> counterparties = result.stream().map(t -> counterparty(t, userId)).collect(Collectors.toSet());
        Map<Long, String> names = namesFor(counterparties);
        var items = result.stream().map(t -> new TransactionSummary(t.getId(), direction(t, userId),
                user(counterparty(t, userId), names), t.getAmount(), t.getCreatedAt())).toList();
        return new TransactionHistoryResponse(items, page, size, result.getTotalElements(), result.getTotalPages());
    }

    public TransactionDetails details(String header, UUID id) {
        long userId = currentUser.requireWallet(header).getUserId();
        MoneyTransfer transfer = transfers.findVisibleById(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));
        Map<Long, String> names = namesFor(Set.of(transfer.getSenderId(), transfer.getRecipientId()));
        return new TransactionDetails(transfer.getId(), direction(transfer, userId),
                user(transfer.getSenderId(), names), user(transfer.getRecipientId(), names),
                transfer.getAmount(), transfer.getCreatedAt());
    }

    private Map<Long, String> namesFor(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        // One external lookup per response; never hold a database transaction during HTTP I/O.
        try {
            return directory.getUsers().stream().filter(profile -> ids.contains(profile.id()))
                    .collect(Collectors.toMap(UserDirectoryClient.UserProfile::id, UserDirectoryClient.UserProfile::name));
        } catch (ResponseStatusException ex) {
            log.warn("transaction_profile_enrichment_unavailable status={}", ex.getStatusCode().value());
            return Map.of();
        }
    }

    private long counterparty(MoneyTransfer transfer, long userId) {
        return transfer.getSenderId() == userId ? transfer.getRecipientId() : transfer.getSenderId();
    }

    private TransactionSummary.Direction direction(MoneyTransfer transfer, long userId) {
        return transfer.getSenderId() == userId ? TransactionSummary.Direction.SENT : TransactionSummary.Direction.RECEIVED;
    }

    private TransactionUser user(long id, Map<Long, String> names) {
        return new TransactionUser(id, names.get(id));
    }
}
