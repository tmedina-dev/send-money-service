package com.send_money;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.send_money.directory.UserDirectoryClient;
import com.send_money.directory.UserDirectoryClient.UserProfile;
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

import static org.mockito.Mockito.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "USER_DIRECTORY_BASE_URL=https://directory.test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TransactionApiTests {
    private static final String SENT = "11111111-1111-1111-1111-111111111111";
    private static final String RECEIVED = "22222222-2222-2222-2222-222222222222";
    private static final String UNRELATED = "33333333-3333-3333-3333-333333333333";
    private static final String TIED = "44444444-4444-4444-4444-444444444444";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean UserDirectoryClient directory;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM money_transfers");
        seed(SENT, 1, 2, "150", "2026-11-04T01:00:00Z");
        seed(RECEIVED, 3, 1, "25", "2026-11-05T01:00:00Z");
        seed(UNRELATED, 2, 3, "50", "2026-11-05T02:00:00Z");
        seed(TIED, 1, 2, "10", "2026-11-05T01:00:00Z");
        when(directory.getUsers()).thenReturn(List.of(new UserProfile(1L, "Leanne Graham"),
                new UserProfile(2L, "Ervin Howell"), new UserProfile(3L, "Clementine Bauch")));
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM money_transfers");
    }

    @Test
    void listsOnlyOwnTransfersWithDirectionAndCounterpartyNames() throws Exception {
        mvc.perform(get("/api/v1/transactions").header("X-User-Id", "1").param("userId", "2"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.items[0].transferId").value(TIED))
                .andExpect(jsonPath("$.items[0].direction").value("SENT"))
                .andExpect(jsonPath("$.items[0].counterparty.userId").value(2))
                .andExpect(jsonPath("$.items[0].counterparty.name").value("Ervin Howell"))
                .andExpect(jsonPath("$.items[0].recipient").doesNotExist())
                .andExpect(jsonPath("$.items[1].transferId").value(RECEIVED))
                .andExpect(jsonPath("$.items[1].direction").value("RECEIVED"))
                .andExpect(jsonPath("$.items[1].counterparty.userId").value(3))
                .andExpect(jsonPath("$.items[2].transferId").value(SENT))
                .andExpect(jsonPath("$.items[*].idempotencyKey").isEmpty())
                .andExpect(jsonPath("$.items[*].senderBalance").isEmpty());
        verify(directory, times(1)).getUsers();
        verify(directory, never()).getUserName(anyLong());
    }

    @Test
    void paginatesWithStableOrdering() throws Exception {
        mvc.perform(get("/api/v1/transactions?page=2&size=1").header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].transferId").value(RECEIVED))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void emptyHistoryAvoidsExternalCall() throws Exception {
        mvc.perform(get("/api/v1/transactions").header("X-User-Id", "10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
        verifyNoInteractions(directory);
    }

    @Test
    void outOfRangePageReturnsEmptyItemsWithTotal() throws Exception {
        mvc.perform(get("/api/v1/transactions?page=100&size=20").header("X-User-Id", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(3));
        verifyNoInteractions(directory);
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=0", "page=-1", "size=0", "size=101", "size=abc", "page=2147483647&size=100"})
    void rejectsInvalidPagination(String query) throws Exception {
        mvc.perform(get("/api/v1/transactions?" + query).header("X-User-Id", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"detail\":\"Invalid request.\"}"));
        verifyNoInteractions(directory);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "2"})
    void bothParticipantsCanSeeDetailsWithoutBalancesOrRetryKeys(String caller) throws Exception {
        mvc.perform(get("/api/v1/transactions/" + SENT).header("X-User-Id", caller))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transferId").value(SENT))
                .andExpect(jsonPath("$.direction").value(caller.equals("1") ? "SENT" : "RECEIVED"))
                .andExpect(jsonPath("$.sender.userId").value(1))
                .andExpect(jsonPath("$.sender.name").value("Leanne Graham"))
                .andExpect(jsonPath("$.recipient.userId").value(2))
                .andExpect(jsonPath("$.recipient.name").value("Ervin Howell"))
                .andExpect(jsonPath("$.amount").value(150))
                .andExpect(jsonPath("$.senderBalance").doesNotExist())
                .andExpect(jsonPath("$.senderBalanceAfter").doesNotExist())
                .andExpect(jsonPath("$.idempotencyKey").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {UNRELATED, "99999999-9999-9999-9999-999999999999"})
    void unrelatedAndMissingTransactionsReturnSameNotFound(String id) throws Exception {
        mvc.perform(get("/api/v1/transactions/" + id).header("X-User-Id", "1"))
                .andExpect(status().isNotFound())
                .andExpect(content().json("{\"detail\":\"Requested resource not found.\"}"));
        verifyNoInteractions(directory);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/transactions", "/api/v1/transactions/" + SENT})
    void validatesIdentityForBothEndpoints(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isBadRequest());
        mvc.perform(get(path).header("X-User-Id", "abc")).andExpect(status().isBadRequest());
        mvc.perform(get(path).header("X-User-Id", "999")).andExpect(status().isNotFound());
        verifyNoInteractions(directory);
    }

    @Test
    void rejectsMalformedTransferId() throws Exception {
        mvc.perform(get("/api/v1/transactions/not-a-uuid").header("X-User-Id", "1"))
                .andExpect(status().isBadRequest()).andExpect(content().json("{\"detail\":\"Invalid request.\"}"));
        verifyNoInteractions(directory);
    }

    @Test
    void financialHistoryRemainsAvailableWhenDirectoryFails() throws Exception {
        when(directory.getUsers()).thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Directory unavailable"));
        mvc.perform(get("/api/v1/transactions").header("X-User-Id", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.items[0].counterparty.name").value(nullValue()));
        mvc.perform(get("/api/v1/transactions/" + SENT).header("X-User-Id", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(150))
                .andExpect(jsonPath("$.sender.name").value(nullValue()));
    }

    @Test
    void missingExternalProfileDoesNotHideTransfer() throws Exception {
        when(directory.getUsers()).thenReturn(List.of(new UserProfile(1L, "Leanne Graham")));
        mvc.perform(get("/api/v1/transactions/" + SENT).header("X-User-Id", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sender.name").value("Leanne Graham"))
                .andExpect(jsonPath("$.recipient.name").value(nullValue()));
    }

    @Test
    void swaggerDocumentsBothEndpoints() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/transactions'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/transactions/{id}'].get.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/transactions'].get.responses['502']").doesNotExist());
    }

    @Test
    void newlySentMoneyAppearsInHistoryAndDetails() throws Exception {
        BigDecimal senderBalance = jdbc.queryForObject("SELECT balance FROM wallets WHERE user_id=1", BigDecimal.class);
        BigDecimal recipientBalance = jdbc.queryForObject("SELECT balance FROM wallets WHERE user_id=2", BigDecimal.class);
        when(directory.getRecipientName(2)).thenReturn("Ervin Howell");
        try {
            var sent = mvc.perform(post("/api/v1/transfers").header("X-User-Id", "1")
                    .header("Idempotency-Key", "history-flow").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"recipientId\":2,\"amount\":10}"))
                    .andExpect(status().isCreated()).andReturn();
            String id = com.jayway.jsonpath.JsonPath.read(sent.getResponse().getContentAsString(), "$.transferId");
            mvc.perform(get("/api/v1/transactions").header("X-User-Id", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[*].transferId", hasItem(id)));
            mvc.perform(get("/api/v1/transactions/" + id).header("X-User-Id", "2"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(10))
                    .andExpect(jsonPath("$.direction").value("RECEIVED"));
        } finally {
            jdbc.update("UPDATE wallets SET balance=? WHERE user_id=1", senderBalance);
            jdbc.update("UPDATE wallets SET balance=? WHERE user_id=2", recipientBalance);
        }
    }

    private void seed(String id, long sender, long recipient, String amount, String timestamp) {
        jdbc.update("INSERT INTO money_transfers (id,sender_user_id,recipient_user_id,amount,created_at,idempotency_key,sender_balance_after) VALUES (?,?,?,?,?,?,?)",
                UUID.fromString(id), sender, recipient, new BigDecimal(amount), OffsetDateTime.parse(timestamp),
                "private-key-" + id, new BigDecimal("1000.00"));
    }
}
