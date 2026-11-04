package com.send_money;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "USER_DIRECTORY_BASE_URL=https://directory.test")
class SendMoneyApplicationTests {

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void migrationsCreateDemoWallets() {
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wallets", Long.class)).isEqualTo(10L);
		assertThat(jdbc.queryForObject("SELECT balance FROM wallets WHERE user_id = 1", BigDecimal.class))
				.isEqualByComparingTo("10000.00");
	}

	@Test
	void migrationConfiguresEachExistingWallet() {
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM daily_transfer_limits", Long.class)).isEqualTo(10L);
		assertThat(jdbc.queryForObject(
				"SELECT daily_limit FROM daily_transfer_limits WHERE user_id = 1", BigDecimal.class))
				.isEqualByComparingTo("50000.00");
	}

	@Test
	@Transactional
	void transferHistoryStoresSenderRecipientAndAmount() {
		jdbc.update("INSERT INTO money_transfers (sender_user_id, recipient_user_id, amount) VALUES (1, 2, 150.00)");
		assertThat(jdbc.queryForObject(
				"SELECT amount FROM money_transfers WHERE sender_user_id = 1 AND recipient_user_id = 2",
				BigDecimal.class)).isEqualByComparingTo("150.00");
	}

	@Test
	@Transactional
	void transferHistoryRejectsSelfTransfers() {
		assertThatThrownBy(() -> jdbc.update(
				"INSERT INTO money_transfers (sender_user_id, recipient_user_id, amount) VALUES (1, 1, 150.00)"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@Transactional
	void dailyLimitsRejectNegativeAmounts() {
		assertThatThrownBy(() -> jdbc.update("UPDATE daily_transfer_limits SET daily_limit = -1 WHERE user_id = 1"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@Transactional
	void financialRecordsCanBeCreatedWithoutCurrency() {
		jdbc.update("INSERT INTO wallets (user_id, balance) VALUES (11, 100.00)");
		jdbc.update("INSERT INTO daily_transfer_limits (user_id, daily_limit) VALUES (11, 500.00)");
		jdbc.update("INSERT INTO money_transfers (sender_user_id, recipient_user_id, amount) VALUES (1, 11, 10.00)");
		assertThat(jdbc.queryForObject(
				"SELECT amount FROM money_transfers WHERE recipient_user_id = 11", BigDecimal.class))
				.isEqualByComparingTo("10.00");
	}

	@Test
	void migrationRemovesRedundantCurrencyColumns() {
		assertThat(jdbc.queryForObject("""
				SELECT COUNT(*) FROM information_schema.columns
				WHERE table_schema = 'public'
				AND table_name IN ('wallets', 'money_transfers', 'daily_transfer_limits')
				AND column_name = 'currency'
				""", Long.class)).isZero();
	}

}
