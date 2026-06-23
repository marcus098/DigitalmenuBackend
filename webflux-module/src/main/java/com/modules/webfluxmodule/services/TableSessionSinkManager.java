package com.modules.webfluxmodule.services;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TableSessionSinkManager {

    private final Map<String, Map<String, Sinks.Many<Map<String, Object>>>> sinksBySession = new ConcurrentHashMap<>();

    public Sinks.Many<Map<String, Object>> register(String sessionId, String connectionId) {
        sinksBySession.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>());
        Sinks.Many<Map<String, Object>> sink = Sinks.many().multicast().onBackpressureBuffer();
        sinksBySession.get(sessionId).put(connectionId, sink);
        return sink;
    }

    public void unregister(String sessionId, String connectionId) {
        Map<String, Sinks.Many<Map<String, Object>>> bySession = sinksBySession.get(sessionId);
        if (bySession != null) {
            Sinks.Many<Map<String, Object>> removed = bySession.remove(connectionId);
            if (removed != null) removed.tryEmitComplete();
            if (bySession.isEmpty()) sinksBySession.remove(sessionId);
        }
    }

    public void broadcast(String sessionId, Map<String, Object> payload) {
        Map<String, Sinks.Many<Map<String, Object>>> bySession = sinksBySession.get(sessionId);
        if (bySession != null) {
            bySession.values().forEach(sink -> sink.tryEmitNext(payload));
        }
    }
}
