package com.send_money.transfer;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TransferTimeConfiguration {
    @Bean
    Clock transferClock() {
        return Clock.systemUTC();
    }
}
