package com.modules.printmodule.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Stampante comande (non fiscale) di un locale.
 * Il deviceToken non viene salvato in chiaro: si salva solo lo SHA-256 (deviceTokenHash) + le ultime 4 cifre (tokenHint).
 */
@Getter
@Setter
@Document(collection = "printer")
public class PrinterDoc {

    @Id
    private String id;
    private Long idAgency;
    private String name;
    private PrinterType type;
    /** MAC della stampante Star (normalizzato a 12 hex minuscoli). Vuoto = appreso al primo contatto. */
    private String macAddress;
    private String deviceTokenHash;
    private String tokenHint;
    /** 58 o 80 (mm). */
    private int paperWidth = 80;
    /** id o nomi categoria; vuoto = tutte. */
    private List<String> categoryFilter = new ArrayList<>();
    private PrintOn printOn = PrintOn.CREATED;
    private int copies = 1;
    private boolean enabled = true;
    private Instant lastSeenAt;
    private String lastStatus;
    private Instant createdAt;

    public int charsPerLine() {
        return paperWidth <= 58 ? 32 : 48;
    }
}
