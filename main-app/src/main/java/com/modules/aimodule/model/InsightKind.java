package com.modules.aimodule.model;

import java.util.Optional;

/** Tipi di riepilogo AxiomAI. {@code path} è il valore usato nell'URL di refresh. */
public enum InsightKind {
    DAILY("daily"),
    WEEKLY("weekly"),
    MENU_TIPS("menu-tips");

    private final String path;

    InsightKind(String path) { this.path = path; }

    public String path() { return path; }

    public static Optional<InsightKind> fromPath(String value) {
        if (value == null) return Optional.empty();
        String v = value.trim().toLowerCase();
        for (InsightKind k : values()) if (k.path.equals(v)) return Optional.of(k);
        return Optional.empty();
    }
}
