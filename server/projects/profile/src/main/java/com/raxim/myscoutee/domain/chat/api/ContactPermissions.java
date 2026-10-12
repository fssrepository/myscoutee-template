package com.raxim.myscoutee.domain.chat.api;
import java.util.List;
import java.util.Optional;
public interface ContactPermissions {
    interface Permission { String id(); String leftUserId(); String rightUserId(); String requestedBy(); String status(); String requestedAtIso(); String decidedAtIso(); Long version(); }
    record Change(String id, String leftUserId, String rightUserId, String requestedBy, String status, String requestedAtIso, String decidedAtIso, Long version) {}
    String pairId(String first, String second);
    /** Stable wire/persistence identity; preserve length prefix to avoid ambiguous concatenation. */
    static String canonicalPairId(String first, String second) {
        String left = first.compareTo(second) < 0 ? first : second;
        String right = first.compareTo(second) < 0 ? second : first;
        return left.length() + ":" + left + right;
    }
    List<? extends Permission> findForUser(String userId);
    Optional<? extends Permission> findById(String id);
    Permission save(Change value);
}
