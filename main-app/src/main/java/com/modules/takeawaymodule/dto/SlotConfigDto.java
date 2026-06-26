package com.modules.takeawaymodule.dto;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DTO scambiato col frontend per la configurazione "fissa" degli slot.
 *  - weeklyHours: chiave = nome giorno (MONDAY..SUNDAY), valore = lista di range "HH:mm"-"HH:mm"
 *  - closedDates: ["YYYY-MM-DD", ...] chiusure straordinarie
 */
public class SlotConfigDto {
    private int slotDurationMinutes = 15;
    private int maxOrdersPerSlot = 5;
    private int maxProductsPerSlot = 20;
    private Map<String, List<TimeRange>> weeklyHours = new LinkedHashMap<>();
    private List<String> closedDates = new java.util.ArrayList<>();

    public static class TimeRange {
        private String start;
        private String end;
        public TimeRange() {}
        public TimeRange(String start, String end) { this.start = start; this.end = end; }
        public String getStart() { return start; }
        public void setStart(String v) { this.start = v; }
        public String getEnd() { return end; }
        public void setEnd(String v) { this.end = v; }
    }

    public int getSlotDurationMinutes() { return slotDurationMinutes; }
    public void setSlotDurationMinutes(int v) { this.slotDurationMinutes = v; }
    public int getMaxOrdersPerSlot() { return maxOrdersPerSlot; }
    public void setMaxOrdersPerSlot(int v) { this.maxOrdersPerSlot = v; }
    public int getMaxProductsPerSlot() { return maxProductsPerSlot; }
    public void setMaxProductsPerSlot(int v) { this.maxProductsPerSlot = v; }
    public Map<String, List<TimeRange>> getWeeklyHours() { return weeklyHours; }
    public void setWeeklyHours(Map<String, List<TimeRange>> v) { this.weeklyHours = v; }
    public List<String> getClosedDates() { return closedDates; }
    public void setClosedDates(List<String> v) { this.closedDates = v; }
}
