package com.send_money;

import org.springframework.boot.SpringApplication;

public class TestSendMoneyApplication {

	public static void main(String[] args) {
		SpringApplication.from(SendMoneyApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
