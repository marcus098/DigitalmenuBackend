package com.modules.printmodule.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.modules.printmodule.model.PrintOn;
import com.modules.printmodule.model.PrinterType;

import java.time.Instant;
import java.util.List;

/**
 * deviceToken è valorizzato solo nella risposta di create / regenerate-token (non è salvato in chiaro).
 * setupUrl: per STAR_CLOUDPRNT è l'URL CloudPRNT completo (contiene il token → solo insieme a deviceToken);
 *           per ESCPOS_BRIDGE è il serverUrl da mettere nel config.json del bridge (sempre presente).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PrinterDto(
        String id,
        String name,
        PrinterType type,
        String macAddress,
        int paperWidth,
        int charsPerLine,
        List<String> categoryFilter,
        PrintOn printOn,
        int copies,
        boolean enabled,
        Instant lastSeenAt,
        boolean online,
        String lastStatus,
        String tokenHint,
        String deviceToken,
        String setupUrl
) {
}
