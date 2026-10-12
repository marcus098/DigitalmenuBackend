package com.modules.pushmodule.service;

import java.util.Map;

/**
 * Contenuto di una notifica push. {@code channelId} è il canale Android creato dall'app ("orders" / "reservations").
 */
public record PushContent(String title, String body, String channelId, Map<String, Object> data) {
}
