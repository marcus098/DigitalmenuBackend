package com.modules.mainapp.superadmin;

import com.modules.authmodule.model.AgencyJpa;
import com.modules.authmodule.model.User;
import com.modules.authmodule.model.superadmin.*;
import com.modules.authmodule.repository.AgencyRepository;
import com.modules.authmodule.repository.UserRepository;
import com.modules.authmodule.repository.superadmin.AgencyAdminInfoRepository;
import com.modules.authmodule.repository.superadmin.AgencyNoteRepository;
import com.modules.authmodule.repository.superadmin.SuperadminAuditRepository;
import com.modules.authmodule.service.AuthService;
import com.modules.authmodule.service.SubscriptionRules;
import com.modules.common.dto.UserDto;
import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.common.responses.AuthResponse;
import com.modules.mainapp.payment.entity.AgencyPaymentAccountJpa;
import com.modules.mainapp.payment.entity.PaymentProvider;
import com.modules.mainapp.payment.repository.AgencyPaymentAccountRepository;
import com.modules.mainapp.superadmin.SuperadminDtos.*;
import com.modules.servletconfiguration.security.JwtService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.modules.common.utilities.Constants.*;

/**
 * Pannello della piattaforma (superadmin): elenco locali, dati abbonamento, note interne, impersonazione, audit.
 * Tutti i metodi presuppongono un chiamante ROLE_SUPERADMIN (verificato da SecurityConf + @PreAuthorize).
 */
@Service
public class SuperadminService {

    public static final int REASON_MIN = 3;
    public static final int REASON_MAX = 500;
    public static final int FIELD_MAX = 100;
    public static final int AUDIT_DEFAULT_LIMIT = 100;
    public static final int AUDIT_MAX_LIMIT = 500;
    public static final int AGENCY_AUDIT_LIMIT = 50;

    /** Chi sta facendo l'operazione (dal token del superadmin). */
    public record Actor(long id, String email, String ip) {
    }

    private final AgencyRepository agencyRepository;
    private final UserRepository userRepository;
    private final AgencyAdminInfoRepository adminInfoRepository;
    private final AgencyNoteRepository noteRepository;
    private final SuperadminAuditRepository auditRepository;
    private final SuperadminAuditService auditService;
    private final AgencyPaymentAccountRepository paymentAccountRepository;
    private final OrderStatsProvider orderStats;
    private final JwtService jwtService;
    private final AuthService authService;

    public SuperadminService(AgencyRepository agencyRepository,
                             UserRepository userRepository,
                             AgencyAdminInfoRepository adminInfoRepository,
                             AgencyNoteRepository noteRepository,
                             SuperadminAuditRepository auditRepository,
                             SuperadminAuditService auditService,
                             AgencyPaymentAccountRepository paymentAccountRepository,
                             OrderStatsProvider orderStats,
                             JwtService jwtService,
                             AuthService authService) {
        this.agencyRepository = agencyRepository;
        this.userRepository = userRepository;
        this.adminInfoRepository = adminInfoRepository;
        this.noteRepository = noteRepository;
        this.auditRepository = auditRepository;
        this.auditService = auditService;
        this.paymentAccountRepository = paymentAccountRepository;
        this.orderStats = orderStats;
        this.jwtService = jwtService;
        this.authService = authService;
    }

    // ── Elenco / dettaglio ────────────────────────────────────────────────

    public List<AgencySummary> listAgencies(String q, String status) {
        SubscriptionStatus statusFilter = parseStatusFilter(status);
        List<AgencyJpa> agencies = agencyRepository.findAll();
        List<AgencySummary> all = summaries(agencies, null);
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        return all.stream()
                .filter(s -> statusFilter == null || statusFilter.name().equals(s.subscriptionStatus()))
                .filter(s -> needle.isEmpty() || matches(s, needle))
                .sorted(Comparator.comparing(AgencySummary::deleted).thenComparing(AgencySummary::id, Comparator.reverseOrder()))
                .toList();
    }

    public AgencyDetail getAgency(long idAgency) {
        AgencyJpa agency = findAgency(idAgency);
        AgencySummary summary = summaries(List.of(agency), List.of(idAgency)).get(0);
        List<Note> notes = noteRepository.findAllByIdAgencyOrderByPinnedDescCreatedAtDescIdDesc(idAgency).stream()
                .map(SuperadminService::toNote).toList();
        List<AuditEntry> audit = toAuditEntries(auditRepository.findByIdAgencyOrderByCreatedAtDescIdDesc(
                idAgency, PageRequest.of(0, AGENCY_AUDIT_LIMIT)));
        List<UserRow> users = userRepository.findAllByIdAgencyOrderByCreatedAtAscIdAsc(idAgency).stream()
                .map(u -> new UserRow(u.getId(), u.getUsername(), u.getEmail(), u.getRole(), u.getName(), u.getSurname(),
                        u.isEmailConfirmed() && (!ROLE_WAITER.equals(u.getRole()) || u.isGeneralConfirmed()),
                        u.isDeleted()))
                .toList();
        return new AgencyDetail(summary, notes, audit, users);
    }

    public Stats stats() {
        List<AgencyJpa> agencies = agencyRepository.findAll().stream().filter(a -> !a.isDeleted()).toList();
        Map<Long, AgencyAdminInfoJpa> infos = adminInfos();
        OffsetDateTime now = OffsetDateTime.now();
        long active = 0, trial = 0, suspended = 0, cancelled = 0, blocked = 0;
        for (AgencyJpa a : agencies) {
            AgencyAdminInfoJpa info = infos.get(a.getId());
            switch (SubscriptionRules.effectiveStatus(a, info)) {
                case ACTIVE -> active++;
                case TRIAL -> trial++;
                case SUSPENDED -> suspended++;
                case CANCELLED -> cancelled++;
            }
            if (SubscriptionRules.isBlocked(a, info, now)) blocked++;
        }
        return new Stats(agencies.size(), active, trial, suspended, cancelled, blocked,
                orderStats.ordersToday(), orderStats.ordersLast30Days());
    }

    // ── Abbonamento ───────────────────────────────────────────────────────

    @Transactional
    public AgencySummary updateSubscription(long idAgency, SubscriptionUpdate body, Actor actor) {
        if (body == null) throw badRequest("Dati mancanti");
        SubscriptionStatus status = parseStatus(body.getSubscriptionStatus());
        if (body.getTrial() == null) throw badRequest("trial obbligatorio");
        LocalDate activeFrom = parseLocalDate(body.getActiveFrom(), "activeFrom");
        OffsetDateTime trialEndAt = parseDateTime(body.getTrialEndAt(), "trialEndAt");
        OffsetDateTime billingEndAt = parseDateTime(body.getBillingEndAt(), "billingEndAt");
        String subscriptionNumber = cleanText(body.getSubscriptionNumber(), "subscriptionNumber");
        String plan = cleanText(body.getPlan(), "plan");

        AgencyJpa agency = findAgency(idAgency);
        AgencyAdminInfoJpa existing = adminInfoRepository.findById(idAgency).orElse(null);
        AgencyAdminInfoJpa info = existing != null ? existing : new AgencyAdminInfoJpa(idAgency);

        List<String> diff = new ArrayList<>();
        diffField(diff, "status", SubscriptionRules.effectiveStatus(agency, existing), status);
        diffField(diff, "trial", agency.isTrial(), body.getTrial());
        diffField(diff, "trialEndAt", iso(agency.getTrialEndAt()), iso(trialEndAt));
        diffField(diff, "billingEndAt", iso(agency.getBillingEndAt()), iso(billingEndAt));
        diffField(diff, "activeFrom", info.getActiveFrom(), activeFrom);
        diffField(diff, "subscriptionNumber", info.getSubscriptionNumber(), subscriptionNumber);
        diffField(diff, "plan", info.getPlan(), plan);

        agency.setTrial(body.getTrial());
        agency.setTrialEndAt(trialEndAt);
        agency.setBillingEndAt(billingEndAt);
        agencyRepository.save(agency);

        info.setSubscriptionStatus(status);
        info.setActiveFrom(activeFrom);
        info.setSubscriptionNumber(subscriptionNumber);
        info.setPlan(plan);
        info.setUpdatedAt(Instant.now());
        info.setUpdatedBy(actor.id());
        adminInfoRepository.save(info);

        auditService.record(actor.id(), actor.email(), idAgency, SuperadminAction.SUBSCRIPTION_UPDATE,
                diff.isEmpty() ? "nessuna modifica" : String.join("; ", diff), actor.ip());
        ErrorLog.logger.info("Superadmin {}: abbonamento locale {} aggiornato", actor.id(), idAgency);
        return summaries(List.of(agency), List.of(idAgency)).get(0);
    }

    // ── Note interne ──────────────────────────────────────────────────────

    @Transactional
    public Note addNote(long idAgency, NoteCreate body, Actor actor) {
        String text = body == null || body.text() == null ? "" : body.text().trim();
        if (text.isEmpty()) throw badRequest("Testo della nota obbligatorio");
        if (text.length() > AgencyNoteJpa.MAX_TEXT) throw badRequest("Nota troppo lunga (max " + AgencyNoteJpa.MAX_TEXT + " caratteri)");
        findAgency(idAgency);
        User author = userRepository.findByIdAndDeleted(actor.id(), false).orElse(null);
        AgencyNoteJpa note = new AgencyNoteJpa();
        note.setIdAgency(idAgency);
        note.setText(text);
        note.setPinned(body.pinned() != null && body.pinned());
        note.setAuthorId(actor.id());
        note.setAuthorEmail(actor.email());
        note.setAuthorName(displayName(author, actor.email()));
        note.setCreatedAt(Instant.now());
        note = noteRepository.save(note);
        auditService.record(actor.id(), actor.email(), idAgency, SuperadminAction.NOTE_ADD,
                "nota " + note.getId() + ": " + abbreviate(text, 200), actor.ip());
        return toNote(note);
    }

    @Transactional
    public Note setPinned(long noteId, NotePatch body) {
        if (body == null || body.pinned() == null) throw badRequest("pinned obbligatorio");
        AgencyNoteJpa note = noteRepository.findById(noteId).orElseThrow(() -> notFound("Nota non trovata"));
        note.setPinned(body.pinned());
        return toNote(noteRepository.save(note));
    }

    @Transactional
    public void deleteNote(long noteId, Actor actor) {
        AgencyNoteJpa note = noteRepository.findById(noteId).orElseThrow(() -> notFound("Nota non trovata"));
        noteRepository.delete(note);
        auditService.record(actor.id(), actor.email(), note.getIdAgency(), SuperadminAction.NOTE_DELETE,
                "nota " + note.getId() + ": " + abbreviate(note.getText(), 200), actor.ip());
    }

    // ── Impersonazione ────────────────────────────────────────────────────

    /**
     * Token per l'admin principale (il più vecchio non eliminato) del locale, valido 60 minuti e mai rinnovato.
     * Funziona anche se il locale è bloccato (abbonamento scaduto/sospeso), per poterlo aiutare.
     */
    @Transactional
    public ImpersonationResponse impersonate(long idAgency, ImpersonateRequest body, Actor actor) {
        String reason = body == null || body.reason() == null ? "" : body.reason().trim();
        if (reason.length() < REASON_MIN || reason.length() > REASON_MAX) {
            throw badRequest("Motivo obbligatorio (" + REASON_MIN + "-" + REASON_MAX + " caratteri)");
        }
        AgencyJpa agency = findAgency(idAgency);
        if (agency.isDeleted()) throw badRequest("Locale eliminato");
        User admin = userRepository.findFirstByIdAgencyAndRoleAndDeletedOrderByCreatedAtAscIdAsc(idAgency, ROLE_ADMIN, false)
                .orElseThrow(() -> notFound("Nessun amministratore attivo per questo locale"));

        UserDto adminDto = AuthService.toDto(admin);
        String token = jwtService.generateImpersonationToken(adminDto, actor.id(), actor.email());
        Date expiresAt = jwtService.extractExpiration(token);
        AuthResponse auth = authService.buildImpersonationAuth(admin, agency, token, actor.email());

        auditService.record(actor.id(), actor.email(), idAgency, SuperadminAction.IMPERSONATE_START,
                "utente " + admin.getId() + " (" + admin.getUsername() + "); motivo: " + reason, actor.ip());
        ErrorLog.logger.info("Superadmin {} impersona l'utente {} del locale {}", actor.id(), admin.getId(), idAgency);
        return new ImpersonationResponse(token, expiresAt.toInstant().toString(),
                new AgencyRef(agency.getId(), agency.getName(), admin.getUsername()), auth);
    }

    // ── Audit ─────────────────────────────────────────────────────────────

    public List<AuditEntry> audit(Long agencyId, Integer limit) {
        int size = limit == null || limit <= 0 ? AUDIT_DEFAULT_LIMIT : Math.min(limit, AUDIT_MAX_LIMIT);
        PageRequest page = PageRequest.of(0, size);
        return toAuditEntries(agencyId != null
                ? auditRepository.findByIdAgencyOrderByCreatedAtDescIdDesc(agencyId, page)
                : auditRepository.findAllByOrderByCreatedAtDescIdDesc(page));
    }

    // ── interni ───────────────────────────────────────────────────────────

    /** Costruisce i riepiloghi con poche query aggregate (non una per locale). {@code orderScope} null = tutti. */
    private List<AgencySummary> summaries(List<AgencyJpa> agencies, Collection<Long> orderScope) {
        Map<Long, AgencyAdminInfoJpa> infos = adminInfos();
        Map<Long, User> admins = new HashMap<>();
        for (User u : userRepository.findAllByRoleAndDeletedOrderByCreatedAtAscIdAsc(ROLE_ADMIN, false)) {
            if (u.getIdAgency() != null) admins.putIfAbsent(u.getIdAgency(), u);
        }
        Map<Long, PaymentProvider> providers = paymentAccountRepository.findAll().stream()
                .filter(p -> p.getIdAgency() != null)
                .collect(Collectors.toMap(AgencyPaymentAccountJpa::getIdAgency,
                        p -> p.getActiveProvider() != null ? p.getActiveProvider() : PaymentProvider.NONE, (a, b) -> a));
        Map<Long, Long> tables = countMap(adminInfoRepository.countTablesByAgency());
        Map<Long, Long> waiters = countMap(adminInfoRepository.countUsersByAgencyAndRole(ROLE_WAITER));
        Map<Long, Long> notes = countMap(noteRepository.countByAgency());
        Map<Long, OrderStatsProvider.AgencyOrderStats> orders = orderStats.statsByAgency(orderScope);
        OffsetDateTime now = OffsetDateTime.now();

        List<AgencySummary> out = new ArrayList<>(agencies.size());
        for (AgencyJpa a : agencies) {
            long id = a.getId();
            AgencyAdminInfoJpa info = infos.get(id);
            User admin = admins.get(id);
            OrderStatsProvider.AgencyOrderStats os = orders.get(id);
            String phone = a.getPhone() != null && !a.getPhone().isBlank() ? a.getPhone()
                    : admin != null ? admin.getPhoneNumber() : null;
            out.add(new AgencySummary(
                    id,
                    a.getName(),
                    admin != null ? admin.getUsername() : null,
                    admin != null ? admin.getEmail() : null,
                    admin != null ? joinName(admin.getName(), admin.getSurname()) : null,
                    phone,
                    iso(a.getCreatedAt()),
                    info != null && info.getActiveFrom() != null ? info.getActiveFrom().toString() : null,
                    info != null ? info.getSubscriptionNumber() : null,
                    info != null ? info.getPlan() : null,
                    SubscriptionRules.effectiveStatus(a, info).name(),
                    a.isTrial(),
                    iso(a.getTrialEndAt()),
                    iso(a.getBillingEndAt()),
                    SubscriptionRules.isBlocked(a, info, now),
                    providers.getOrDefault(id, PaymentProvider.NONE).name(),
                    tables.getOrDefault(id, 0L),
                    waiters.getOrDefault(id, 0L),
                    os != null && os.lastOrderAt() != null ? os.lastOrderAt().toInstant().toString() : null,
                    os != null ? os.ordersLast30Days() : 0,
                    notes.getOrDefault(id, 0L),
                    a.isDeleted()
            ));
        }
        return out;
    }

    private Map<Long, AgencyAdminInfoJpa> adminInfos() {
        return adminInfoRepository.findAll().stream()
                .collect(Collectors.toMap(AgencyAdminInfoJpa::getIdAgency, Function.identity(), (a, b) -> a));
    }

    private List<AuditEntry> toAuditEntries(List<SuperadminAuditJpa> rows) {
        Set<Long> ids = rows.stream().map(SuperadminAuditJpa::getIdAgency).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> names = ids.isEmpty() ? Map.of() : agencyRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(AgencyJpa::getId, a -> a.getName() != null ? a.getName() : "", (a, b) -> a));
        return rows.stream().map(r -> new AuditEntry(
                r.getId() != null ? r.getId() : 0, r.getSuperadminEmail(), r.getIdAgency(),
                r.getIdAgency() != null ? names.get(r.getIdAgency()) : null,
                r.getAction() != null ? r.getAction().name() : null, r.getDetail(), r.getIp(),
                r.getCreatedAt() != null ? r.getCreatedAt().toString() : null)).toList();
    }

    private static Map<Long, Long> countMap(List<Object[]> rows) {
        Map<Long, Long> m = new HashMap<>();
        if (rows == null) return m;
        for (Object[] r : rows) {
            if (r != null && r.length >= 2 && r[0] instanceof Number k && r[1] instanceof Number v) {
                m.put(k.longValue(), v.longValue());
            }
        }
        return m;
    }

    private static boolean matches(AgencySummary s, String needle) {
        return contains(s.name(), needle) || contains(s.localname(), needle)
                || contains(s.adminEmail(), needle) || contains(s.subscriptionNumber(), needle);
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private AgencyJpa findAgency(long idAgency) {
        return agencyRepository.findById(idAgency).orElseThrow(() -> notFound("Locale non trovato"));
    }

    static Note toNote(AgencyNoteJpa n) {
        return new Note(n.getId() != null ? n.getId() : 0, n.getText(), n.getAuthorName(), n.getAuthorEmail(),
                n.getCreatedAt() != null ? n.getCreatedAt().toString() : null, n.isPinned());
    }

    private static String displayName(User u, String fallback) {
        if (u == null) return fallback;
        String n = joinName(u.getName(), u.getSurname());
        return n == null || n.isBlank() ? fallback : n;
    }

    private static String joinName(String name, String surname) {
        String n = ((name != null ? name : "") + " " + (surname != null ? surname : "")).trim();
        return n.isEmpty() ? null : n;
    }

    private static String iso(OffsetDateTime t) {
        return t != null ? t.toString() : null;
    }

    private static void diffField(List<String> diff, String name, Object before, Object after) {
        if (!Objects.equals(before, after)) diff.add(name + ": " + before + " -> " + after);
    }

    private static String abbreviate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max) + "...";
    }

    static SubscriptionStatus parseStatus(String value) {
        if (value == null || value.isBlank()) throw badRequest("subscriptionStatus obbligatorio");
        try {
            return SubscriptionStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw badRequest("subscriptionStatus non valido: usa TRIAL, ACTIVE, SUSPENDED o CANCELLED");
        }
    }

    private static SubscriptionStatus parseStatusFilter(String value) {
        return value == null || value.isBlank() ? null : parseStatus(value);
    }

    static LocalDate parseLocalDate(String value, String field) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw badRequest(field + " non valido (formato YYYY-MM-DD)");
        }
    }

    /** Fuso usato per le date senza offset inviate dal pannello. */
    static final ZoneId PANEL_ZONE = ZoneId.of("Europe/Rome");

    /**
     * "YYYY-MM-DD" = fine di quel giorno (23:59:59, Europe/Rome); ISO con offset ("...Z", "...+02:00");
     * ISO senza offset = ora locale Europe/Rome.
     */
    static OffsetDateTime parseDateTime(String value, String field) {
        if (value == null || value.isBlank()) return null;
        String v = value.trim();
        try {
            return LocalDate.parse(v).atTime(23, 59, 59).atZone(PANEL_ZONE).toOffsetDateTime();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return OffsetDateTime.parse(v);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(v).atZone(PANEL_ZONE).toOffsetDateTime();
        } catch (DateTimeParseException e) {
            throw badRequest(field + " non valido (YYYY-MM-DD o ISO-8601)");
        }
    }

    static String cleanText(String value, String field) {
        if (value == null) return null;
        String v = value.trim();
        if (v.isEmpty()) return null;
        if (v.length() > FIELD_MAX) throw badRequest(field + " troppo lungo (max " + FIELD_MAX + " caratteri)");
        return v;
    }

    private static ResponseStatusException badRequest(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }

    private static ResponseStatusException notFound(String msg) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, msg);
    }
}
