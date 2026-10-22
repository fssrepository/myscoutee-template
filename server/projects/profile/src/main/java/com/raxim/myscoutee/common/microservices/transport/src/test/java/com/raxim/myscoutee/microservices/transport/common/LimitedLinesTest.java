package com.raxim.myscoutee.microservices.transport.common;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class LimitedLinesTest {
    @Test void frameEqualityUsesByteContentAndDiagnosticsDoNotPrintPayloads() {
        var first = new LimitedLines.Item("private-frame".getBytes(StandardCharsets.UTF_8), false, null);
        var equal = new LimitedLines.Item("private-frame".getBytes(StandardCharsets.UTF_8), false, null);
        assertEquals(first, equal);
        assertEquals(first.hashCode(), equal.hashCode());
        assertNotEquals(first, new LimitedLines.Item(new byte[]{1}, false, null));
        assertNotEquals(first, new LimitedLines.Item(first.bytes(), true, null));
        assertFalse(first.toString().contains("private-frame"));
    }
    @Test void preservesFramesAcrossArbitraryNetworkChunks() throws Exception {
        var body = new LimitedLines(32, 128); subscribe(body);
        body.onNext(bytes("{\"a\":"));
        body.onNext(bytes("1}\n{\"b\":2}\n")); body.onComplete();
        assertEquals("{\"a\":1}", new String(body.next(TimeUnit.SECONDS.toNanos(1)).bytes(), StandardCharsets.UTF_8));
        assertEquals("{\"b\":2}", new String(body.next(TimeUnit.SECONDS.toNanos(1)).bytes(), StandardCharsets.UTF_8));
        assertTrue(body.next(TimeUnit.SECONDS.toNanos(1)).complete());
    }
    @Test void rejectsOversizeAndTruncatedFramesAndCancelsNetworkSubscription() throws Exception {
        var large = new LimitedLines(4, 8); var cancelled = subscribe(large);
        large.onNext(bytes("12345"));
        assertNotNull(large.next(TimeUnit.SECONDS.toNanos(1)).failure()); assertTrue(cancelled.get());
        var partial = new LimitedLines(4, 8); subscribe(partial);
        partial.onNext(bytes("{}")); partial.onComplete();
        assertNotNull(partial.next(TimeUnit.SECONDS.toNanos(1)).failure());
    }
    private List<ByteBuffer> bytes(String value) { return List.of(ByteBuffer.wrap(value.getBytes(StandardCharsets.UTF_8))); }
    private AtomicBoolean subscribe(LimitedLines body) {
        var cancelled = new AtomicBoolean();
        body.onSubscribe(new Flow.Subscription() {
            @Override public void request(long n) {}
            @Override public void cancel() { cancelled.set(true); }
        }); return cancelled;
    }
}
