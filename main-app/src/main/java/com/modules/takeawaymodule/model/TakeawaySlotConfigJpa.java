package com.modules.takeawaymodule.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "takeaway_slot_config")
public class TakeawaySlotConfigJpa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_agency", nullable = false, unique = true)
    private Long idAgency;

    @Column(name = "slot_duration_minutes", nullable = false)
    private int slotDurationMinutes = 15;

    @Column(name = "max_orders_per_slot", nullable = false)
    private int maxOrdersPerSlot = 5;

    @Column(name = "max_products_per_slot", nullable = false)
    private int maxProductsPerSlot = 20;

    /** JSON: { "MONDAY": [{"start":"19:00","end":"23:00"},{...}], ... } — chiavi DayOfWeek.name() */
    @Column(name = "weekly_hours", columnDefinition = "TEXT", nullable = false)
    private String weeklyHours = "{}";

    /** JSON array di stringhe "YYYY-MM-DD" per chiusure straordinarie. */
    @Column(name = "closed_dates", columnDefinition = "TEXT", nullable = false)
    private String closedDates = "[]";

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public TakeawaySlotConfigJpa() {}

    public TakeawaySlotConfigJpa(Long idAgency) {
        this.idAgency = idAgency;
    }

    @PreUpdate
    public void onUpdate() { this.updatedAt = OffsetDateTime.now(); }

    // ── Getters / Setters ────────────────────────────────────────────────────
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getIdAgency() { return idAgency; }
    public void setIdAgency(Long idAgency) { this.idAgency = idAgency; }
    public int getSlotDurationMinutes() { return slotDurationMinutes; }
    public void setSlotDurationMinutes(int v) { this.slotDurationMinutes = v; }
    public int getMaxOrdersPerSlot() { return maxOrdersPerSlot; }
    public void setMaxOrdersPerSlot(int v) { this.maxOrdersPerSlot = v; }
    public int getMaxProductsPerSlot() { return maxProductsPerSlot; }
    public void setMaxProductsPerSlot(int v) { this.maxProductsPerSlot = v; }
    public String getWeeklyHours() { return weeklyHours; }
    public void setWeeklyHours(String v) { this.weeklyHours = v; }
    public String getClosedDates() { return closedDates; }
    public void setClosedDates(String v) { this.closedDates = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
}
