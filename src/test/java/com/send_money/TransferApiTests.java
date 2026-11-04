package com.send_money;

import java.math.BigDecimal;
import java.util.concurrent.*;

import com.send_money.directory.UserDirectoryClient;
import com.send_money.transfer.TransferRequest;
import com.send_money.transfer.TransferService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "USER_DIRECTORY_BASE_URL=https://directory.test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TransferApiTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransferService service;
    @MockitoBean UserDirectoryClient directory;

    @BeforeEach
    void setUp() {
        resetBalances();
        when(directory.getRecipientName(2)).thenReturn("Ervin Howell");
        when(directory.getRecipientName(3)).thenReturn("Clementine Bauch");
    }

    @AfterEach
    void cleanUp() {
        resetBalances();
    }

    private void resetBalances() {
        jdbc.update("DELETE FROM money_transfers");
        jdbc.update("UPDATE wallets SET balance = CASE user_id WHEN 1 THEN 10000.00 WHEN 2 THEN 5000.00 ELSE 2500.00 END WHERE user_id IN (1,2,3)");
    }

    @Test
    void commitsBothWalletsAndHistory() throws Exception {
        send("1", "first-payment", "{\"recipientId\":2,\"amount\":150.00}", 201)
                .andExpect(jsonPath("$.transferId").isNotEmpty())
                .andExpect(jsonPath("$.senderId").value(1))
                .andExpect(jsonPath("$.recipientName").value("Ervin Howell"))
                .andExpect(jsonPath("$.amount").value(150.00))
                .andExpect(jsonPath("$.senderBalance").value(9850.00))
                .andExpect(header().exists("X-Request-Id"));
        assertBalances("9850.00", "5150.00");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM money_transfers WHERE sender_user_id=1 AND recipient_user_id=2 AND amount=150.00", Long.class)).isEqualTo(1);
    }

    @Test
    void allowsSendingEntireBalance() throws Exception {
        send("1", "entire-balance", "{\"recipientId\":2,\"amount\":10000.00}", 201);
        assertBalances("0", "15000");
    }

    @Test
    void rejectsInsufficientFundsWithoutChangingData() throws Exception {
        send("1", "too-much", "{\"recipientId\":2,\"amount\":10000.01}", 409)
                .andExpect(content().json("{\"detail\":\"Insufficient balance. Your wallet balance is too low to complete this transfer.\"}",
                        org.springframework.test.json.JsonCompareMode.STRICT));
        assertUnchanged();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"recipientId\":2}", "{\"recipientId\":2,\"amount\":0}",
            "{\"recipientId\":2,\"amount\":-1}", "{\"recipientId\":2,\"amount\":0.001}",
            "{\"recipientId\":0,\"amount\":1}", "{\"recipientId\":1,\"amount\":1}",
            "{\"recipientId\":2,\"amount\":100000000000000000}", "not json"})
    void rejectsInvalidPayload(String payload) throws Exception {
        send("1", "invalid-payload", payload, 400)
                .andExpect(content().json("{\"detail\":\"Invalid request.\"}"));
        verifyNoInteractions(directory);
        assertUnchanged();
    }

    @Test
    void rejectsUnknownRecipient() throws Exception {
        send("1", "invalid-recipient", "{\"recipientId\":999,\"amount\":1}", 404);
        verifyNoInteractions(directory);
        assertUnchanged();
    }

    @Test
    void rejectsMissingIdentityAndKey() throws Exception {
        mvc.perform(post("/api/v1/transfers").contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "missing-user").content("{\"recipientId\":2,\"amount\":1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/transfers").contentType(MediaType.APPLICATION_JSON)
                .header("X-User-Id", "1").content("{\"recipientId\":2,\"amount\":1}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(directory);
        assertUnchanged();
    }

    @Test
    void rejectsUnknownSender() throws Exception {
        send("999", "unknown-sender", "{\"recipientId\":2,\"amount\":1}", 404);
        verifyNoInteractions(directory);
        assertUnchanged();
    }

    @Test
    void externalFailureDoesNotMoveMoney() throws Exception {
        when(directory.getRecipientName(2)).thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Directory unavailable"));
        send("1", "external-failure", "{\"recipientId\":2,\"amount\":1}", 502);
        assertUnchanged();
    }

    @Test
    void retryReturnsOriginalReceiptWithoutAnotherDebit() throws Exception {
        var first = send("1", "same-key", "{\"recipientId\":2,\"amount\":150}", 201).andReturn();
        // Another payment must not alter the balance snapshot in the original receipt.
        send("1", "different-key", "{\"recipientId\":2,\"amount\":50}", 201);
        send("1", "same-key", "{\"recipientId\":2,\"amount\":150.00}", 200)
                .andExpect(content().json(first.getResponse().getContentAsString()));
        assertBalances("9800", "5200");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM money_transfers", Long.class)).isEqualTo(2);
    }

    @Test
    void rejectsKeyReuseWithDifferentPayload() throws Exception {
        send("1", "same-key", "{\"recipientId\":2,\"amount\":150}", 201);
        send("1", "same-key", "{\"recipientId\":3,\"amount\":150}", 409)
                .andExpect(content().json("{\"detail\":\"Unable to process your request.\"}"));
        assertBalances("9850", "5150");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM money_transfers", Long.class)).isEqualTo(1);
    }

    @Test
    void storageFailureRollsBackWalletUpdatesAndHistory() throws Exception {
        jdbc.execute("ALTER TABLE money_transfers ADD CONSTRAINT test_reject_amount CHECK (amount <> 123.45)");
        try {
            send("1", "rollback", "{\"recipientId\":2,\"amount\":123.45}", 500)
                    .andExpect(content().json("{\"detail\":\"Unable to process your request.\"}"));
            assertUnchanged();
        } finally {
            jdbc.execute("ALTER TABLE money_transfers DROP CONSTRAINT test_reject_amount");
        }
    }

    @Test
    void concurrentPaymentsCannotOverdraw() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> first = pool.submit(() -> concurrentSend(start, "concurrent-1", 2L));
            Future<Integer> second = pool.submit(() -> concurrentSend(start, "concurrent-2", 3L));
            start.countDown();
            assertThat(java.util.List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
            assertThat(balance(1)).isEqualByComparingTo("3000");
            assertThat(jdbc.queryForObject("SELECT SUM(balance) FROM wallets WHERE user_id IN (1,2,3)", BigDecimal.class))
                    .isEqualByComparingTo("17500");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM money_transfers", Long.class)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentRetriesDebitOnlyOnce() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> first = pool.submit(() -> concurrentSend(start, "shared-key", 2L));
            Future<Integer> second = pool.submit(() -> concurrentSend(start, "shared-key", 2L));
            start.countDown();
            assertThat(java.util.List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 200);
            assertBalances("3000", "12000");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM money_transfers", Long.class)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void swaggerDocumentsTransferRequestAndRetryHeader() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/transfers'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/transfers'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/transfers'].post.parameters[?(@.name == 'Idempotency-Key')].required")
                        .value(org.hamcrest.Matchers.contains(true)));
    }

    private int concurrentSend(CountDownLatch start, String key, long recipient) throws InterruptedException {
        start.await();
        try {
            return service.send("1", new TransferRequest(recipient, new BigDecimal("7000")), key).created() ? 201 : 200;
        } catch (ResponseStatusException ex) {
            return ex.getStatusCode().value();
        }
    }

    private org.springframework.test.web.servlet.ResultActions send(String user, String key, String body, int status) throws Exception {
        return mvc.perform(post("/api/v1/transfers").header("X-User-Id", user).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().is(status));
    }

    private BigDecimal balance(long user) {
        return jdbc.queryForObject("SELECT balance FROM wallets WHERE user_id = ?", BigDecimal.class, user);
    }

    private void assertBalances(String sender, String recipient) {
        assertThat(balance(1)).isEqualByComparingTo(sender);
        assertThat(balance(2)).isEqualByComparingTo(recipient);
    }

    private void assertUnchanged() {
        assertBalances("10000", "5000");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM money_transfers", Long.class)).isZero();
    }
}
