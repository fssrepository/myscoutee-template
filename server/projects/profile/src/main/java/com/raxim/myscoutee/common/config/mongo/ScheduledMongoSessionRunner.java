package com.raxim.myscoutee.common.config.mongo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Runs one scheduled pass against each distinct configured application database.
 * Request threads already carry their own mode through {@link MongoSessionModeContext};
 * scheduler threads do not, so they would otherwise always use the real database.
 */
@Component
public class ScheduledMongoSessionRunner {
    private final boolean distinctDemoDatabase;

    public ScheduledMongoSessionRunner(
            @Value("${spring.mongodb.database:${MONGODB_DATABASE:demo_db}}") String realDatabase,
            @Value("${MONGODB_DEMO_DATABASE:${spring.mongodb.database:${MONGODB_DATABASE:demo_db}}}") String demoDatabase) {
        this.distinctDemoDatabase = !normalize(realDatabase).equals(normalize(demoDatabase));
    }

    public boolean hasDistinctDemoDatabase() { return distinctDemoDatabase; }

    public void runAcrossConfiguredDatabases(Runnable action) {
        if (action == null) {
            return;
        }
        MongoSessionMode previousMode = MongoSessionModeContext.currentMode();
        RuntimeException firstFailure = null;
        try {
            for (MongoSessionMode mode : modes()) {
                try {
                    MongoSessionModeContext.set(mode);
                    action.run();
                } catch (RuntimeException exception) {
                    if (firstFailure == null) {
                        firstFailure = exception;
                    } else {
                        firstFailure.addSuppressed(exception);
                    }
                }
            }
        } finally {
            MongoSessionModeContext.set(previousMode);
        }
        if (firstFailure != null) {
            throw firstFailure;
        }
    }

    private MongoSessionMode[] modes() {
        return distinctDemoDatabase
                ? new MongoSessionMode[] { MongoSessionMode.REAL, MongoSessionMode.DEMO }
                : new MongoSessionMode[] { MongoSessionMode.REAL };
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : "demo_db";
    }
}
