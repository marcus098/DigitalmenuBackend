package com.modules.webfluxmodule.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SseHeartbeatTest {

    @Test
    void pingsImmediatelyThenForwardsDataAndCompletesWithIt() {
        Flux<Map<String, Object>> data = Flux.just(Map.<String, Object>of("type", "comand"))
                .delayElements(Duration.ofMillis(100));

        List<ServerSentEvent<Map<String, Object>>> out = AllController.withHeartbeat(data)
                .collectList()
                .block(Duration.ofSeconds(3));

        assertNotNull(out);
        assertEquals("ping", out.get(0).comment(), "il primo frame è il ping, così la connessione risulta aperta subito");
        assertTrue(out.stream().anyMatch(e -> e.data() != null && "comand".equals(e.data().get("type"))));
        assertEquals(1, out.stream().filter(e -> e.data() != null).count());
    }
}
