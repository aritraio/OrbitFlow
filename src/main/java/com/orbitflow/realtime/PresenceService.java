package com.orbitflow.realtime;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Presence tracking: Redis-backed when available, in-memory fallback otherwise.
 * Core board CRUD never depends on Redis availability.
 */
@Service
@Slf4j
public class PresenceService {
    private final Optional<StringRedisTemplate> redis;
    private final Map<String, Map<UUID, Long>> memory = new ConcurrentHashMap<>();

    public PresenceService(@org.springframework.beans.factory.annotation.Autowired(required = false) StringRedisTemplate redis) {
        this.redis = Optional.ofNullable(redis);
    }

    public void heartbeat(UUID boardId, UUID userId) {
        long now = Instant.now().toEpochMilli();
        memory.computeIfAbsent(key(boardId), k -> new ConcurrentHashMap<>()).put(userId, now);
        redis.ifPresent(r -> {
            try {
                r.opsForZSet().add("presence:board:" + boardId, userId.toString(), now);
                r.expire("presence:board:" + boardId, java.time.Duration.ofMinutes(2));
            } catch (Exception e) {
                log.debug("Redis presence unavailable, using memory fallback");
            }
        });
    }

    public Set<UUID> viewers(UUID boardId) {
        // Try Redis first
        if (redis.isPresent()) {
            try {
                long cutoff = Instant.now().toEpochMilli() - 90_000;
                Set<String> ids = redis.get().opsForZSet().rangeByScore("presence:board:" + boardId, cutoff, Double.MAX_VALUE);
                if (ids != null && !ids.isEmpty()) return ids.stream().map(UUID::fromString).collect(java.util.stream.Collectors.toSet());
            } catch (Exception ignored) {}
        }
        long cutoff = Instant.now().toEpochMilli() - 90_000;
        return memory.getOrDefault(key(boardId), Map.of()).entrySet().stream()
                .filter(e -> e.getValue() >= cutoff).map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
    }

    public void leave(UUID boardId, UUID userId) {
        Map<UUID, Long> m = memory.get(key(boardId));
        if (m != null) m.remove(userId);
        redis.ifPresent(r -> {
            try { r.opsForZSet().remove("presence:board:" + boardId, userId.toString()); }
            catch (Exception ignored) {}
        });
    }

    private String key(UUID boardId) { return "presence:board:" + boardId; }
}
