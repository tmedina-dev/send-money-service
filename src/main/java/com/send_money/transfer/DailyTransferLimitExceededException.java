package com.send_money.transfer;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class DailyTransferLimitExceededException extends ResponseStatusException {
    public static final String MESSAGE =
            "Daily transfer limit exceeded. Reduce the amount or try again tomorrow.";

    public DailyTransferLimitExceededException() {
        super(HttpStatus.CONFLICT, MESSAGE);
    }
}
