package com.modules.printmodule.dto;

import com.modules.printmodule.model.PrintOn;
import com.modules.printmodule.model.PrinterType;

import java.util.List;

/** Body di POST/PUT /api/printers. */
public record PrinterRequest(
        String name,
        PrinterType type,
        String macAddress,
        Integer paperWidth,
        List<String> categoryFilter,
        PrintOn printOn,
        Integer copies,
        Boolean enabled
) {
}
