package com.modules.cardmodule.service;

import com.modules.authmodule.model.AgencyJpa;
import com.modules.authmodule.repository.AgencyRepository;
import com.modules.cardmodule.models.CardJpa;
import com.modules.cardmodule.models.CardMovement;
import com.modules.cardmodule.models.LoyaltySettingsJpa;
import com.modules.cardmodule.repository.CardClaimRepository;
import com.modules.cardmodule.repository.CardMovementRepository;
import com.modules.cardmodule.repository.CardRepository;
import com.modules.cardmodule.repository.LoyaltySettingsRepository;
import com.modules.cardmodule.requests.AddCard;
import com.modules.cardmodule.requests.CardDetailsRequest;
import com.modules.cardmodule.requests.CardMovementDto;
import com.modules.cardmodule.requests.LoyaltySettingsDto;
import com.modules.common.dto.CardDto;
import com.modules.common.email.EmailService;
import com.modules.common.email.LanguageEmail;
import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.common.utilities.Utilities;
import com.modules.servletconfiguration.security.AuthenticatedUserProvider;
import jakarta.transaction.Transactional;
import com.modules.common.finders.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.thymeleaf.context.Context;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class CardService {
    /** Giorno "di calendario" del locale: i container girano in UTC. */
    static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Rome");

    @Autowired
    private CardRepository cardRepository;
    @Autowired
    private AuthenticatedUserProvider authUserProvider;
    @Autowired
    private Utilities utilities;
    @Autowired
    private FileUtils fileUtils;
    @Autowired
    private EmailService emailService;
    @Autowired
    private CardClaimRepository cardClaimRepository;
    @Autowired
    private CardMovementRepository cardMovementRepository;
    @Autowired
    private AgencyRepository agencyRepository;
    @Autowired
    private LoyaltySettingsRepository loyaltySettingsRepository;

    // ── Impostazioni del locale ──────────────────────────────────────────────

    public LoyaltySettingsDto getSettings() {
        return LoyaltySettingsDto.of(settings(authUserProvider.getAgencyId()));
    }

    @Transactional
    public LoyaltySettingsDto updateSettings(LoyaltySettingsDto dto) {
        long idAgency = authUserProvider.getAgencyId();
        LoyaltySettingsJpa s = loyaltySettingsRepository.findById(idAgency).orElseGet(() -> new LoyaltySettingsJpa(idAgency));
        String newPolicy = dto.expiryPolicy() == null ? "NONE" : dto.expiryPolicy();
        boolean yearlyDateChanged = "YEARLY".equals(newPolicy)
                && (!Objects.equals(dto.expiryMonth(), s.getExpiryMonth()) || !Objects.equals(dto.expiryDay(), s.getExpiryDay()));
        if (!newPolicy.equals(policy(s)) || yearlyDateChanged) startExpiryPeriod(idAgency, newPolicy);
        s.setEurosPerPoint(dto.eurosPerPoint());
        s.setPointValue(dto.pointValue());
        s.setStampsForPrize(dto.stampsForPrize());
        s.setStampsPrize(dto.stampsPrize() == null || dto.stampsPrize().isBlank() ? null : dto.stampsPrize().trim());
        s.setProgramType(dto.programType());
        s.setExpiryPolicy(dto.expiryPolicy());
        boolean yearly = "YEARLY".equals(dto.expiryPolicy());
        s.setExpiryMonth(yearly ? dto.expiryMonth() : null);
        s.setExpiryDay(yearly ? dto.expiryDay() : null);
        s.setInactivityMonths("INACTIVITY".equals(dto.expiryPolicy()) ? dto.inactivityMonths() : null);
        s.setMinSpendForVisit(dto.minSpendForVisit() != null && dto.minSpendForVisit() > 0 ? dto.minSpendForVisit() : null);
        s.setOneVisitPerDay(Boolean.TRUE.equals(dto.oneVisitPerDay()) ? Boolean.TRUE : null);
        s.setUpdatedAt(OffsetDateTime.now());
        ErrorLog.logger.info("Impostazioni tessere aggiornate (agency {})", idAgency);
        return LoyaltySettingsDto.of(loyaltySettingsRepository.save(s));
    }

    /**
     * Attivando una scadenza i saldi esistenti non devono sparire subito: il conteggio parte da adesso
     * (azzeramento annuale alla prossima data, inattività contata da oggi per le tessere mai usate).
     */
    private void startExpiryPeriod(long idAgency, String newPolicy) {
        if ("NONE".equals(newPolicy)) return;
        OffsetDateTime now = OffsetDateTime.now();
        List<CardJpa> cards = cardRepository.findAllByIdAgencyAndDeleted(idAgency, false);
        for (CardJpa c : cards) {
            if ("YEARLY".equals(newPolicy)) c.setBalanceSince(now);
            else if (c.getLastActivityAt() == null) c.setLastActivityAt(now);
        }
        cardRepository.saveAll(cards);
    }

    private LoyaltySettingsJpa settings(long idAgency) {
        return loyaltySettingsRepository.findById(idAgency).orElse(null);
    }

    /** Timbri/visite per il premio: impostazione del locale, altrimenti quella salvata sulla tessera. */
    private static int effectiveScope(CardJpa card, LoyaltySettingsJpa s) {
        if (!card.isTypePoints() && s != null && s.getStampsForPrize() != null) return s.getStampsForPrize();
        return card.getScope();
    }

    /** € per 1 punto: impostazione del locale, altrimenti quella salvata sulla tessera. */
    private static double effectiveEurosPerPoint(CardJpa card, LoyaltySettingsJpa s) {
        if (card.isTypePoints() && s != null && s.getEurosPerPoint() != null) return s.getEurosPerPoint();
        return card.getPriceForPoint();
    }

    /** DTO con le regole effettive (quelle del locale prevalgono sui valori storici della tessera). */
    private CardDto toDto(CardJpa card, LoyaltySettingsJpa s) {
        CardDto dto = new CardDto(card);
        dto.setScope(effectiveScope(card, s));
        dto.setPriceForPoint(effectiveEurosPerPoint(card, s));
        dto.setHolderName(card.getHolderName());
        dto.setHolderContact(card.getHolderContact());
        dto.setNote(card.getNote());
        dto.setVisitsCount(card.getVisitsCount() == null ? 0 : card.getVisitsCount());
        dto.setTotalSpentCents(card.getTotalSpentCents() == null ? 0 : card.getTotalSpentCents());
        dto.setLastVisitAt(card.getLastVisitAt() == null ? null : card.getLastVisitAt().toString());
        OffsetDateTime expires = card.getActualValue() > 0 ? nextExpiry(card, s, OffsetDateTime.now()) : null;
        dto.setExpiresAt(expires == null ? null : expires.toString());
        return dto;
    }

    // ── Scadenze ─────────────────────────────────────────────────────────────

    private static String policy(LoyaltySettingsJpa s) {
        return s == null || s.getExpiryPolicy() == null ? "NONE" : s.getExpiryPolicy();
    }

    /** Ultima data di azzeramento annuale (inizio giornata, ora italiana) non successiva a {@code now}. */
    static OffsetDateTime lastYearlyBoundary(int month, int day, OffsetDateTime now) {
        ZonedDateTime local = now.atZoneSameInstant(BUSINESS_ZONE);
        ZonedDateTime b = yearlyBoundary(local.getYear(), month, day);
        if (b.isAfter(local)) b = yearlyBoundary(local.getYear() - 1, month, day);
        return b.toOffsetDateTime();
    }

    private static ZonedDateTime yearlyBoundary(int year, int month, int day) {
        int d = Math.min(day, YearMonth.of(year, month).lengthOfMonth()); // 29/2 negli anni non bisestili → 28/2
        return LocalDate.of(year, month, d).atStartOfDay(BUSINESS_ZONE);
    }

    private static OffsetDateTime balanceSince(CardJpa card) {
        return card.getBalanceSince() != null ? card.getBalanceSince() : card.getCreatedAt();
    }

    private static OffsetDateTime lastActivity(CardJpa card) {
        return card.getLastActivityAt() != null ? card.getLastActivityAt() : card.getCreatedAt();
    }

    /** Quando verrà azzerato il saldo attuale, o null se non scade. */
    static OffsetDateTime nextExpiry(CardJpa card, LoyaltySettingsJpa s, OffsetDateTime now) {
        switch (policy(s)) {
            case "YEARLY": {
                OffsetDateTime last = lastYearlyBoundary(s.getExpiryMonth(), s.getExpiryDay(), now);
                int nextYear = last.atZoneSameInstant(BUSINESS_ZONE).getYear() + 1;
                return yearlyBoundary(nextYear, s.getExpiryMonth(), s.getExpiryDay()).toOffsetDateTime();
            }
            case "INACTIVITY":
                return lastActivity(card).plusMonths(s.getInactivityMonths());
            default:
                return null;
        }
    }

    /**
     * Applica le regole di scadenza del locale (in modo "pigro": quando la tessera viene letta o usata).
     * @return true se la tessera è stata modificata e va salvata
     */
    boolean applyExpiry(CardJpa card, LoyaltySettingsJpa s, OffsetDateTime now) {
        switch (policy(s)) {
            case "YEARLY": {
                OffsetDateTime boundary = lastYearlyBoundary(s.getExpiryMonth(), s.getExpiryDay(), now);
                if (!balanceSince(card).isBefore(boundary)) return false;
                // Anche a saldo zero si sposta l'inizio periodo: i punti presi dopo la data non devono scadere
                expire(card, now, "Azzeramento annuale");
                return true;
            }
            case "INACTIVITY": {
                if (card.getActualValue() <= 0) return false;
                if (!lastActivity(card).plusMonths(s.getInactivityMonths()).isBefore(now)) return false;
                expire(card, now, "Scadenza per " + s.getInactivityMonths() + " mesi senza movimenti");
                return true;
            }
            default:
                return false;
        }
    }

    private void expire(CardJpa card, OffsetDateTime now, String note) {
        int old = card.getActualValue();
        card.setActualValue(0);
        card.setBalanceSince(now);
        if (old > 0) {
            cardMovementRepository.save(new CardMovement(card, null, CardMovement.Type.EXPIRE, -old, null, null, note));
            ErrorLog.logger.info("Tessera {} azzerata ({}): {} → 0", card.getId(), note, old);
        }
    }

    /** Tessera del locale con le scadenze già applicate. */
    private CardJpa load(long id, long idAgency, LoyaltySettingsJpa s) {
        CardJpa card = cardRepository.findByIdAndDeletedAndIdAgency(id, false, idAgency)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Card non trovata"));
        if (applyExpiry(card, s, OffsetDateTime.now())) card = cardRepository.save(card);
        return card;
    }

    private void touch(CardJpa card, OffsetDateTime now) {
        card.setLastActivityAt(now);
    }

    // ── Movimenti alla chiusura del conto ────────────────────────────────────

    /** {@code note}: perché non è stato accreditato nulla (null se tutto regolare). */
    public record CheckoutLoyalty(int claimed, int earned, CardDto card, String note) {}

    /**
     * Movimenti tessera alla chiusura di un conto: prima scala (punti usati / premio), poi accredita.
     * Punti: calcolati qui sul totale pagato con le regole del locale.
     * Visite: +1 se il conto rispetta spesa minima e "una visita al giorno".
     */
    @Transactional
    public CheckoutLoyalty applyCheckout(long cardId, long idAgency, long idUser, long totalCents, String comandId,
                                         int pointsToUse, boolean redeemStamps, boolean earn) {
        LoyaltySettingsJpa s = settings(idAgency);
        CardJpa card = load(cardId, idAgency, s);
        OffsetDateTime now = OffsetDateTime.now();
        int scope = effectiveScope(card, s);

        int claim = card.isTypePoints() ? Math.max(0, pointsToUse)
                : (redeemStamps && card.getActualValue() >= scope ? scope : 0);
        claim = Math.min(claim, card.getActualValue());
        if (claim > 0) {
            card.setActualValue(card.getActualValue() - claim);
            cardMovementRepository.save(new CardMovement(card, idUser, CardMovement.Type.CLAIM, -claim, null, comandId,
                    card.isTypePoints() ? null : "Premio riscattato"));
        }

        // Una visita al giorno: si confronta con la visita precedente, prima di aggiornarla
        boolean sameDay = card.getLastVisitAt() != null && card.getLastVisitAt().atZoneSameInstant(BUSINESS_ZONE).toLocalDate()
                .equals(now.atZoneSameInstant(BUSINESS_ZONE).toLocalDate());

        int earned = 0;
        String note = null;
        if (earn) {
            if (card.isTypePoints()) {
                double eurosPerPoint = effectiveEurosPerPoint(card, s);
                earned = eurosPerPoint > 0 ? (int) Math.floor(totalCents / (eurosPerPoint * 100) + 1e-9) : 0;
                if (earned == 0) note = "Importo troppo basso per accreditare punti";
            } else {
                long minCents = s != null && s.getMinSpendForVisit() != null ? Math.round(s.getMinSpendForVisit() * 100) : 0;
                if (totalCents < minCents) {
                    note = String.format(Locale.ITALY, "Visita non conteggiata: spesa minima %.2f €", minCents / 100.0);
                } else if (sameDay && s != null && Boolean.TRUE.equals(s.getOneVisitPerDay())) {
                    note = "Visita già conteggiata oggi";
                } else if (card.getActualValue() >= scope) {
                    note = "Tessera già completa: riscatta il premio";
                } else {
                    earned = 1;
                }
            }
            if (earned > 0) {
                card.setActualValue(card.getActualValue() + earned);
                cardMovementRepository.save(new CardMovement(card, idUser, CardMovement.Type.EARN, earned, totalCents, comandId, null));
            }
        }

        card.setVisitsCount((card.getVisitsCount() == null ? 0 : card.getVisitsCount()) + 1);
        card.setTotalSpentCents((card.getTotalSpentCents() == null ? 0 : card.getTotalSpentCents()) + totalCents);
        card.setLastVisitAt(now);
        touch(card, now);
        card = cardRepository.save(card);
        return new CheckoutLoyalty(claim, earned, toDto(card, s), note);
    }

    // ── Operazioni manuali ───────────────────────────────────────────────────

    @Transactional
    public CardDto claimPoints(long id, int quantity){
        long idAgency = authUserProvider.getAgencyId();
        long idUser = authUserProvider.getUserId();
        LoyaltySettingsJpa s = settings(idAgency);
        CardJpa cardJpa = load(id, idAgency, s);

        // Lo storico registra quanto è stato davvero scalato, non quanto richiesto
        quantity = Math.min(quantity, cardJpa.getActualValue());
        if (quantity > 0) {
            cardJpa.setActualValue(cardJpa.getActualValue() - quantity);
            cardMovementRepository.save(new CardMovement(cardJpa, idUser, CardMovement.Type.CLAIM, -quantity, null, null, null));
            touch(cardJpa, OffsetDateTime.now());
            cardJpa = cardRepository.save(cardJpa);
        }
        return toDto(cardJpa, s);
    }

    @Transactional
    public CardDto addPoints(long id, int quantity) {
        long idAgency = authUserProvider.getAgencyId();
        long idUser = authUserProvider.getUserId();
        LoyaltySettingsJpa s = settings(idAgency);
        CardJpa cardJpa = load(id, idAgency, s);
        int value = cardJpa.getActualValue() + quantity;
        int scope = effectiveScope(cardJpa, s);
        if(!cardJpa.isTypePoints() && value > scope){
            value = scope;
        }
        int delta = value - cardJpa.getActualValue();
        if (delta > 0) {
            cardJpa.setActualValue(value);
            cardMovementRepository.save(new CardMovement(cardJpa, idUser, CardMovement.Type.ADD, delta, null, null, null));
            touch(cardJpa, OffsetDateTime.now());
            cardJpa = cardRepository.save(cardJpa);
        }
        return toDto(cardJpa, s);
    }

    @Transactional
    public CardDto resetPoints(long id){
        long idAgency = authUserProvider.getAgencyId();
        long idUser = authUserProvider.getUserId();
        LoyaltySettingsJpa s = settings(idAgency);
        CardJpa cardJpa = load(id, idAgency, s);
        reset(cardJpa, idUser, null);
        return toDto(cardRepository.save(cardJpa), s);
    }

    /** Azzera il saldo di tutte le tessere del locale (es. chiusura della raccolta punti annuale). */
    @Transactional
    public int resetAll() {
        long idAgency = authUserProvider.getAgencyId();
        long idUser = authUserProvider.getUserId();
        List<CardJpa> cards = cardRepository.findAllByIdAgencyAndDeleted(idAgency, false);
        int count = 0;
        for (CardJpa c : cards) {
            if (c.getActualValue() > 0) count++;
            reset(c, idUser, "Azzeramento di tutte le tessere");
        }
        cardRepository.saveAll(cards);
        ErrorLog.logger.info("Azzerate {} tessere da utente {} (agency {})", count, idUser, idAgency);
        return count;
    }

    private void reset(CardJpa card, long idUser, String note) {
        int old = card.getActualValue();
        OffsetDateTime now = OffsetDateTime.now();
        card.setActualValue(0);
        card.setBalanceSince(now);
        if (old > 0) {
            cardMovementRepository.save(new CardMovement(card, idUser, CardMovement.Type.RESET, -old, null, null, note));
            touch(card, now);
        }
    }

    @Transactional
    public CardDto updateDetails(long id, CardDetailsRequest req) {
        long idAgency = authUserProvider.getAgencyId();
        LoyaltySettingsJpa s = settings(idAgency);
        CardJpa card = load(id, idAgency, s);
        card.setHolderName(blankToNull(req.holderName()));
        card.setHolderContact(blankToNull(req.holderContact()));
        card.setNote(blankToNull(req.note()));
        return toDto(cardRepository.save(card), s);
    }

    /** Storico della tessera, dal più recente (comprende gli utilizzi registrati col vecchio sistema). */
    public List<CardMovementDto> getMovements(long id) {
        long idAgency = authUserProvider.getAgencyId();
        cardRepository.findByIdAndDeletedAndIdAgency(id, false, idAgency)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Card non trovata"));
        return Stream.concat(
                        cardMovementRepository.findTop200ByIdCardAndIdAgencyOrderByCreatedAtDesc(id, idAgency).stream().map(CardMovementDto::of),
                        cardClaimRepository.findAllByIdCardAndIdAgency(id, idAgency).stream().map(CardMovementDto::ofLegacy))
                .sorted(Comparator.comparing(CardMovementDto::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(200)
                .collect(Collectors.toList());
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    @Transactional
    public CardDto getCardInfo(String code){
        // Scoped all'agency del chiamante: una card di un altro locale risulta inesistente.
        long idAgency = authUserProvider.getAgencyId();
        CardJpa card = cardRepository.findByCodeAndDeletedAndIdAgency(code, false, idAgency)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Card non trovata"));
        LoyaltySettingsJpa s = settings(idAgency);
        if (applyExpiry(card, s, OffsetDateTime.now())) card = cardRepository.save(card);
        return toDto(card, s);
    }

    /** Saldo visibile al cliente: solo i dati della tessera, niente titolare, note o spesa. */
    public record PublicCardStatus(String code, boolean typePoints, int actualValue, int scope, double priceForPoint,
                                   String createdAt, String expiresAt, String prize, String localName) {}

    @Transactional
    public Optional<PublicCardStatus> getPublicStatus(String code) {
        return cardRepository.findByCodeAndDeleted(code, false).map(card -> {
            LoyaltySettingsJpa s = settings(card.getIdAgency());
            if (applyExpiry(card, s, OffsetDateTime.now())) card = cardRepository.save(card);
            CardDto dto = toDto(card, s);
            String localName = agencyRepository.findByIdAndDeleted(card.getIdAgency(), false).map(AgencyJpa::getName).orElse(null);
            return new PublicCardStatus(dto.getCode(), dto.isTypePoints(), dto.getActualValue(), dto.getScope(),
                    dto.getPriceForPoint(), dto.getCreatedAt(), dto.getExpiresAt(),
                    card.isTypePoints() || s == null ? null : s.getStampsPrize(), localName);
        });
    }

    @Transactional
    public CardDto addCard(AddCard addCard) throws Exception{
        long idAgency = authUserProvider.getAgencyId();
        long idUser = authUserProvider.getUserId();
        String cardCode = "";
        boolean find = true;
        int count = 10;
        while (find) {
            cardCode = count > 0 ? UUID.randomUUID().toString().substring(0, 13) : UUID.randomUUID().toString().substring(0, 18);
            cardCode = cardCode.toUpperCase();
            find = cardRepository.existsByCode(cardCode);
            count--;
        }

        MultipartFile multipartFile = utilities.generateQRCodeAsMultipartFile(cardCode, 300, 300, "webp");

        if(multipartFile == null) {
            throw new Exception("Errore creazione qrcode per card. multipartfile e' null");
        }

        String url = fileUtils.uploadImageWithBucket(multipartFile, idAgency, idUser, "cards");

        // Le regole del locale (se impostate) prevalgono su quelle inviate dal client
        LoyaltySettingsJpa s = settings(idAgency);
        boolean typePoints = s != null && s.getProgramType() != null ? "POINTS".equals(s.getProgramType()) : addCard.isTypePoints();
        int scope = !typePoints && s != null && s.getStampsForPrize() != null ? s.getStampsForPrize()
                : addCard.getScope() > 0 ? addCard.getScope() : 10;
        double priceForPoint = !typePoints ? 0
                : s != null && s.getEurosPerPoint() != null ? s.getEurosPerPoint()
                : addCard.getPriceForPoint() != null ? addCard.getPriceForPoint() : 1;
        OffsetDateTime now = OffsetDateTime.now();
        CardJpa card = new CardJpa(cardCode, typePoints, 0, scope, priceForPoint, url, idAgency, now);
        card.setBalanceSince(now);
        card.setHolderName(blankToNull(addCard.getHolderName()));
        card.setHolderContact(blankToNull(addCard.getHolderContact()));
        card.setNote(blankToNull(addCard.getNote()));
        card = cardRepository.save(card);
        ErrorLog.logger.info("Card {} created for agency {}", card.getId(), idAgency);
        return toDto(card, s);
    }

    public boolean sendCardByEmail(String email, long idCard) {
        long idAgency = authUserProvider.getAgencyId();
        CardJpa card = cardRepository.findByIdAndDeletedAndIdAgency(idCard, false, idAgency).orElseThrow();
        AgencyJpa agency = agencyRepository.findByIdAndDeleted(idAgency, false).orElseThrow();

        CardDto effective = toDto(card, settings(idAgency));
        Map<String, String> texts = LanguageEmail.getFidelityCard(
                "IT", agency.getName(), card.getCode(),
                card.isTypePoints(), effective.getScope(), effective.getPriceForPoint()
        );

        Context context = new Context();
        context.setVariable("title",        texts.get("title"));
        context.setVariable("localeName",   agency.getName());
        context.setVariable("greeting",     texts.get("greeting"));
        context.setVariable("cardCode",     card.getCode());
        context.setVariable("qrCodeUrl",    card.getQrCodeUrl());
        context.setVariable("cardTypeLabel",texts.get("cardTypeLabel"));
        context.setVariable("scopeLabel",   texts.get("scopeLabel"));
        context.setVariable("instructions", texts.get("instructions"));
        context.setVariable("closing",      texts.get("closing"));
        context.setVariable("team",         texts.get("team"));

        boolean sent = emailService.sendEmail(email, texts.get("title"), "FidelityCard", context);
        if (sent) {
            ErrorLog.logger.info("Carta fedeltà {} inviata via email a {} (agency {})", idCard, email, idAgency);
        } else {
            ErrorLog.logger.error("Errore invio carta fedeltà {} a {} (agency {})", idCard, email, idAgency);
        }
        return sent;
    }

    @Transactional
    public boolean deleteCard(long id){
        long idAgency = authUserProvider.getAgencyId();
        long idUser = authUserProvider.getUserId();
        CardJpa cardJpa = cardRepository.findByIdAndDeletedAndIdAgency(id, false, idAgency).orElseThrow();
        cardJpa.setDeleted(true);
        cardJpa.setDeletedAt(OffsetDateTime.now());
        cardRepository.save(cardJpa);
        ErrorLog.logger.info("Card {} deleted by user {} (agency {})", id, idUser, idAgency);
        return true;
    }

    @Transactional
    public List<CardDto> getAllCards(){
        long idAgency = authUserProvider.getAgencyId();
        LoyaltySettingsJpa s = settings(idAgency);
        OffsetDateTime now = OffsetDateTime.now();
        List<CardJpa> cards = cardRepository.findAllByIdAgencyAndDeleted(idAgency, false);
        List<CardJpa> expired = cards.stream().filter(c -> applyExpiry(c, s, now)).collect(Collectors.toList());
        if (!expired.isEmpty()) cardRepository.saveAll(expired);
        return cards.stream().map(c -> toDto(c, s)).collect(Collectors.toList());
    }

}
