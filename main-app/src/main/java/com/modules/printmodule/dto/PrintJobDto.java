package com.modules.printmodule.dto;

import com.modules.printmodule.model.PrintJobKind;
import com.modules.printmodule.model.PrintJobStatus;

import java.time.Instant;

public record PrintJobDto(
        String id,
        String comandId,
        PrintJobKind kind,
        PrintJobStatus status,
        int attempts,
        Instant createdAt,
        Instant sentAt,
        Instant printedAt,
        String lastError,
        String preview
) {
}
