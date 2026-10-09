package com.modules.takeawaymodule.dto;

import java.time.LocalDateTime;

/** Stato del "Sospendi asporto". pausedUntil null con paused = true: fino a ripresa manuale. */
public record PauseStatusDto(boolean paused, LocalDateTime pausedUntil) {
}
