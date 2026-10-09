package com.modules.printmodule.repository;

import com.modules.printmodule.model.PrinterDoc;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository su MongoTemplate (niente Spring Data interface: il package non è in @EnableMongoRepositories
 * e servono comunque operazioni atomiche).
 */
@Repository
public class PrinterRepository {

    private final MongoTemplate mongo;

    public PrinterRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public PrinterDoc save(PrinterDoc printer) {
        return mongo.save(printer);
    }

    public Optional<PrinterDoc> findByIdAndIdAgency(String id, long idAgency) {
        return Optional.ofNullable(mongo.findOne(
                Query.query(Criteria.where("_id").is(id).and("idAgency").is(idAgency)), PrinterDoc.class));
    }

    public List<PrinterDoc> findByIdAgency(long idAgency) {
        return mongo.find(Query.query(Criteria.where("idAgency").is(idAgency))
                .with(Sort.by(Sort.Direction.ASC, "createdAt")), PrinterDoc.class);
    }

    public List<PrinterDoc> findEnabledByIdAgency(long idAgency) {
        return mongo.find(Query.query(Criteria.where("idAgency").is(idAgency).and("enabled").is(true)), PrinterDoc.class);
    }

    public Optional<PrinterDoc> findByDeviceTokenHash(String hash) {
        return Optional.ofNullable(mongo.findOne(Query.query(Criteria.where("deviceTokenHash").is(hash)), PrinterDoc.class));
    }

    public void touch(String id, String mac, String lastStatus) {
        Update u = new Update().set("lastSeenAt", Instant.now());
        if (mac != null) u.set("macAddress", mac);
        if (lastStatus != null) u.set("lastStatus", lastStatus.length() > 200 ? lastStatus.substring(0, 200) : lastStatus);
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)), u, PrinterDoc.class);
    }

    /** Come touch ma scrive al massimo ogni 30s (i dispositivi fanno poll ogni 2-5s). */
    public void touchIfStale(PrinterDoc p, String mac, String lastStatus) {
        boolean stale = p.getLastSeenAt() == null || p.getLastSeenAt().isBefore(Instant.now().minusSeconds(30));
        boolean statusChanged = lastStatus != null && !lastStatus.equals(p.getLastStatus());
        if (stale || mac != null || statusChanged) touch(p.getId(), mac, lastStatus);
    }

    public void delete(PrinterDoc printer) {
        mongo.remove(printer);
    }
}
