package com.send_money.transaction;

import java.util.List;

public record TransactionHistoryResponse(List<TransactionSummary> items, int page, int size,
        long totalElements, int totalPages) {
}
