package com.send_money.transfer;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TransferRepository extends JpaRepository<MoneyTransfer, UUID> {
    Optional<MoneyTransfer> findBySenderIdAndIdempotencyKey(long senderId, String idempotencyKey);

    @Query("select sum(t.amount) from MoneyTransfer t where t.senderId = :senderId and t.createdAt >= :start and t.createdAt < :end")
    BigDecimal totalSent(@Param("senderId") long senderId,
            @Param("start") OffsetDateTime start, @Param("end") OffsetDateTime end);

    @Query("select t from MoneyTransfer t where t.senderId = :userId or t.recipientId = :userId")
    Page<MoneyTransfer> findHistory(@Param("userId") long userId, Pageable pageable);

    @Query("select t from MoneyTransfer t where t.id = :id and (t.senderId = :userId or t.recipientId = :userId)")
    Optional<MoneyTransfer> findVisibleById(@Param("id") UUID id, @Param("userId") long userId);
}
