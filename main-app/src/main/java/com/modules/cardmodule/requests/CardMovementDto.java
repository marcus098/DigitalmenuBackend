package com.modules.cardmodule.requests;

import com.modules.cardmodule.models.CardClaim;
import com.modules.cardmodule.models.CardMovement;

public record CardMovementDto(String type, int delta, Integer balanceAfter, Long amountCents, String comandId,
                              String note, String createdAt) {

    public static CardMovementDto of(CardMovement m) {
        return new CardMovementDto(m.getType().name(), m.getDelta(), m.getBalanceAfter(), m.getAmountCents(),
                m.getComandId(), m.getNote(), m.getCreatedAt() == null ? null : m.getCreatedAt().toString());
    }

    /** Utilizzo dello storico precedente: il saldo dopo l'operazione non era registrato. */
    public static CardMovementDto ofLegacy(CardClaim c) {
        return new CardMovementDto(CardMovement.Type.CLAIM.name(), -c.getPoints(), null, null, null, null,
                c.getClaimAt() == null ? null : c.getClaimAt().toString());
    }
}
