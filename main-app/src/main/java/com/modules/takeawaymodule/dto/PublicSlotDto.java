package com.modules.takeawaymodule.dto;

import java.util.List;

/**
 * Slot visto dal cliente: solo orario e stato (AVAILABLE | ON_REQUEST).
 * Capienza e ordini già presi restano riservati al locale.
 */
public record PublicSlotDto(String time, SlotDto.Status status) {

    public static List<PublicSlotDto> of(List<SlotDto> slots) {
        return slots.stream().map(s -> new PublicSlotDto(s.getTime(), s.getStatus())).toList();
    }
}
