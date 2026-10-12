package com.raxim.myscoutee.microservices.transport.common;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/** Bounded NDJSON body; caller drains frames so job callbacks retain caller context. */
final class LimitedLines implements HttpResponse.BodySubscriber<LimitedLines> {
    record Item(byte[] bytes, boolean complete, Throwable failure) {}
    private final CompletableFuture<LimitedLines> body = new CompletableFuture<>();
    private final ArrayDeque<Item> frames = new ArrayDeque<>();
    private final ByteArrayOutputStream line = new ByteArrayOutputStream();
    private final int maxFrame, maxTotal;
    private int total;
    private Flow.Subscription subscription;
    private boolean finished, cancelled;
    LimitedLines(int maxFrame, int maxTotal) {
        if (maxFrame < 1 || maxTotal < maxFrame) throw new IllegalArgumentException("Invalid stream bounds");
        this.maxFrame = maxFrame; this.maxTotal = maxTotal;
    }
    @Override public CompletionStage<LimitedLines> getBody() { return body; }
    @Override public synchronized void onSubscribe(Flow.Subscription value) {
        subscription = value;
        if (cancelled) { value.cancel(); return; }
        body.complete(this);
        value.request(1);
    }
    @Override public synchronized void onNext(List<ByteBuffer> chunks) {
        if (finished || cancelled) return;
        for (var chunk : chunks) while (chunk.hasRemaining()) {
            if (++total > maxTotal) { onError(new IllegalStateException("Stream exceeds byte limit")); return; }
            byte value = chunk.get();
            if (value == '\n') {
                if (frames.size() >= 1024) { onError(new IllegalStateException("Too many queued stream frames")); return; }
                if (line.size() == 0) { onError(new IllegalStateException("Empty stream frame")); return; }
                frames.addLast(new Item(line.toByteArray(), false, null)); line.reset();
            } else {
                if (line.size() == maxFrame) { onError(new IllegalStateException("Stream frame exceeds byte limit")); return; }
                line.write(value);
            }
        }
        notifyAll();
        // Request only after the caller has consumed the current network batch.
        if (frames.isEmpty()) subscription.request(1);
    }
    @Override public synchronized void onError(Throwable failure) {
        if (finished || cancelled) return;
        finished = true;
        frames.clear(); frames.addLast(new Item(null, false, failure));
        if (subscription != null) subscription.cancel();
        body.complete(this); notifyAll();
    }
    @Override public synchronized void onComplete() {
        if (finished || cancelled) return;
        if (line.size() != 0) { onError(new IllegalStateException("Truncated stream frame")); return; }
        finished = true; frames.addLast(new Item(null, true, null)); notifyAll();
    }
    synchronized Item next(long nanos) throws InterruptedException {
        long deadline = System.nanoTime() + nanos;
        while (frames.isEmpty() && !cancelled) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) return null;
            java.util.concurrent.TimeUnit.NANOSECONDS.timedWait(this, remaining);
        }
        Item next = frames.pollFirst();
        if (frames.isEmpty() && !finished && !cancelled && subscription != null) subscription.request(1);
        return next;
    }
    synchronized void cancel() {
        cancelled = true;
        if (subscription != null) subscription.cancel();
        frames.clear(); line.reset(); notifyAll();
    }
}
