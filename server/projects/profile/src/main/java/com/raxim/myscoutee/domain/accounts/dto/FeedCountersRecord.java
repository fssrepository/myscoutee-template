package com.raxim.myscoutee.domain.accounts.dto;
import java.util.Map;
public record FeedCountersRecord(long revision, Map<String, Long> counts) {
        public static FeedCountersRecord empty() { return new FeedCountersRecord(0, Map.of()); }
    }
