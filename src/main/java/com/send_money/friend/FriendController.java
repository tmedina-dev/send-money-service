package com.send_money.friend;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/friends")
@Tag(name = "Friends", description = "Available recipients for sending money")
public class FriendController {

    private final FriendService friends;

    public FriendController(FriendService friends) {
        this.friends = friends;
    }

    @GetMapping(produces = "application/json")
    @Operation(summary = "List available friends", description = "Fetches external users, excludes the caller, and includes only users with local wallets. Returns an empty array when no recipients are eligible.")
    @ApiResponse(responseCode = "200", description = "Eligible recipient IDs and names")
    public ResponseEntity<List<FriendResponse>> getFriends(
            @RequestHeader(name = "X-User-Id", required = false) String userId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(friends.getFriends(userId));
    }
}
