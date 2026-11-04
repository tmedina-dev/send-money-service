package com.send_money;

import java.io.IOException;

import com.send_money.directory.UserDirectoryClient;
import com.send_money.common.DownstreamLoggingInterceptor;
import org.slf4j.MDC;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class UserDirectoryClientTests {

    MockRestServiceServer server;
    UserDirectoryClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://directory.test")
                .requestInterceptor(new DownstreamLoggingInterceptor());
        server = MockRestServiceServer.bindTo(builder).build();
        client = new UserDirectoryClient(builder.build());
    }

    @Test
    void extractsNameAndIgnoresOtherProfileFields() {
        server.expect(requestTo("https://directory.test/users/1"))
                .andRespond(withSuccess("""
                        {"id":1,"name":"Leanne Graham","email":"private@example.com","address":{"city":"test"}}
                        """, MediaType.APPLICATION_JSON));
        assertThat(client.getUserName(1)).isEqualTo("Leanne Graham");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"id\":2,\"name\":\"Wrong user\"}",
            "{\"id\":1,\"name\":\" \"}", "not json", ""})
    void rejectsInvalidResponses(String body) {
        server.expect(requestTo("https://directory.test/users/1"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertBadGateway();
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 429, 500})
    void mapsUpstreamHttpErrorsToBadGateway(int status) {
        server.expect(requestTo("https://directory.test/users/1"))
                .andRespond(withStatus(HttpStatus.valueOf(status)));
        assertBadGateway();
    }

    @Test
    void mapsConnectionFailureToBadGateway() {
        server.expect(requestTo("https://directory.test/users/1"))
                .andRespond(withException(new IOException("Connection failed")));
        assertBadGateway();
    }

    @Test
    void propagatesTheApiRequestIdToTheDirectory() {
        MDC.put("requestId", "wallet-request-123");
        try {
            server.expect(requestTo("https://directory.test/users/1"))
                    .andExpect(header("X-Request-Id", "wallet-request-123"))
                    .andRespond(withSuccess("{\"id\":1,\"name\":\"Leanne Graham\"}", MediaType.APPLICATION_JSON));
            assertThat(client.getUserName(1)).isEqualTo("Leanne Graham");
            server.verify();
        } finally {
            MDC.remove("requestId");
        }
    }

    private void assertBadGateway() {
        assertThatThrownBy(() -> client.getUserName(1))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
        server.verify();
    }

    @Test
    void fetchesUserListWithOnlyIdsAndNames() {
        server.expect(requestTo("https://directory.test/users"))
                .andRespond(withSuccess("[{\"id\":2,\"name\":\"Ervin Howell\",\"email\":\"private@example.com\"}]",
                        MediaType.APPLICATION_JSON));
        assertThat(client.getUsers()).containsExactly(new UserDirectoryClient.UserProfile(2L, "Ervin Howell"));
        server.verify();
    }

    @Test
    void acceptsEmptyUserList() {
        server.expect(requestTo("https://directory.test/users"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        assertThat(client.getUsers()).isEmpty();
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{}", "[null]", "[{\"id\":0,\"name\":\"Invalid\"}]",
            "[{\"id\":1}]", "[{\"id\":1,\"name\":\"A\"},{\"id\":1,\"name\":\"B\"}]"})
    void rejectsInvalidUserLists(String body) {
        server.expect(requestTo("https://directory.test/users"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.getUsers()).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
        server.verify();
    }

    @Test
    void mapsUserListHttpFailureToBadGateway() {
        server.expect(requestTo("https://directory.test/users")).andRespond(withServerError());
        assertThatThrownBy(() -> client.getUsers()).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
        server.verify();
    }
}
