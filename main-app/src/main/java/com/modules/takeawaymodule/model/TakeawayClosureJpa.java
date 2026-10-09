package com.modules.takeawaymodule.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Chiusura dell'asporto per un giorno (fromDate == toDate) o per un periodo (ferie), estremi inclusi.
 */
@Entity
@Table(name = "takeaway_closure", indexes = @Index(columnList = "id_agency,to_date"))
public class TakeawayClosureJpa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_agency", nullable = false)
    private Long idAgency;

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "to_date", nullable = false)
    private LocalDate toDate;

    @Column(name = "note", length = 200)
    private String note;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public TakeawayClosureJpa() {}

    public TakeawayClosureJpa(Long idAgency, LocalDate fromDate, LocalDate toDate, String note) {
        this.idAgency = idAgency;
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.note = note;
    }

    public boolean covers(LocalDate d) {
        return !d.isBefore(fromDate) && !d.isAfter(toDate);
    }

    public Long getId() { return id; }
    public Long getIdAgency() { return idAgency; }
    public void setIdAgency(Long v) { this.idAgency = v; }
    public LocalDate getFromDate() { return fromDate; }
    public void setFromDate(LocalDate v) { this.fromDate = v; }
    public LocalDate getToDate() { return toDate; }
    public void setToDate(LocalDate v) { this.toDate = v; }
    public String getNote() { return note; }
    public void setNote(String v) { this.note = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
