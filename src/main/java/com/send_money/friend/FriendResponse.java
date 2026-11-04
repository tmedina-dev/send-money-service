package com.send_money.friend;

import io.swagger.v3.oas.annotations.media.Schema;

public record FriendResponse(
        @Schema(description = "Eligible recipient's external ID", example = "2") long userId,
        @Schema(description = "Name from the external directory", example = "Ervin Howell") String name) {
}
