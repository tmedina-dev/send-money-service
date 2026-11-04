package com.send_money.directory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class UserDirectoryClient {

    private final RestClient client;

    public UserDirectoryClient(@Qualifier("userDirectoryRestClient") RestClient client) {
        this.client = client;
    }

    public String getUserName(long userId) {
        UserProfile profile;
        try {
            profile = client.get().uri("/users/{id}", userId).retrieve().body(UserProfile.class);
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "User directory unavailable", ex);
        }
        if (profile == null || profile.id() == null || profile.id() != userId
                || profile.name() == null || profile.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid user directory response");
        }
        return profile.name();
    }

    public record UserProfile(Long id, String name) {
    }

    public List<UserProfile> getUsers() {
        UserProfile[] profiles;
        try {
            profiles = client.get().uri("/users").retrieve().body(UserProfile[].class);
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "User directory unavailable", ex);
        }
        if (profiles == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid user directory response");
        }
        Set<Long> seen = new HashSet<>();
        for (UserProfile profile : profiles) {
            if (profile == null || profile.id() == null || profile.id() <= 0
                    || profile.name() == null || profile.name().isBlank() || !seen.add(profile.id())) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid user directory response");
            }
        }
        return List.of(profiles);
    }
}
