package com.raxim.myscoutee.common.config.mongo;

public final class MongoSessionModeContext {
    private static final ThreadLocal<MongoSessionMode> CURRENT_MODE = ThreadLocal.withInitial(() -> MongoSessionMode.REAL);

    private MongoSessionModeContext() {
    }

    public static MongoSessionMode currentMode() {
        return CURRENT_MODE.get();
    }

    public static void set(MongoSessionMode mode) {
        CURRENT_MODE.set(mode == null ? MongoSessionMode.REAL : mode);
    }

    public static void clear() {
        CURRENT_MODE.remove();
    }
}
