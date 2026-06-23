package com.modules.webfluxmodule.controllers;

import com.modules.webfluxmodule.services.TableSessionSinkManager;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@CrossOrigin(origins = "*")
@RequestMapping("/api/public/sessions")
@RestController
public class TableSessionSseController {

    private final TableSessionSinkManager sinkManager;

    public TableSessionSseController(TableSessionSinkManager sinkManager) {
        this.sinkManager = sinkManager;
    }

    @GetMapping(value = "/{sessionId}/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> stream(@PathVariable String sessionId,
                                                @RequestParam("clientSessionId") String clientSessionId) {
        String connectionId = clientSessionId + "_" + UUID.randomUUID();
        Sinks.Many<Map<String, Object>> sink = sinkManager.register(sessionId, connectionId);

        Flux<ServerSentEvent<Object>> events = sink.asFlux()
                .map(payload -> {
                    String eventName = String.valueOf(payload.getOrDefault("type", "state"));
                    return ServerSentEvent.<Object>builder()
                            .event(eventName)
                            .data(payload)
                            .build();
                });

        Flux<ServerSentEvent<Object>> heartbeat = Flux.interval(Duration.ofSeconds(15))
                .map(i -> ServerSentEvent.<Object>builder()
                        .event("heartbeat")
                        .data(Map.of("ts", System.currentTimeMillis()))
                        .build());

        return Flux.merge(events, heartbeat)
                .doOnCancel(() -> sinkManager.unregister(sessionId, connectionId))
                .doOnTerminate(() -> sinkManager.unregister(sessionId, connectionId));
    }
}
