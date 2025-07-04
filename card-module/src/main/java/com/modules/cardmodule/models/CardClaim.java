package com.modules.cardmodule.models;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "cardClaims")
public class CardClaim {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;
    private long idCard;
    private OffsetDateTime claimAt;
    private long idAgency;
    private long idUser;
    private int points;

    public CardClaim() {}

    public CardClaim(long idCard, OffsetDateTime claimAt, long idAgency, long idUser, int points) {
        this.idCard = idCard;
        this.claimAt = claimAt;
        this.idAgency = idAgency;
        this.idUser = idUser;
        this.points = points;
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getIdCard() {
        return idCard;
    }

    public void setIdCard(long idCard) {
        this.idCard = idCard;
    }

    public OffsetDateTime getClaimAt() {
        return claimAt;
    }

    public void setClaimAt(OffsetDateTime claimAt) {
        this.claimAt = claimAt;
    }

    public long getIdAgency() {
        return idAgency;
    }

    public void setIdAgency(long idAgency) {
        this.idAgency = idAgency;
    }

    public long getIdUser() {
        return idUser;
    }

    public void setIdUser(long idUser) {
        this.idUser = idUser;
    }

    @Override
    public String toString() {
        return "CardClaim{" +
                "id=" + id +
                ", idCard=" + idCard +
                ", claimAt=" + claimAt +
                ", idAgency=" + idAgency +
                ", idUser=" + idUser +
                '}';
    }
}
