package com.raxim.myscoutee.domain.media.api;

/** Owner-side object cleanup used by the existing domain purge workflows. */
public interface MediaCleanup {
    void deletePublicObjectOrThrow(String mediaUrlOrKey);

    /** Existing best-effort cleanup, distinct from durable purge stages which must fail. */
    default void deletePublicObject(String mediaUrlOrKey) {
        try { deletePublicObjectOrThrow(mediaUrlOrKey); }
        catch (RuntimeException ignored) { }
    }
}
