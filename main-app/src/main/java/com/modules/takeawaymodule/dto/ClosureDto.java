package com.modules.takeawaymodule.dto;

/** Chiusura asporto (giorno singolo se from == to). Date "YYYY-MM-DD". */
public record ClosureDto(Long id, String from, String to, String note) {
}
