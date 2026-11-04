package com.send_money;

import java.util.List;

import com.send_money.directory.UserDirectoryClient;
import com.send_money.directory.UserDirectoryClient.UserProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "USER_DIRECTORY_BASE_URL=https://directory.test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FriendApiTests {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    UserDirectoryClient directory;

    @Test
    void returnsExternalFriendsWithWalletsWithoutExposingBalances() throws Exception {
        when(directory.getUsers()).thenReturn(List.of(new UserProfile(1L, "Caller"),
                new UserProfile(2L, "Ervin Howell"), new UserProfile(11L, "No wallet")));
        mvc.perform(get("/api/v1/friends").header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(content().json("[{\"userId\":2,\"name\":\"Ervin Howell\"}]", JsonCompareMode.STRICT));
        verify(directory).getUsers();
    }

    @Test
    void excludesTheUserFromTheHeader() throws Exception {
        when(directory.getUsers()).thenReturn(List.of(new UserProfile(1L, "Leanne Graham"), new UserProfile(2L, "Caller")));
        mvc.perform(get("/api/v1/friends").header("X-User-Id", "2"))
                .andExpect(status().isOk())
                .andExpect(content().json("[{\"userId\":1,\"name\":\"Leanne Graham\"}]", JsonCompareMode.STRICT));
    }

    @Test
    void returnsEmptyListWhenNoEligibleFriends() throws Exception {
        when(directory.getUsers()).thenReturn(List.of(new UserProfile(1L, "Caller"), new UserProfile(11L, "No wallet")));
        mvc.perform(get("/api/v1/friends").header("X-User-Id", "1"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void rejectsMissingHeaderBeforeCallingDirectory() throws Exception {
        mvc.perform(get("/api/v1/friends")).andExpect(status().isBadRequest());
        verifyNoInteractions(directory);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "0", "-1", "9223372036854775808"})
    void rejectsInvalidHeaderBeforeCallingDirectory(String id) throws Exception {
        mvc.perform(get("/api/v1/friends").header("X-User-Id", id))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"detail\":\"Invalid request.\"}", JsonCompareMode.STRICT));
        verifyNoInteractions(directory);
    }

    @Test
    void rejectsUnknownCallerBeforeCallingDirectory() throws Exception {
        mvc.perform(get("/api/v1/friends").header("X-User-Id", "999"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(directory);
    }

    @Test
    void handlesDirectoryFailureWithGenericMessage() throws Exception {
        when(directory.getUsers()).thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "User directory unavailable"));
        mvc.perform(get("/api/v1/friends").header("X-User-Id", "1"))
                .andExpect(status().isBadGateway())
                .andExpect(content().json("{\"detail\":\"Unable to process your request.\"}", JsonCompareMode.STRICT));
    }
}
