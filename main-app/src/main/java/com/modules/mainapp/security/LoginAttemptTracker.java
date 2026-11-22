package com.modules.mainapp.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Conta i tentativi falliti (login sbagliati, captcha non superati) per chiave ("ip:..." / "acc:...") in una finestra
 * scorrevole. In memoria: con più istanze del backend ogni istanza ha i suoi contatori (come IpRateLimiter).
 */
@Component
public class LoginAttemptTracker {

    /** Oltre questo numero di tentativi per chiave non serve ricordarne altri (tetto alla memoria). */
    private static final int MAX_TRACKED = 200;

    private final long windowMs;
    private final ConcurrentHashMap<String, Deque<Long>> failures = new ConcurrentHashMap<>();

    public LoginAttemptTracker(@Value("${app.login.attempts.window-minutes:15}") long windowMinutes) {
        this.windowMs = Math.max(1, windowMinutes) * 60_000L;
    }

    public int failures(String key) {
        Deque<Long> d = failures.get(key);
        if (d == null) return 0;
        synchronized (d) {
            evict(d, System.currentTimeMillis());
            return d.size();
        }
    }

    public void recordFailure(String key) {
        long now = System.currentTimeMillis();
        Deque<Long> d = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (d) {
            evict(d, now);
            if (d.size() < MAX_TRACKED) d.addLast(now);
        }
    }

    public void reset(String key) {
        failures.remove(key);
    }

    public long windowMinutes() {
        return windowMs / 60_000L;
    }

    private void evict(Deque<Long> d, long now) {
        while (!d.isEmpty() && now - d.peekFirst() > windowMs) d.pollFirst();
    }

    @Scheduled(fixedDelay = 60_000)
    public void evictStale() {
        long now = System.currentTimeMillis();
        failures.entrySet().removeIf(e -> {
            Deque<Long> d = e.getValue();
            synchronized (d) {
                evict(d, now);
                return d.isEmpty();
            }
        });
    }
}
