package com.send_money.transfer;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface DailyTransferLimitRepository extends JpaRepository<DailyTransferLimit, Long> {
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select l from DailyTransferLimit l where l.userId = :userId")
    Optional<DailyTransferLimit> findForTransfer(@Param("userId") long userId);
}
