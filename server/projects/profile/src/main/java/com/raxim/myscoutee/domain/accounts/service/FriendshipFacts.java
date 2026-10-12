package com.raxim.myscoutee.domain.accounts.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import com.raxim.myscoutee.domain.accounts.repository.UserFriendshipsRepository;

/** One indexed adjacency read per viewer/request; no process-wide authorization cache. */
@Component
public class FriendshipFacts implements com.raxim.myscoutee.domain.accounts.api.FriendshipReads {
    private final UserFriendshipsRepository friendships;

    public FriendshipFacts(UserFriendshipsRepository friendships) {
        this.friendships = friendships;
    }

    public boolean pairExists(String first, String second) {
        if (first == null || second == null || first.isBlank() || second.isBlank() || first.equals(second)) return false;
        String left = first.compareTo(second) < 0 ? first : second;
        String right = first.compareTo(second) < 0 ? second : first;
        return friendships.existsById(left.length() + ":" + left + right);
    }

    public Set<String> loadFriendIds(String viewer) {
        if (viewer == null || viewer.isBlank()) return Set.of();
        Set<String> ids = new LinkedHashSet<>();
        for (var pair : friendships.findRelevantByUserIds(List.of(viewer))) {
            if (viewer.equals(pair.leftUserId())) ids.add(pair.rightUserId());
            else if (viewer.equals(pair.rightUserId())) ids.add(pair.leftUserId());
        }
        ids.remove(null);
        ids.remove(viewer);
        return Set.copyOf(ids);
    }
}
