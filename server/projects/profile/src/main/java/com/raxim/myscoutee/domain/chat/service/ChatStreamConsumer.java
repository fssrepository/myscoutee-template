package com.raxim.myscoutee.domain.chat.service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import tools.jackson.databind.ObjectMapper;

@Service
public class ChatStreamConsumer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatStreamConsumer.class);
    private static final long IDLE_PAUSE_MS = 10L;

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final ChatWebSocketSessionRegistry sessionRegistry;
    private final long blockMs;
    private final ExecutorService consumerExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "chat-stream-consumer");
        thread.setDaemon(true);
        return thread;
    });

    private String lastSeenId = "0-0";
    private boolean streamUnavailableLogged;
    private volatile boolean running;
    private volatile Future<?> consumerTask;

    public ChatStreamConsumer(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            ChatWebSocketSessionRegistry sessionRegistry,
            @Value("${chat.stream.block-ms:10000}") long blockMs) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.sessionRegistry = sessionRegistry;
        this.blockMs = Math.max(0L, blockMs);
    }

    @PostConstruct
    void startConsumer() {
        running = true;
        consumerTask = consumerExecutor.submit(this::consumeLoop);
    }

    @PreDestroy
    void stopConsumer() {
        running = false;
        Future<?> task = consumerTask;
        if (task != null) {
            task.cancel(true);
        }
        consumerExecutor.shutdownNow();
    }

    private void consumeLoop() {
        while (running && !Thread.currentThread().isInterrupted()) {
            boolean processedRecords = poll();
            if (!processedRecords) {
                pauseWhenIdle();
            }
        }
    }

    public boolean poll() {
        try {
            List<MapRecord<String, Object, Object>> records = readRecords();
            if (streamUnavailableLogged) {
                LOGGER.info("Chat Redis stream consumer reconnected.");
                streamUnavailableLogged = false;
            }
            if (records == null || records.isEmpty()) {
                return false;
            }
            for (MapRecord<String, Object, Object> record : records) {
                lastSeenId = record.getId().getValue();
                Object payload = record.getValue().get("payload");
                if (!(payload instanceof String jsonPayload) || jsonPayload.isBlank()) {
                    continue;
                }
                try {
                    ChatStreamEvent event = objectMapper.readValue(jsonPayload, ChatStreamEvent.class);
                    sessionRegistry.broadcast(event);
                } catch (Exception ignored) {
                    // Ignore malformed records and keep draining the stream.
                }
            }
            return true;
        } catch (Exception exception) {
            if (!streamUnavailableLogged) {
                LOGGER.warn("Chat Redis stream consumer paused because Redis is unavailable.", exception);
                streamUnavailableLogged = true;
            }
            return false;
        }
    }

    private void pauseWhenIdle() {
        try {
            Thread.sleep(IDLE_PAUSE_MS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    @SuppressWarnings("unchecked")
    private List<MapRecord<String, Object, Object>> readRecords() {
        StreamReadOptions options = StreamReadOptions.empty().count(100);
        if (blockMs > 0L) {
            options = options.block(Duration.ofMillis(blockMs));
        }
        return stringRedisTemplate.opsForStream().read(
                options,
                StreamOffset.create(ChatStreamPublisher.STREAM_KEY, ReadOffset.from(lastSeenId)));
    }
}
