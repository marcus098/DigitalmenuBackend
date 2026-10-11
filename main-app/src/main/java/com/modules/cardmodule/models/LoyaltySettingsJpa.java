package com.modules.cardmodule.models;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/**
 * Regole della tessera fedeltà a livello di locale. Valgono per tutte le tessere dell'agency
 * (anche quelle già emesse): cambiando "1 punto ogni X €" non serve riemettere le carte.
 * Campi null = non configurato: si usano i valori salvati sulla singola tessera (tessere storiche).
 */
@Entity
@Table(name = "loyalty_settings")
public class LoyaltySettingsJpa {

    @Id
    private Long idAgency;

    /** Tessere a punti: € di spesa per ottenere 1 punto. */
    private Double eurosPerPoint;

    /** Tessere a punti: valore in € di 1 punto quando viene usato in cassa (null = lo decide l'operatore). */
    private Double pointValue;

    /** Tessere a timbri: timbri necessari per il premio. */
    private Integer stampsForPrize;

    /** Tessere a timbri: descrizione del premio (es. "Caffè omaggio"). */
    private String stampsPrize;

    /** Tipo di tessera emessa dal locale: POINTS, VISITS (timbri) o null = si sceglie a ogni emissione. */
    private String programType;

    /** NONE, YEARLY (azzeramento ogni anno a una data), INACTIVITY (azzeramento dopo N mesi senza movimenti). */
    private String expiryPolicy;
    private Integer expiryMonth;
    private Integer expiryDay;
    private Integer inactivityMonths;

    /** Tessere a visite: spesa minima perché il conto valga come visita (null = qualsiasi importo). */
    private Double minSpendForVisit;
    /** Tessere a visite: al massimo una visita al giorno. */
    private Boolean oneVisitPerDay;

    private OffsetDateTime updatedAt;

    public LoyaltySettingsJpa() {}

    public LoyaltySettingsJpa(Long idAgency) {
        this.idAgency = idAgency;
    }

    public Long getIdAgency() { return idAgency; }
    public void setIdAgency(Long idAgency) { this.idAgency = idAgency; }
    public Double getEurosPerPoint() { return eurosPerPoint; }
    public void setEurosPerPoint(Double eurosPerPoint) { this.eurosPerPoint = eurosPerPoint; }
    public Double getPointValue() { return pointValue; }
    public void setPointValue(Double pointValue) { this.pointValue = pointValue; }
    public Integer getStampsForPrize() { return stampsForPrize; }
    public void setStampsForPrize(Integer stampsForPrize) { this.stampsForPrize = stampsForPrize; }
    public String getStampsPrize() { return stampsPrize; }
    public void setStampsPrize(String stampsPrize) { this.stampsPrize = stampsPrize; }
    public String getProgramType() { return programType; }
    public void setProgramType(String programType) { this.programType = programType; }
    public String getExpiryPolicy() { return expiryPolicy; }
    public void setExpiryPolicy(String expiryPolicy) { this.expiryPolicy = expiryPolicy; }
    public Integer getExpiryMonth() { return expiryMonth; }
    public void setExpiryMonth(Integer expiryMonth) { this.expiryMonth = expiryMonth; }
    public Integer getExpiryDay() { return expiryDay; }
    public void setExpiryDay(Integer expiryDay) { this.expiryDay = expiryDay; }
    public Integer getInactivityMonths() { return inactivityMonths; }
    public void setInactivityMonths(Integer inactivityMonths) { this.inactivityMonths = inactivityMonths; }
    public Double getMinSpendForVisit() { return minSpendForVisit; }
    public void setMinSpendForVisit(Double minSpendForVisit) { this.minSpendForVisit = minSpendForVisit; }
    public Boolean getOneVisitPerDay() { return oneVisitPerDay; }
    public void setOneVisitPerDay(Boolean oneVisitPerDay) { this.oneVisitPerDay = oneVisitPerDay; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
