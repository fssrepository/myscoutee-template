package com.raxim.myscoutee.common.redis;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RedisStreamRetentionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedisStreamRetentionService.class);

    public void trimByAge(StringRedisTemplate stringRedisTemplate, String streamKey, Duration retention) {
        if (stringRedisTemplate == null || !StringUtils.hasText(streamKey) || retention == null || retention.isNegative() || retention.isZero()) {
            return;
        }
        String minId = Instant.now().minus(retention).toEpochMilli() + "-0";
        try {
            stringRedisTemplate.execute((RedisCallback<Void>) connection -> {
                connection.execute("XTRIM", bytes(streamKey), bytes("MINID"), bytes(minId));
                return null;
            });
        } catch (RuntimeException exception) {
            LOGGER.debug("Redis stream time-based trim failed for {}.", streamKey, exception);
        }
    }

    public void deleteRecords(StringRedisTemplate stringRedisTemplate, String streamKey, Collection<String> recordIds) {
        if (stringRedisTemplate == null || !StringUtils.hasText(streamKey) || recordIds == null || recordIds.isEmpty()) {
            return;
        }
        List<byte[]> args = new ArrayList<>();
        args.add(bytes(streamKey));
        for (String recordId : recordIds) {
            if (StringUtils.hasText(recordId)) {
                args.add(bytes(recordId.trim()));
            }
        }
        if (args.size() <= 1) {
            return;
        }
        try {
            stringRedisTemplate.execute((RedisCallback<Void>) connection -> {
                connection.execute("XDEL", args.toArray(byte[][]::new));
                return null;
            });
        } catch (RuntimeException exception) {
            LOGGER.debug("Redis stream record cleanup failed for {}.", streamKey, exception);
        }
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
