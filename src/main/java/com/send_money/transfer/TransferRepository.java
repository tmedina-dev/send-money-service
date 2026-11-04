package com.send_money.transfer;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferRepository extends JpaRepository<MoneyTransfer, UUID> {
    Optional<MoneyTransfer> findBySenderIdAndIdempotencyKey(long senderId, String idempotencyKey);
}
