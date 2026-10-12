package com.raxim.myscoutee.domain.chat.service;

import com.raxim.myscoutee.common.redis.RedisStreamRetentionService;

import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import tools.jackson.databind.ObjectMapper;

@Service
public class ChatStreamPublisher {
    public static final String STREAM_KEY = "activities:chat:messages";
    private static final Duration STREAM_RETENTION = Duration.ofDays(30);

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatStreamPublisher.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final RedisStreamRetentionService redisStreamRetentionService;

    public ChatStreamPublisher(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            RedisStreamRetentionService redisStreamRetentionService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.redisStreamRetentionService = redisStreamRetentionService;
    }

    public boolean publish(ChatStreamEvent event) {
        if (event == null) {
            return false;
        }
        try {
            stringRedisTemplate.opsForStream().add(
                    StreamRecords.mapBacked(Map.of("payload", objectMapper.writeValueAsString(event)))
                            .withStreamKey(STREAM_KEY));
            redisStreamRetentionService.trimByAge(stringRedisTemplate, STREAM_KEY, STREAM_RETENTION);
            return true;
        } catch (Exception exception) {
            LOGGER.warn("Unable to publish chat stream event for chat {}.", event.chatId(), exception);
            return false;
        }
    }
}
