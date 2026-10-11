package com.modules.cardmodule.models;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/** Storico dei movimenti di una tessera: accrediti, utilizzi, azzeramenti e scadenze. */
@Entity
@Table(name = "card_movements", indexes = @Index(name = "idx_card_movements_card", columnList = "idCard, createdAt"))
public class CardMovement {

    public enum Type {
        /** Accredito alla chiusura del conto. */ EARN,
        /** Accredito manuale dalla pagina tessere. */ ADD,
        /** Punti usati / premio riscattato. */ CLAIM,
        /** Azzeramento manuale. */ RESET,
        /** Azzeramento automatico (annuale o per inattività). */ EXPIRE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private long idCard;
    private long idAgency;
    private Long idUser;
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Type type;
    /** Variazione del saldo (negativa per utilizzi e azzeramenti). */
    private int delta;
    private int balanceAfter;
    /** Importo del conto per gli accrediti in cassa. */
    private Long amountCents;
    @Column(length = 64)
    private String comandId;
    @Column(length = 200)
    private String note;
    private OffsetDateTime createdAt;

    public CardMovement() {}

    public CardMovement(CardJpa card, Long idUser, Type type, int delta, Long amountCents, String comandId, String note) {
        this.idCard = card.getId();
        this.idAgency = card.getIdAgency();
        this.idUser = idUser;
        this.type = type;
        this.delta = delta;
        this.balanceAfter = card.getActualValue();
        this.amountCents = amountCents;
        this.comandId = comandId;
        this.note = note;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public long getIdCard() { return idCard; }
    public long getIdAgency() { return idAgency; }
    public Long getIdUser() { return idUser; }
    public Type getType() { return type; }
    public int getDelta() { return delta; }
    public int getBalanceAfter() { return balanceAfter; }
    public Long getAmountCents() { return amountCents; }
    public String getComandId() { return comandId; }
    public String getNote() { return note; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
