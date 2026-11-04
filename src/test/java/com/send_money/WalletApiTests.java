package com.send_money;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.send_money.directory.UserDirectoryClient;

import static org.mockito.Mockito.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "USER_DIRECTORY_BASE_URL=https://directory.test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class WalletApiTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    @MockitoBean
    UserDirectoryClient directory;

    @Test
    void returnsOnlyTheHeaderUsersBalance() throws Exception {
        when(directory.getUserName(1)).thenReturn("Leanne Graham");
        mvc.perform(get("/api/v1/wallet").header("X-User-Id", "1").param("userId", "2"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.name").value("Leanne Graham"))
                .andExpect(jsonPath("$.balance").value(storedBalance(1)));
    }

    @Test
    void returnsAnotherUsersOwnBalance() throws Exception {
        when(directory.getUserName(2)).thenReturn("Ervin Howell");
        mvc.perform(get("/api/v1/wallet").header("X-User-Id", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(2))
                .andExpect(jsonPath("$.name").value("Ervin Howell"))
                .andExpect(jsonPath("$.balance").value(storedBalance(2)));
    }

    @Test
    void rejectsMissingIdentity() throws Exception {
        mvc.perform(get("/api/v1/wallet"))
                .andExpect(status().isBadRequest())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(content().json("{\"detail\":\"Invalid request.\"}",
                        org.springframework.test.json.JsonCompareMode.STRICT))
                .andExpect(jsonPath("$.detail").value("Invalid request."));
        verifyNoInteractions(directory);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "abc", "0", "-1", "1.5", "9223372036854775808"})
    void rejectsInvalidIdentity(String userId) throws Exception {
        mvc.perform(get("/api/v1/wallet").header("X-User-Id", userId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid request."));
        verifyNoInteractions(directory);
    }

    @Test
    void rejectsUnknownIdentity() throws Exception {
        mvc.perform(get("/api/v1/wallet").header("X-User-Id", "999"))
                .andExpect(status().isNotFound())
                .andExpect(content().json("{\"detail\":\"Requested resource not found.\"}",
                        org.springframework.test.json.JsonCompareMode.STRICT))
                .andExpect(jsonPath("$.detail").value("Requested resource not found."));
        verifyNoInteractions(directory);
    }

    @Test
    void returnsBadGatewayWhenDirectoryFails() throws Exception {
        when(directory.getUserName(1)).thenThrow(
                new ResponseStatusException(HttpStatus.BAD_GATEWAY, "User directory unavailable"));
        mvc.perform(get("/api/v1/wallet").header("X-User-Id", "1"))
                .andExpect(status().isBadGateway())
                .andExpect(content().json("{\"detail\":\"Unable to process your request.\"}",
                        org.springframework.test.json.JsonCompareMode.STRICT))
                .andExpect(jsonPath("$.detail").value("Unable to process your request."));
    }

    @Test
    void publishesApiDocumentationWithoutCallingDirectory() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Send Money API"))
                .andExpect(jsonPath("$.paths['/api/v1/wallet'].get.summary").value("Get your wallet balance"))
                .andExpect(jsonPath("$.paths['/api/v1/friends'].get.summary").value("List available friends"))
                .andExpect(jsonPath("$.paths['/api/v1/wallet'].get.parameters[?(@.name == 'X-User-Id')].required")
                        .value(contains(true)))
                .andExpect(jsonPath("$.paths['/api/v1/friends'].get.parameters[?(@.name == 'X-User-Id')].required")
                        .value(contains(true)))
                .andExpect(jsonPath("$.paths['/api/v1/wallet'].get.responses['400'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ApiError"))
                .andExpect(jsonPath("$.paths['/api/v1/friends'].get.responses['502'].headers['X-Request-Id']").exists())
                .andExpect(jsonPath("$.components.schemas.WalletBalanceResponse.properties.balance").exists())
                .andExpect(jsonPath("$.components.schemas.FriendResponse.properties.name").exists())
                .andExpect(jsonPath("$.components.schemas.ApiError.properties.*", hasSize(1)));
        verifyNoInteractions(directory);
    }

    @Test
    void servesSwaggerUiWithoutIdentityHeader() throws Exception {
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Swagger UI")));
        mvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/v3/api-docs"));
        verifyNoInteractions(directory);
    }

    private double storedBalance(long userId) {
        return jdbc.queryForObject("SELECT balance FROM wallets WHERE user_id = ?", java.math.BigDecimal.class, userId)
                .doubleValue();
    }
}
