package com.raxim.myscoutee.microservices.transport.media;

import java.time.Instant;
import com.raxim.myscoutee.domain.platform.api.JobGroupContext;

public final class MediaDeletionQueueProtocol {
    public static final String RUN = "/_internal/v1/media/media-deletion/run";
    public static final String ENQUEUE = "/_internal/v1/media/media-deletion/enqueue";
    public record Enqueue(String baseGroupId, java.util.List<String> references) {}
    public static boolean valid(Enqueue value) {
        return value != null && (value.baseGroupId() == null || !value.baseGroupId().isBlank() && value.baseGroupId().length() <= 512)
                && value.references() != null && !value.references().isEmpty() && value.references().size() <= 100
                && value.references().stream().allMatch(v -> v != null && !v.isBlank() && v.length() <= 8192);
    }
    public static final int MAX_REQUEST_BYTES = 1024 * 1024, MAX_RESPONSE_BYTES = 16384;
    private MediaDeletionQueueProtocol() {}
    public static String[] paths() { return new String[]{RUN, ENQUEUE}; }
    public record Command(Boolean active, String groupId, String actor, Instant startedAt) {
        public static Command current(String actor, Instant startedAt) {
            return new Command(JobGroupContext.isActive(), JobGroupContext.groupId(), actor, startedAt);
        }
    }
    /** Worker returned; actual completed/failed/skipped status remains in the native job registry. */
    public record Receipt(Boolean returned) {}
    public record Error(String code) {}
    public static boolean valid(Command command) {
        return command != null && command.active() != null && (command.groupId() == null || command.groupId().length() <= 512)
                && (command.active() || command.groupId() == null) && command.actor() != null
                && !command.actor().isBlank() && command.actor().length() <= 512 && command.startedAt() != null;
    }
}
