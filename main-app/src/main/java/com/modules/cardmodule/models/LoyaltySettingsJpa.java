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
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
