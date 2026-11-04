package com.send_money.transfer;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class InsufficientBalanceException extends ResponseStatusException {
    public static final String MESSAGE =
            "Insufficient balance. Your wallet balance is too low to complete this transfer.";

    public InsufficientBalanceException() {
        super(HttpStatus.CONFLICT, MESSAGE);
    }
}
