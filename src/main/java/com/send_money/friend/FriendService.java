package com.send_money.friend;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.send_money.common.CurrentUserService;
import com.send_money.directory.UserDirectoryClient;
import com.send_money.directory.UserDirectoryClient.UserProfile;
import com.send_money.wallet.Wallet;
import com.send_money.wallet.WalletRepository;
import org.springframework.stereotype.Service;

@Service
public class FriendService {

    private final CurrentUserService currentUser;
    private final UserDirectoryClient directory;
    private final WalletRepository wallets;

    public FriendService(CurrentUserService currentUser, UserDirectoryClient directory, WalletRepository wallets) {
        this.currentUser = currentUser;
        this.directory = directory;
        this.wallets = wallets;
    }

    public List<FriendResponse> getFriends(String userIdHeader) {
        long callerId = currentUser.requireWallet(userIdHeader).getUserId();
        // External users supply the list and names; wallets determine eligibility.
        List<UserProfile> candidates = directory.getUsers().stream()
                .filter(user -> user.id() != callerId).toList();
        if (candidates.isEmpty()) {
            return List.of();
        }
        Set<Long> eligibleIds = wallets.findAllById(candidates.stream().map(UserProfile::id).toList())
                .stream().map(Wallet::getUserId).collect(Collectors.toSet());
        return candidates.stream().filter(user -> eligibleIds.contains(user.id()))
                .map(user -> new FriendResponse(user.id(), user.name())).toList();
    }
}
