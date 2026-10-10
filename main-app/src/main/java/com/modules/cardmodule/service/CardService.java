package com.modules.cardmodule.service;

import com.modules.authmodule.model.AgencyJpa;
import com.modules.authmodule.repository.AgencyRepository;
import com.modules.cardmodule.models.CardClaim;
import com.modules.cardmodule.models.CardJpa;
import com.modules.cardmodule.models.LoyaltySettingsJpa;
import com.modules.cardmodule.repository.CardClaimRepository;
import com.modules.cardmodule.repository.CardRepository;
import com.modules.cardmodule.repository.LoyaltySettingsRepository;
import com.modules.cardmodule.requests.AddCard;
import com.modules.cardmodule.requests.LoyaltySettingsDto;
import com.modules.common.dto.CardDto;
import com.modules.common.email.EmailRequest;
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

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CardService {
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
        s.setEurosPerPoint(dto.eurosPerPoint());
        s.setPointValue(dto.pointValue());
        s.setStampsForPrize(dto.stampsForPrize());
        s.setStampsPrize(dto.stampsPrize() == null || dto.stampsPrize().isBlank() ? null : dto.stampsPrize().trim());
        s.setUpdatedAt(OffsetDateTime.now());
        ErrorLog.logger.info("Impostazioni tessere aggiornate (agency {})", idAgency);
        return LoyaltySettingsDto.of(loyaltySettingsRepository.save(s));
    }

    private LoyaltySettingsJpa settings(long idAgency) {
        return loyaltySettingsRepository.findById(idAgency).orElse(null);
    }

    /** Timbri per il premio: impostazione del locale, altrimenti quella salvata sulla tessera. */
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
        return dto;
    }

    private CardDto toDto(CardJpa card) {
        return toDto(card, settings(card.getIdAgency()));
    }

    // ── Movimenti alla chiusura del conto ────────────────────────────────────

    public record CheckoutLoyalty(int claimed, int earned, CardDto card) {}

    /**
     * Movimenti tessera alla chiusura di un conto: prima scala (punti usati / premio timbri), poi accredita.
     * I punti guadagnati sono calcolati qui sul totale pagato, con le regole del locale.
     */
    @Transactional
    public CheckoutLoyalty applyCheckout(long cardId, long idAgency, long idUser, long totalCents,
                                         int pointsToUse, boolean redeemStamps, boolean earn) {
        CardJpa card = cardRepository.findByIdAndDeletedAndIdAgency(cardId, false, idAgency)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Card non trovata"));
        LoyaltySettingsJpa s = settings(idAgency);
        int scope = effectiveScope(card, s);

        int claim = card.isTypePoints() ? Math.max(0, pointsToUse)
                : (redeemStamps && card.getActualValue() >= scope ? scope : 0);
        claim = Math.min(claim, card.getActualValue());
        if (claim > 0) {
            cardClaimRepository.save(new CardClaim(card.getId(), OffsetDateTime.now(), idAgency, idUser, claim));
            card.setActualValue(card.getActualValue() - claim);
        }

        int earned = 0;
        if (earn) {
            if (card.isTypePoints()) {
                double eurosPerPoint = effectiveEurosPerPoint(card, s);
                earned = eurosPerPoint > 0 ? (int) Math.floor(totalCents / (eurosPerPoint * 100) + 1e-9) : 0;
                card.setActualValue(card.getActualValue() + earned);
            } else if (card.getActualValue() < scope) {
                earned = 1;
                card.setActualValue(card.getActualValue() + 1);
            }
        }
        card = cardRepository.save(card);
        return new CheckoutLoyalty(claim, earned, toDto(card, s));
    }


    @Transactional
    public CardDto claimPoints(long id, int quantity){
        long idAgency = authUserProvider.getAgencyId();
        long idUser = authUserProvider.getUserId();

        CardJpa cardJpa = cardRepository.findByIdAndDeletedAndIdAgency(id, false, idAgency)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Card non trovata"));

        if(quantity > cardJpa.getActualValue()){
            quantity = cardJpa.getActualValue();
        }
        // Lo storico registra quanto è stato davvero scalato, non quanto richiesto
        cardClaimRepository.save(new CardClaim(cardJpa.getId(), OffsetDateTime.now(), idAgency, idUser, quantity));
        cardJpa.setActualValue(cardJpa.getActualValue() - quantity);
        cardJpa = cardRepository.save(cardJpa);
        return toDto(cardJpa);
    }

    @Transactional
    public CardDto addPoints(long id, int quantity) {
        long idAgency = authUserProvider.getAgencyId();
        CardJpa cardJpa = cardRepository.findByIdAndDeletedAndIdAgency(id, false, idAgency)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Card non trovata"));
        LoyaltySettingsJpa s = settings(idAgency);
        int value = cardJpa.getActualValue() + quantity;
        int scope = effectiveScope(cardJpa, s);
        if(!cardJpa.isTypePoints() && value > scope){
            value = scope;
        }
        cardJpa.setActualValue(value);
        cardJpa = cardRepository.save(cardJpa);
        return toDto(cardJpa, s);
    }

    @Transactional
    public CardDto resetPoints(long id){
        long idAgency = authUserProvider.getAgencyId();
        CardJpa cardJpa = cardRepository.findByIdAndDeletedAndIdAgency(id, false, idAgency).orElseThrow();
        cardJpa.setActualValue(0);
        cardJpa = cardRepository.save(cardJpa);
        return toDto(cardJpa);
    }

    @Transactional
    public CardDto getCardInfo(String code){
        // Scoped all'agency del chiamante: una card di un altro locale risulta inesistente.
        long idAgency = authUserProvider.getAgencyId();
        return toDto(cardRepository.findByCodeAndDeletedAndIdAgency(code, false, idAgency)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Card non trovata")));
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
        int scope = !addCard.isTypePoints() && s != null && s.getStampsForPrize() != null ? s.getStampsForPrize() : addCard.getScope();
        double priceForPoint = !addCard.isTypePoints() ? 0
                : s != null && s.getEurosPerPoint() != null ? s.getEurosPerPoint()
                : addCard.getPriceForPoint() != null ? addCard.getPriceForPoint() : 1;
        CardJpa card = new CardJpa(cardCode, addCard.isTypePoints(), 0, scope, priceForPoint, url, idAgency, OffsetDateTime.now());
        card = cardRepository.save(card);
        ErrorLog.logger.info("Card {} created for agency {}", card.getId(), idAgency);
        return toDto(card, s);
    }

    public boolean sendCardByEmail(String email, long idCard) {
        long idAgency = authUserProvider.getAgencyId();
        CardJpa card = cardRepository.findByIdAndDeletedAndIdAgency(idCard, false, idAgency).orElseThrow();
        AgencyJpa agency = agencyRepository.findByIdAndDeleted(idAgency, false).orElseThrow();

        CardDto effective = toDto(card);
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
        return cardRepository
                .findAllByIdAgencyAndDeleted(idAgency, false)
                .stream()
                .map(c -> toDto(c, s)).collect(Collectors.toList());
    }

}
