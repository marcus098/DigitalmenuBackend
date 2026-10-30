package com.modules.aimodule.service;

import com.modules.aimodule.model.InsightKind;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * Periodo di un riepilogo (estremi inclusi), calcolato sulla data di "oggi" del locale (Europe/Rome):
 * DAILY = ieri, WEEKLY = ultima settimana completa lun–dom, MENU_TIPS = 28 giorni terminati con l'ultima domenica.
 */
public record InsightPeriod(InsightKind kind, LocalDate from, LocalDate to) {

    public static final int MENU_TIPS_DAYS = 28;

    public static InsightPeriod of(InsightKind kind, LocalDate today) {
        LocalDate lastSunday = today.with(TemporalAdjusters.previous(DayOfWeek.SUNDAY));
        return switch (kind) {
            case DAILY -> new InsightPeriod(kind, today.minusDays(1), today.minusDays(1));
            case WEEKLY -> new InsightPeriod(kind, lastSunday.minusDays(6), lastSunday);
            case MENU_TIPS -> new InsightPeriod(kind, lastSunday.minusDays(MENU_TIPS_DAYS - 1), lastSunday);
        };
    }
}
