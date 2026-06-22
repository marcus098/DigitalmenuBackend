package com.modules.mainapp.config;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window rate limiter: max MAX_REQUESTS per WINDOW_MS per IP.
 * No external dependencies — backed by a ConcurrentHashMap.
 */
@Component
public class IpRateLimiter {

    private static final int MAX_REQUESTS = 5;
    private static final long WINDOW_MS = 10 * 60 * 1000L; // 10 minutes

    private final ConcurrentHashMap<String, Deque<Long>> buckets = new ConcurrentHashMap<>();

    /**
     * Returns true if the request is allowed, false if the IP is over limit.
     */
    public boolean isAllowed(String ip) {
        long now = System.currentTimeMillis();
        Deque<Long> timestamps = buckets.computeIfAbsent(ip, k -> new ArrayDeque<>());

        synchronized (timestamps) {
            // evict entries outside the window
            while (!timestamps.isEmpty() && now - timestamps.peekFirst() > WINDOW_MS) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= MAX_REQUESTS) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }
}
