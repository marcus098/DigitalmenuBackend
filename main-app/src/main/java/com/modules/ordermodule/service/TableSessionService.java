package com.modules.ordermodule.service;

import com.modules.authmodule.repository.UserRepository;
import com.modules.common.dto.TableDto;
import com.modules.common.finders.TableUtils;
import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.common.model.Comand;
import com.modules.common.model.Order;
import com.modules.common.model.enums.ComandStatus;
import com.modules.common.model.enums.ComandWaiterType;
import com.modules.common.model.enums.SessionStatus;
import com.modules.ordermodule.kafka.OrderUpdateProducer;
import com.modules.ordermodule.kafka.TableSessionUpdateProducer;
import com.modules.ordermodule.model.ComandFromWaiterJpa;
import com.modules.ordermodule.model.TableSessionJpa;
import com.modules.ordermodule.repository.MongoComandRepository;
import com.modules.ordermodule.repository.TableSessionRepository;
import com.modules.ordermodule.request.AddComandOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class TableSessionService {

    private static final Logger log = LoggerFactory.getLogger(TableSessionService.class);

    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9]{4}$");

    @Autowired
    private TableSessionRepository tableSessionRepository;
    @Autowired
    private MongoComandRepository mongoComandRepository;
    @Autowired
    private TableUtils tableUtils;
    @Autowired
    private OrderComandService orderComandService;
    @Autowired
    private TableSessionUpdateProducer tableSessionUpdateProducer;
    @Autowired
    private OrderUpdateProducer orderUpdateProducer;
    @Autowired
    private UserRepository userRepository;

    // =====================================================================
    // ============================= LOOKUP ================================
    // =====================================================================

    public Map<String, Object> getPublicTableInfo(long tableId, String localname) {
        Optional<TableDto> tableOpt = tableUtils.findById(tableId);
        if (tableOpt.isEmpty()) return null;
        TableDto table = tableOpt.get();

        Long agencyId = resolveAgencyByLocalname(localname);
        if (agencyId == null || !agencyId.equals(table.getIdAgency())) return null;

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("tableId", table.getId());
        resp.put("tableName", table.getName());

        Optional<TableSessionJpa> sessionOpt = tableSessionRepository
                .findByTableIdAndStatus(tableId, SessionStatus.OPEN);

        if (sessionOpt.isEmpty()) {
            resp.put("busy", false);
            return resp;
        }
        TableSessionJpa session = sessionOpt.get();
        resp.put("busy", true);
        resp.put("seats", session.getSeats());
        resp.put("joinable", session.getClients().size() < session.getSeats());
        resp.put("connectedCount", session.getClients().size());
        resp.put("sessionId", session.getId());
        return resp;
    }

    // =====================================================================
    // ============================== JOIN =================================
    // =====================================================================

    @Transactional
    public Map<String, Object> join(long tableId, String code, String localname, String clientSessionId) {
        if (clientSessionId == null || clientSessionId.isBlank()) {
            return errorResp(400, "INVALID_PAYLOAD");
        }
        if (code == null || !CODE_PATTERN.matcher(code.toUpperCase(Locale.ROOT)).matches()) {
            return errorResp(401, "INVALID_CODE");
        }
        Optional<TableDto> tableOpt = tableUtils.findById(tableId);
        if (tableOpt.isEmpty()) return errorResp(404, "TABLE_NOT_FOUND");
        TableDto table = tableOpt.get();

        Long agencyId = resolveAgencyByLocalname(localname);
        if (agencyId == null || !agencyId.equals(table.getIdAgency())) {
            return errorResp(404, "TABLE_NOT_FOUND");
        }

        Optional<TableSessionJpa> sessionOpt = tableSessionRepository
                .findByTableIdAndStatus(tableId, SessionStatus.OPEN);
        if (sessionOpt.isEmpty()) return errorResp(409, "TABLE_NOT_OPEN");
        TableSessionJpa session = sessionOpt.get();

        String normalized = code.toUpperCase(Locale.ROOT);
        if (!constantTimeEquals(normalized, session.getAccessCode())) {
            return errorResp(401, "INVALID_CODE");
        }

        synchronized (session.getId().intern()) {
            // re-load inside lock
            TableSessionJpa fresh = tableSessionRepository.findById(session.getId()).orElse(session);
            Optional<TableSessionJpa.SessionClient> existing = fresh.getClients().stream()
                    .filter(c -> clientSessionId.equals(c.getClientSessionId()))
                    .findFirst();

            if (existing.isEmpty()) {
                if (fresh.getClients().size() >= fresh.getSeats()) {
                    return errorResp(409, "TABLE_FULL");
                }
                TableSessionJpa.SessionClient sc = new TableSessionJpa.SessionClient();
                sc.setClientSessionId(clientSessionId);
                sc.setLabel("Coperto " + (fresh.getClients().size() + 1));
                sc.setReady(false);
                sc.setJoinedAt(OffsetDateTime.now());
                fresh.getClients().add(sc);
                tableSessionRepository.save(fresh);
                log.info("session join sessionId={} tableId={} idAgency={} clientSessionId={}",
                        fresh.getId(), tableId, agencyId, clientSessionId);
                emit(fresh.getId(), "state");
            }
            return buildJoinResponse(fresh, clientSessionId);
        }
    }

    // =====================================================================
    // ============================= STATE =================================
    // =====================================================================

    public Map<String, Object> getState(String sessionId, String clientSessionId) {
        Optional<TableSessionJpa> opt = tableSessionRepository.findById(sessionId);
        if (opt.isEmpty()) return null;
        return buildStateResponse(opt.get(), clientSessionId, false);
    }

    public Map<String, Object> getWaiterState(String sessionId) {
        Optional<TableSessionJpa> opt = tableSessionRepository.findById(sessionId);
        if (opt.isEmpty()) return null;
        return buildStateResponse(opt.get(), null, true);
    }

    public Map<String, Object> getWaiterStateByTableId(long tableId) {
        Optional<TableSessionJpa> opt = tableSessionRepository
                .findByTableIdAndStatus(tableId, SessionStatus.OPEN);
        if (opt.isEmpty()) return null;
        return buildStateResponse(opt.get(), null, true);
    }

    // =====================================================================
    // ============================ READY ==================================
    // =====================================================================

    @Transactional
    public Map<String, Object> setReady(String sessionId, String clientSessionId, List<AddComandOrder> draftOrder) {
        if (clientSessionId == null || clientSessionId.isBlank()) return errorResp(400, "INVALID_PAYLOAD");
        Optional<TableSessionJpa> opt = tableSessionRepository.findById(sessionId);
        if (opt.isEmpty()) return errorResp(404, "SESSION_NOT_FOUND");

        synchronized (sessionId.intern()) {
            TableSessionJpa session = tableSessionRepository.findById(sessionId).orElse(null);
            if (session == null) return errorResp(404, "SESSION_NOT_FOUND");
            if (session.getStatus() != SessionStatus.OPEN) return errorResp(409, "SESSION_NOT_OPEN");

            TableSessionJpa.SessionClient sc = findClient(session, clientSessionId);
            if (sc == null) return errorResp(403, "NOT_JOINED");
            sc.setDraftOrder(draftOrder == null ? new ArrayList<>() : draftOrder);
            sc.setReady(true);
            sc.setLastReadyAt(OffsetDateTime.now());
            tableSessionRepository.save(session);
            emit(sessionId, "state");
            return buildStateResponse(session, clientSessionId, false);
        }
    }

    @Transactional
    public Map<String, Object> setNotReady(String sessionId, String clientSessionId) {
        if (clientSessionId == null || clientSessionId.isBlank()) return errorResp(400, "INVALID_PAYLOAD");
        synchronized (sessionId.intern()) {
            Optional<TableSessionJpa> opt = tableSessionRepository.findById(sessionId);
            if (opt.isEmpty()) return errorResp(404, "SESSION_NOT_FOUND");
            TableSessionJpa session = opt.get();
            if (session.getStatus() != SessionStatus.OPEN) return errorResp(409, "SESSION_NOT_OPEN");
            TableSessionJpa.SessionClient sc = findClient(session, clientSessionId);
            if (sc == null) return errorResp(403, "NOT_JOINED");
            sc.setReady(false);
            tableSessionRepository.save(session);
            emit(sessionId, "state");
            return buildStateResponse(session, clientSessionId, false);
        }
    }

    // =====================================================================
    // ============================ SUBMIT =================================
    // =====================================================================

    @Transactional
    public Map<String, Object> submit(String sessionId, String clientSessionId) {
        synchronized (sessionId.intern()) {
            Optional<TableSessionJpa> opt = tableSessionRepository.findById(sessionId);
            if (opt.isEmpty()) return errorResp(404, "SESSION_NOT_FOUND");
            TableSessionJpa session = opt.get();
            if (session.getStatus() == SessionStatus.SUBMITTED) return errorResp(409, "ALREADY_SUBMITTED");
            if (session.getStatus() != SessionStatus.OPEN) return errorResp(409, "NOT_SUBMITTABLE");
            return doSubmit(session, false);
        }
    }

    @Transactional
    public Map<String, Object> forceSubmit(long tableId) {
        Optional<TableSessionJpa> opt = tableSessionRepository
                .findByTableIdAndStatus(tableId, SessionStatus.OPEN);
        if (opt.isEmpty()) return errorResp(404, "SESSION_NOT_FOUND");
        TableSessionJpa session = opt.get();
        synchronized (session.getId().intern()) {
            TableSessionJpa fresh = tableSessionRepository.findById(session.getId()).orElse(session);
            if (fresh.getStatus() != SessionStatus.OPEN) return errorResp(409, "NOT_SUBMITTABLE");
            return doSubmit(fresh, true);
        }
    }

    private Map<String, Object> doSubmit(TableSessionJpa session, boolean force) {
        long idAgency = session.getIdAgency();
        long tableId = session.getTableId();

        List<TableSessionJpa.SessionClient> readyClients = session.getClients().stream()
                .filter(TableSessionJpa.SessionClient::isReady)
                .filter(c -> c.getDraftOrder() != null && !c.getDraftOrder().isEmpty())
                .collect(Collectors.toList());

        if (readyClients.isEmpty()) return errorResp(409, "NOT_SUBMITTABLE");

        List<TableSessionJpa.SubmittedComand> created = new ArrayList<>();
        for (TableSessionJpa.SessionClient sc : readyClients) {
            try {
                String comandId = createComandForClient(idAgency, tableId, session.getId(),
                        sc.getClientSessionId(), sc.getDraftOrder());
                if (comandId != null) {
                    created.add(new TableSessionJpa.SubmittedComand(sc.getClientSessionId(), comandId));
                }
            } catch (Exception e) {
                ErrorLog.logger.error("Errore creazione comand per clientSessionId="
                        + sc.getClientSessionId() + " sessionId=" + session.getId(), e);
            }
        }

        if (created.isEmpty()) return errorResp(409, "NOT_SUBMITTABLE");

        session.setStatus(SessionStatus.SUBMITTED);
        session.setSubmittedAt(OffsetDateTime.now());
        session.setComands(created);
        // mark TableEntity busy in sync with what waiter probably already set
        ensureTableBusy(tableId, idAgency, session.getId(), session.getSeats());
        tableSessionRepository.save(session);

        log.info("session submit sessionId={} tableId={} idAgency={} comandCount={} force={}",
                session.getId(), tableId, idAgency, created.size(), force);

        emit(session.getId(), "submitted");
        return buildStateResponse(session, null, false);
    }

    private String createComandForClient(long idAgency, long tableId, String tableSessionId,
                                         String clientSessionId, List<AddComandOrder> draftOrders) {
        String comandId = UUID.randomUUID().toString() + "_" + System.currentTimeMillis();
        List<Order> orders = orderComandService.orderList(idAgency, 0L, draftOrders, comandId);
        ComandFromWaiterJpa comand = new ComandFromWaiterJpa(idAgency, tableId, 0L,
                ComandStatus.AWAIT, orders, tableSessionId);
        comand.setComandWaiterType(ComandWaiterType.TABLE);
        comand.setClientSessionId(clientSessionId);
        Comand saved = mongoComandRepository.save(comand);

        try {
            String json = String.format("{\"id\":\"%s\",\"status\":\"%s\",\"idAgency\":%d}",
                    saved.getId(), ComandStatus.AWAIT.toString(), idAgency);
            orderUpdateProducer.sendUpdate(json);
        } catch (Exception e) {
            ErrorLog.logger.error("Errore invio kafka order event", e);
        }
        return saved.getId();
    }

    private void ensureTableBusy(long tableId, long idAgency, String sessionId, int seats) {
        try {
            Optional<TableDto> tOpt = tableUtils.findByIdAndIdAgencyAndDeleted(tableId, idAgency);
            if (tOpt.isEmpty()) return;
            TableDto t = tOpt.get();
            boolean changed = false;
            if (!t.isBusy()) { t.setBusy(true); changed = true; }
            if (t.getSessionId() == null || !t.getSessionId().equals(sessionId)) {
                t.setSessionId(sessionId); changed = true;
            }
            if (seats > 0 && t.getSeats() != seats) { t.setSeats(seats); changed = true; }
            if (changed) tableUtils.save(t, idAgency);
        } catch (Exception e) {
            ErrorLog.logger.error("Errore ensureTableBusy tableId=" + tableId, e);
        }
    }

    // =====================================================================
    // ===================== WAITER OPEN / SEATS / CLOSE ===================
    // =====================================================================

    @Transactional
    public Map<String, Object> openSession(long tableId, long idAgency, int seats) {
        if (seats < 1) return errorResp(400, "INVALID_SEATS");
        Optional<TableDto> tOpt = tableUtils.findByIdAndIdAgencyAndDeleted(tableId, idAgency);
        if (tOpt.isEmpty()) return errorResp(404, "TABLE_NOT_FOUND");
        TableDto table = tOpt.get();

        Optional<TableSessionJpa> existing = tableSessionRepository
                .findByTableIdAndStatus(tableId, SessionStatus.OPEN);
        if (existing.isPresent()) return errorResp(409, "TABLE_ALREADY_OPEN");
        if (table.isBusy()) return errorResp(409, "TABLE_ALREADY_OPEN");

        String sessionId = UUID.randomUUID().toString() + "_" + System.currentTimeMillis();
        String accessCode = generateCode();

        TableSessionJpa session = new TableSessionJpa();
        session.setId(sessionId);
        session.setTableId(tableId);
        session.setIdAgency(idAgency);
        session.setLocalname(table.getLocation());
        session.setAccessCode(accessCode);
        session.setSeats(seats);
        session.setStatus(SessionStatus.OPEN);
        session.setCreatedAt(OffsetDateTime.now());
        tableSessionRepository.save(session);

        table.setBusy(true);
        table.setSeats(seats);
        table.setSessionId(sessionId);
        tableUtils.save(table, idAgency);

        log.info("session open sessionId={} tableId={} idAgency={} seats={}",
                sessionId, tableId, idAgency, seats);

        emit(sessionId, "state");

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("sessionId", sessionId);
        resp.put("accessCode", accessCode);
        resp.put("seats", seats);
        resp.put("tableId", tableId);
        return resp;
    }

    @Transactional
    public Map<String, Object> updateSeats(long tableId, long idAgency, int seats) {
        if (seats < 1) return errorResp(400, "INVALID_SEATS");
        Optional<TableSessionJpa> opt = tableSessionRepository
                .findByTableIdAndStatus(tableId, SessionStatus.OPEN);
        if (opt.isEmpty()) return errorResp(404, "SESSION_NOT_FOUND");
        TableSessionJpa session = opt.get();
        if (session.getIdAgency() != idAgency) return errorResp(403, "FORBIDDEN");

        synchronized (session.getId().intern()) {
            TableSessionJpa fresh = tableSessionRepository.findById(session.getId()).orElse(session);
            fresh.setSeats(seats);
            tableSessionRepository.save(fresh);

            Optional<TableDto> tOpt = tableUtils.findByIdAndIdAgencyAndDeleted(tableId, idAgency);
            tOpt.ifPresent(t -> { t.setSeats(seats); tableUtils.save(t, idAgency); });

            emit(fresh.getId(), "state");
            return buildStateResponse(fresh, null, true);
        }
    }

    @Transactional
    public Map<String, Object> closeSession(long tableId, long idAgency) {
        Optional<TableSessionJpa> opt = tableSessionRepository
                .findByTableIdAndStatus(tableId, SessionStatus.OPEN);
        if (opt.isEmpty()) {
            // also try submitted (post-submit close)
            // we don't currently index submitted sessions by tableId — best-effort no-op
        }
        TableSessionJpa session = opt.orElse(null);
        if (session == null) {
            // try to find any session pinned in TableEntity
            Optional<TableDto> tOpt = tableUtils.findByIdAndIdAgencyAndDeleted(tableId, idAgency);
            if (tOpt.isPresent() && tOpt.get().getSessionId() != null && !tOpt.get().getSessionId().isEmpty()) {
                session = tableSessionRepository.findById(tOpt.get().getSessionId()).orElse(null);
            }
        }
        if (session == null) return errorResp(404, "SESSION_NOT_FOUND");
        if (session.getIdAgency() != idAgency) return errorResp(403, "FORBIDDEN");

        synchronized (session.getId().intern()) {
            TableSessionJpa fresh = tableSessionRepository.findById(session.getId()).orElse(session);
            fresh.setStatus(SessionStatus.CLOSED);
            fresh.setClosedAt(OffsetDateTime.now());
            // discard drafts
            for (TableSessionJpa.SessionClient c : fresh.getClients()) {
                c.setDraftOrder(new ArrayList<>());
                c.setReady(false);
            }
            tableSessionRepository.save(fresh);

            Optional<TableDto> tOpt = tableUtils.findByIdAndIdAgencyAndDeleted(tableId, idAgency);
            tOpt.ifPresent(t -> {
                t.setBusy(false);
                t.setSeats(0);
                t.setSessionId("");
                tableUtils.save(t, idAgency);
            });

            log.info("session close sessionId={} tableId={} idAgency={}",
                    fresh.getId(), tableId, idAgency);

            emit(fresh.getId(), "closed");

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("sessionId", fresh.getId());
            resp.put("status", "CLOSED");
            return resp;
        }
    }

    // =====================================================================
    // ============================ HELPERS ================================
    // =====================================================================

    public boolean isAgencyOwner(long tableId, long idAgency) {
        Optional<TableDto> tOpt = tableUtils.findByIdAndIdAgencyAndDeleted(tableId, idAgency);
        return tOpt.isPresent();
    }

    private void emit(String sessionId, String type) {
        try {
            tableSessionUpdateProducer.sendUpdate(sessionId, type);
        } catch (Exception e) {
            ErrorLog.logger.error("Errore emit kafka table-session-updated sessionId=" + sessionId, e);
        }
    }

    private Long resolveAgencyByLocalname(String localname) {
        if (localname == null || localname.isBlank()) return null;
        return userRepository.findByUsernameAndDeleted(localname, false)
                .map(u -> u.getIdAgency()).orElse(null);
    }

    private TableSessionJpa.SessionClient findClient(TableSessionJpa s, String clientSessionId) {
        return s.getClients().stream()
                .filter(c -> clientSessionId.equals(c.getClientSessionId()))
                .findFirst().orElse(null);
    }

    private Map<String, Object> buildJoinResponse(TableSessionJpa s, String youId) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("sessionId", s.getId());
        resp.put("tableId", s.getTableId());
        resp.put("seats", s.getSeats());
        resp.put("status", s.getStatus().toString());

        TableSessionJpa.SessionClient you = findClient(s, youId);
        if (you != null) {
            Map<String, Object> youMap = new LinkedHashMap<>();
            youMap.put("clientSessionId", you.getClientSessionId());
            youMap.put("label", you.getLabel());
            youMap.put("ready", you.isReady());
            resp.put("you", youMap);
        }
        resp.put("clients", buildClientsList(s, youId, false));
        return resp;
    }

    private Map<String, Object> buildStateResponse(TableSessionJpa s, String youId, boolean waiterView) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("sessionId", s.getId());
        resp.put("tableId", s.getTableId());
        // table name lookup
        try {
            tableUtils.findById(s.getTableId()).ifPresent(t -> resp.put("tableName", t.getName()));
        } catch (Exception ignored) {}
        resp.put("seats", s.getSeats());
        if (waiterView) {
            resp.put("accessCode", s.getAccessCode());
        }
        resp.put("status", s.getStatus().toString());
        boolean submittable = s.getStatus() == SessionStatus.OPEN
                && !s.getClients().isEmpty()
                && s.getClients().stream().allMatch(TableSessionJpa.SessionClient::isReady)
                && s.getClients().stream().anyMatch(c -> c.getDraftOrder() != null && !c.getDraftOrder().isEmpty());
        resp.put("submittable", submittable);
        resp.put("clients", buildClientsList(s, youId, waiterView));

        List<Map<String, Object>> comands = new ArrayList<>();
        if (s.getComands() != null) {
            // fetch all referenced comands at once
            List<String> ids = s.getComands().stream().map(TableSessionJpa.SubmittedComand::getComandId).collect(Collectors.toList());
            Map<String, ComandStatus> statusByComand = new HashMap<>();
            if (!ids.isEmpty()) {
                List<Comand> existing = mongoComandRepository.findByTableSessionIdAndIdAgencyAndStatusIn(
                        s.getId(), s.getIdAgency(),
                        Arrays.asList(ComandStatus.AWAIT.toString(), ComandStatus.PENDING.toString(),
                                ComandStatus.PROGRESS.toString(), ComandStatus.COMPLETED.toString(),
                                ComandStatus.DELETED.toString()));
                for (Comand c : existing) statusByComand.put(c.getId(), c.getStatus());
            }
            for (TableSessionJpa.SubmittedComand sub : s.getComands()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("clientSessionId", sub.getClientSessionId());
                m.put("comandId", sub.getComandId());
                ComandStatus st = statusByComand.get(sub.getComandId());
                m.put("status", st == null ? "AWAIT" : st.toString());
                comands.add(m);
            }
        }
        resp.put("comands", comands);
        return resp;
    }

    private List<Map<String, Object>> buildClientsList(TableSessionJpa s, String youId, boolean waiterView) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (TableSessionJpa.SessionClient c : s.getClients()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("clientSessionId", c.getClientSessionId());
            m.put("label", c.getLabel());
            m.put("ready", c.isReady());
            m.put("isYou", youId != null && youId.equals(c.getClientSessionId()));
            m.put("draftItemsCount", c.getDraftOrder() == null ? 0
                    : c.getDraftOrder().stream().mapToInt(o -> o.getProducts() == null ? 0 : o.getProducts().size()).sum());
            if (waiterView) {
                m.put("draftOrder", c.getDraftOrder() == null ? new ArrayList<>() : c.getDraftOrder());
            }
            list.add(m);
        }
        return list;
    }

    private Map<String, Object> errorResp(int httpStatus, String code) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("__status", httpStatus);
        m.put("error", code);
        return m;
    }

    public static int extractStatus(Map<String, Object> resp) {
        Object s = resp.get("__status");
        if (s instanceof Integer) {
            resp.remove("__status");
            return (Integer) s;
        }
        return 200;
    }

    private static String generateCode() {
        StringBuilder sb = new StringBuilder(4);
        for (int i = 0; i < 4; i++) sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        return sb.toString();
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] ab = a.getBytes();
        byte[] bb = b.getBytes();
        if (ab.length != bb.length) return false;
        return MessageDigest.isEqual(ab, bb);
    }
}
