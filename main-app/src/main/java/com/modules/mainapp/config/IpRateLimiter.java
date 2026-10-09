package com.modules.mainapp.config;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window rate limiter: max N requests per window per key (default: 5 per 10 minutes per IP).
 * No external dependencies — backed by a ConcurrentHashMap; stale buckets are evicted periodically
 * so the map cannot grow without bound.
 *
 * The key must be built from {@code request.getRemoteAddr()}: X-Forwarded-For is client-controlled and
 * must not be trusted directly (behind a proxy, server.forward-headers-strategy rewrites remoteAddr).
 */
@Component
public class IpRateLimiter {

    private static final int MAX_REQUESTS = 5;
    private static final long WINDOW_MS = 10 * 60 * 1000L; // 10 minutes

    private static final class Bucket {
        final Deque<Long> timestamps = new ArrayDeque<>();
        volatile long windowMs;
        volatile long lastSeen;
    }

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * Returns true if the request is allowed, false if the IP is over limit.
     */
    public boolean isAllowed(String ip) {
        return isAllowed(ip, MAX_REQUESTS, WINDOW_MS);
    }

    /**
     * Returns true if the request identified by {@code key} is allowed (max {@code maxRequests} per {@code windowMs}).
     * Use distinct key prefixes for distinct limits (e.g. "join:" + ip + ":" + tableId).
     */
    public boolean isAllowed(String key, int maxRequests, long windowMs) {
        long now = System.currentTimeMillis();
        Bucket bucket = buckets.computeIfAbsent(key == null ? "unknown" : key, k -> new Bucket());

        synchronized (bucket) {
            bucket.windowMs = windowMs;
            bucket.lastSeen = now;
            Deque<Long> timestamps = bucket.timestamps;
            // evict entries outside the window
            while (!timestamps.isEmpty() && now - timestamps.peekFirst() > windowMs) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxRequests) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }

    /** Rimuove i bucket non più usati da oltre la loro finestra. */
    @Scheduled(fixedDelay = 60_000)
    public void evictStaleBuckets() {
        long now = System.currentTimeMillis();
        buckets.entrySet().removeIf(e -> {
            Bucket b = e.getValue();
            synchronized (b) {
                return now - b.lastSeen > b.windowMs;
            }
        });
    }
}
