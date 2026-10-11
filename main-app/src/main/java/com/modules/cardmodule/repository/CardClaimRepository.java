package com.modules.cardmodule.repository;

import com.modules.cardmodule.models.CardClaim;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface CardClaimRepository extends CrudRepository<CardClaim, Long> {
    /** Utilizzi registrati prima dello storico movimenti (tabella card_movements). */
    List<CardClaim> findAllByIdCardAndIdAgency(long idCard, long idAgency);

}
