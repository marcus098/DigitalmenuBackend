package com.modules.printmodule.repository;

import com.modules.printmodule.model.PrintJobDoc;
import com.modules.printmodule.model.PrintJobStatus;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class PrintJobRepository {

    private final MongoTemplate mongo;

    public PrintJobRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /** @throws org.springframework.dao.DuplicateKeyException se esiste già un NEW_ORDER per (printer, comand). */
    public PrintJobDoc insert(PrintJobDoc job) {
        return mongo.insert(job);
    }

    public Optional<PrintJobDoc> findByIdAndPrinterId(String id, String printerId) {
        return Optional.ofNullable(mongo.findOne(
                Query.query(Criteria.where("_id").is(id).and("printerId").is(printerId)), PrintJobDoc.class));
    }

    /** Primo job PENDING (FIFO) senza modificarlo: usato dal poll CloudPRNT per rispondere jobReady. */
    public Optional<PrintJobDoc> peekNextPending(String printerId) {
        return Optional.ofNullable(mongo.findOne(pendingQuery(printerId), PrintJobDoc.class));
    }

    /** Prende in carico atomicamente il prossimo job PENDING → SENT, attempts++. */
    public Optional<PrintJobDoc> claimNext(String printerId) {
        return Optional.ofNullable(mongo.findAndModify(pendingQuery(printerId), claimUpdate(),
                FindAndModifyOptions.options().returnNew(true), PrintJobDoc.class));
    }

    /** Come claimNext ma per un job specifico (jobToken CloudPRNT). Se già SENT lo restituisce senza ri-contarlo. */
    public Optional<PrintJobDoc> claimById(String printerId, String jobId) {
        Query q = Query.query(Criteria.where("_id").is(jobId).and("printerId").is(printerId).and("status").is(PrintJobStatus.PENDING));
        PrintJobDoc claimed = mongo.findAndModify(q, claimUpdate(), FindAndModifyOptions.options().returnNew(true), PrintJobDoc.class);
        if (claimed != null) return Optional.of(claimed);
        return findByIdAndPrinterId(jobId, printerId).filter(j -> j.getStatus() == PrintJobStatus.SENT);
    }

    /** Job SENT più recente della stampante (fallback per DELETE CloudPRNT senza token). */
    public Optional<PrintJobDoc> findLatestSent(String printerId) {
        Query q = Query.query(Criteria.where("printerId").is(printerId).and("status").is(PrintJobStatus.SENT))
                .with(Sort.by(Sort.Direction.DESC, "sentAt"));
        return Optional.ofNullable(mongo.findOne(q, PrintJobDoc.class));
    }

    public void markPrinted(String jobId) {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(jobId).and("status").is(PrintJobStatus.SENT)),
                new Update().set("status", PrintJobStatus.PRINTED).set("printedAt", Instant.now()).unset("lastError"),
                PrintJobDoc.class);
    }

    /** Errore di stampa: torna PENDING se attempts < maxAttempts, altrimenti FAILED. */
    public void markFailedAttempt(PrintJobDoc job, String error, int maxAttempts) {
        PrintJobStatus next = job.getAttempts() >= maxAttempts ? PrintJobStatus.FAILED : PrintJobStatus.PENDING;
        Update u = new Update().set("status", next).set("lastError", error == null ? "" : truncate(error));
        mongo.updateFirst(Query.query(Criteria.where("_id").is(job.getId()).and("status").is(PrintJobStatus.SENT)), u, PrintJobDoc.class);
    }

    /** Job SENT da più di cutoff senza conferma (stampante spenta / bridge morto). */
    public List<PrintJobDoc> findStaleSent(Instant cutoff) {
        return mongo.find(Query.query(Criteria.where("status").is(PrintJobStatus.SENT).and("sentAt").lt(cutoff)).limit(500),
                PrintJobDoc.class);
    }

    public List<PrintJobDoc> findRecentByPrinter(String printerId, int limit) {
        Query q = Query.query(Criteria.where("printerId").is(printerId))
                .with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(limit);
        return mongo.find(q, PrintJobDoc.class);
    }

    public void deleteByPrinterId(String printerId) {
        mongo.remove(Query.query(Criteria.where("printerId").is(printerId)), PrintJobDoc.class);
    }

    private Query pendingQuery(String printerId) {
        return Query.query(Criteria.where("printerId").is(printerId).and("status").is(PrintJobStatus.PENDING))
                .with(Sort.by(Sort.Direction.ASC, "createdAt"));
    }

    private Update claimUpdate() {
        return new Update().set("status", PrintJobStatus.SENT).set("sentAt", Instant.now()).inc("attempts", 1);
    }

    private static String truncate(String s) {
        return s.length() > 300 ? s.substring(0, 300) : s;
    }
}
