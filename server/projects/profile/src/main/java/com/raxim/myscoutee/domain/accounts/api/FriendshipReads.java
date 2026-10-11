package com.raxim.myscoutee.domain.accounts.api;

import java.util.Set;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import com.raxim.myscoutee.common.config.mongo.MongoSessionModeContext;

/** Same request-local authorization facts for native and remote account reads; no process-wide cache. */
public interface FriendshipReads {
    boolean pairExists(String first, String second);
    Set<String> loadFriendIds(String viewer);
    default boolean areFriends(String first, String second) {
        if (first == null || second == null || first.isBlank() || second.isBlank() || first.equals(second)) return false;
        if (RequestContextHolder.getRequestAttributes() != null) return friendIds(second).contains(first);
        return pairExists(first, second);
    }
    @SuppressWarnings("unchecked")
    default Set<String> friendIds(String viewer) {
        if (viewer == null || viewer.isBlank()) return Set.of();
        RequestAttributes request = RequestContextHolder.getRequestAttributes();
        String key = getClass().getName() + ":" + MongoSessionModeContext.currentMode() + ":" + viewer;
        if (request != null && request.getAttribute(key, RequestAttributes.SCOPE_REQUEST) instanceof Set<?> cached)
            return (Set<String>) cached;
        Set<String> result = loadFriendIds(viewer);
        if (request != null) request.setAttribute(key, result, RequestAttributes.SCOPE_REQUEST);
        return result;
    }
}
