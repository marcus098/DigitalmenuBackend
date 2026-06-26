package com.modules.takeawaymodule.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

/**
 * Override "vivo" su un singolo slot di un singolo giorno.
 *  - closed = true → slot chiuso a mano (es. sold out, pausa cucina)
 *  - manualOrdersCount / manualProductsCount → ordini telefonici registrati a mano
 *    che concorrono al riempimento dello slot ma non hanno un Comand su Mongo.
 */
@Entity
@Table(
    name = "takeaway_slot_override",
    uniqueConstraints = @UniqueConstraint(columnNames = {"id_agency", "slot_date", "slot_time"})
)
public class TakeawaySlotOverrideJpa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_agency", nullable = false)
    private Long idAgency;

    @Column(name = "slot_date", nullable = false)
    private LocalDate slotDate;

    @Column(name = "slot_time", nullable = false)
    private LocalTime slotTime;

    @Column(nullable = false)
    private boolean closed = false;

    @Column(name = "manual_orders_count", nullable = false)
    private int manualOrdersCount = 0;

    @Column(name = "manual_products_count", nullable = false)
    private int manualProductsCount = 0;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public TakeawaySlotOverrideJpa() {}

    public TakeawaySlotOverrideJpa(Long idAgency, LocalDate slotDate, LocalTime slotTime) {
        this.idAgency = idAgency;
        this.slotDate = slotDate;
        this.slotTime = slotTime;
    }

    @PreUpdate
    public void onUpdate() { this.updatedAt = OffsetDateTime.now(); }

    public Long getId() { return id; }
    public Long getIdAgency() { return idAgency; }
    public void setIdAgency(Long v) { this.idAgency = v; }
    public LocalDate getSlotDate() { return slotDate; }
    public void setSlotDate(LocalDate v) { this.slotDate = v; }
    public LocalTime getSlotTime() { return slotTime; }
    public void setSlotTime(LocalTime v) { this.slotTime = v; }
    public boolean isClosed() { return closed; }
    public void setClosed(boolean v) { this.closed = v; }
    public int getManualOrdersCount() { return manualOrdersCount; }
    public void setManualOrdersCount(int v) { this.manualOrdersCount = v; }
    public int getManualProductsCount() { return manualProductsCount; }
    public void setManualProductsCount(int v) { this.manualProductsCount = v; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
}
