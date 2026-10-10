package com.modules.common.model;

import java.time.LocalDateTime;

/**
 * Chiusura del conto in cassa: quanto è stato davvero incassato e con quale sconto.
 * Gli importi della comanda restano a prezzo pieno; incassi e report usano {@code totalCents}.
 */
public class ComandCheckout {
    /** Totale prima degli sconti (include le modifiche fatte in cassa: quantità, aggiunte). */
    private long subtotalCents;
    /** subtotalCents − totalCents (negativo = maggiorazione). */
    private long discountCents;
    /** Importo incassato. */
    private long totalCents;
    /** PCT = sconto percentuale, FINAL = prezzo finale digitato dall'operatore. */
    private String discountMode;
    private Double discountPct;
    /** Parte dello sconto dovuta all'uso dei punti tessera. */
    private long cardDiscountCents;
    private Long cardId;
    private int pointsUsed;
    private int pointsEarned;
    private boolean stampRedeemed;
    /** true se i movimenti sulla tessera non sono andati a buon fine (da verificare a mano). */
    private boolean loyaltyError;
    /** Comanda già pagata online: l'incasso è già contato nei pagamenti, qui non va sommato di nuovo. */
    private boolean paidOnline;
    private LocalDateTime closedAt;
    private Long closedBy;

    public ComandCheckout() {}

    public long getSubtotalCents() { return subtotalCents; }
    public void setSubtotalCents(long subtotalCents) { this.subtotalCents = subtotalCents; }
    public long getDiscountCents() { return discountCents; }
    public void setDiscountCents(long discountCents) { this.discountCents = discountCents; }
    public long getTotalCents() { return totalCents; }
    public void setTotalCents(long totalCents) { this.totalCents = totalCents; }
    public String getDiscountMode() { return discountMode; }
    public void setDiscountMode(String discountMode) { this.discountMode = discountMode; }
    public Double getDiscountPct() { return discountPct; }
    public void setDiscountPct(Double discountPct) { this.discountPct = discountPct; }
    public long getCardDiscountCents() { return cardDiscountCents; }
    public void setCardDiscountCents(long cardDiscountCents) { this.cardDiscountCents = cardDiscountCents; }
    public Long getCardId() { return cardId; }
    public void setCardId(Long cardId) { this.cardId = cardId; }
    public int getPointsUsed() { return pointsUsed; }
    public void setPointsUsed(int pointsUsed) { this.pointsUsed = pointsUsed; }
    public int getPointsEarned() { return pointsEarned; }
    public void setPointsEarned(int pointsEarned) { this.pointsEarned = pointsEarned; }
    public boolean isStampRedeemed() { return stampRedeemed; }
    public void setStampRedeemed(boolean stampRedeemed) { this.stampRedeemed = stampRedeemed; }
    public boolean isLoyaltyError() { return loyaltyError; }
    public void setLoyaltyError(boolean loyaltyError) { this.loyaltyError = loyaltyError; }
    public boolean isPaidOnline() { return paidOnline; }
    public void setPaidOnline(boolean paidOnline) { this.paidOnline = paidOnline; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
    public Long getClosedBy() { return closedBy; }
    public void setClosedBy(Long closedBy) { this.closedBy = closedBy; }
}
