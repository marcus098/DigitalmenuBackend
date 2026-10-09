package com.modules.ordermodule.service;

import com.modules.common.model.enums.ComandStatus;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.function.Consumer;

/**
 * Transizioni di stato delle comande come UPDATE condizionali su Mongo (compare-and-set sullo stato attuale).
 * Niente read-modify-write: un cambio stato non può sovrascrivere i campi di pagamento scritti in parallelo dal
 * webhook Stripe, e due transizioni concorrenti (es. approvazione vs rifiuto automatico) non vincono entrambe.
 */
@Component
public class ComandStatusUpdater {

    public static final String COLLECTION = "comand";

    private final MongoTemplate mongoTemplate;

    public ComandStatusUpdater(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * @param idAgency null = nessun vincolo sull'agency (chiamate interne: webhook, job)
     * @param extra    campi aggiuntivi da impostare nello stesso update (può essere null)
     * @return true se la comanda era in uno degli stati {@code from} ed è stata aggiornata
     */
    public boolean compareAndSet(String comandId, Long idAgency, Collection<ComandStatus> from, ComandStatus to,
                                 Consumer<Update> extra) {
        if (comandId == null) return false;
        Criteria c = Criteria.where("_id").is(comandId)
                .and("status").in(from.stream().map(Enum::name).toList());
        if (idAgency != null) c = c.and("idAgency").is(idAgency);
        Update u = new Update().set("status", to.name()).set("updatedAt", LocalDateTime.now());
        if (extra != null) extra.accept(u);
        return mongoTemplate.updateFirst(new Query(c), u, COLLECTION).getModifiedCount() > 0;
    }

    /** Aggiorna solo campi accessori (non lo stato), se la comanda è ancora in uno degli stati indicati. */
    public boolean setIfStatusIn(String comandId, Collection<ComandStatus> in, Update update) {
        Query q = new Query(Criteria.where("_id").is(comandId).and("status").in(in.stream().map(Enum::name).toList()));
        return mongoTemplate.updateFirst(q, update, COLLECTION).getModifiedCount() > 0;
    }
}
