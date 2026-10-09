package com.modules.mainapp.payment.service;

import com.modules.authmodule.model.AgencyJpa;
import com.modules.authmodule.repository.AgencyRepository;
import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.mainapp.payment.ApplicationFeeCalculator;
import com.modules.mainapp.payment.dto.ConnectStatusResponse;
import com.modules.mainapp.payment.dto.PrepaymentSettings;
import com.modules.mainapp.payment.stripe.StripeGateway;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.AccountLink;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.param.AccountCreateParams;
import com.stripe.param.AccountLinkCreateParams;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Stripe Connect (account Express) dei locali: onboarding, stato, login alla dashboard Express,
 * webhook Connect (account.updated). Ogni locale incassa sul proprio account (destination charge con on_behalf_of).
 */
@Service
public class StripeConnectService {

    private final AgencyRepository agencyRepository;
    private final StripeGateway stripe;
    private final String connectWebhookSecret;
    private final int defaultFeeBps;
    private final String frontendBaseUrl;

    public StripeConnectService(AgencyRepository agencyRepository,
                                StripeGateway stripe,
                                @Value("${stripe.connect-webhook-secret:}") String connectWebhookSecret,
                                @Value("${stripe.connect.default-fee-bps:0}") int defaultFeeBps,
                                @Value("${app.frontend-base-url:${app.frontend.url:http://localhost:3000}}") String frontendBaseUrl) {
        this.agencyRepository = agencyRepository;
        this.stripe = stripe;
        this.connectWebhookSecret = connectWebhookSecret;
        this.defaultFeeBps = defaultFeeBps;
        this.frontendBaseUrl = frontendBaseUrl.endsWith("/")
                ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1) : frontendBaseUrl;
    }

    @PostConstruct
    void checkConfig() {
        if (!isConnectWebhookConfigured()) {
            ErrorLog.logger.warn("STRIPE: stripe.connect-webhook-secret non configurato: /api/payments/webhook/connect "
                    + "rifiuterà gli eventi (lo stato degli account verrà aggiornato solo da GET /api/payments/connect/status).");
        }
    }

    public boolean isConnectWebhookConfigured() {
        return connectWebhookSecret != null && connectWebhookSecret.startsWith("whsec_");
    }

    /** bps effettivi della commissione piattaforma per il locale. */
    public int effectiveFeeBps(AgencyJpa agency) {
        return ApplicationFeeCalculator.effectiveBps(agency.getApplicationFeeBps(), defaultFeeBps);
    }

    /** true se il locale può ricevere pagamenti online (account collegato e charges_enabled). */
    public static boolean canAcceptPayments(AgencyJpa agency) {
        return agency != null && agency.getStripeAccountId() != null && !agency.getStripeAccountId().isBlank()
                && Boolean.TRUE.equals(agency.getStripeChargesEnabled());
    }

    public boolean isPaymentsEnabled(long idAgency) {
        return stripe.isConfigured()
                && agencyRepository.findByIdAndDeleted(idAgency, false).map(StripeConnectService::canAcceptPayments).orElse(false);
    }

    // ── Prepagamento ────────────────────────────────────────────────────────

    public PrepaymentSettings getPrepaymentSettings(long idAgency) {
        AgencyJpa a = requireAgency(idAgency);
        return new PrepaymentSettings(Boolean.TRUE.equals(a.getPrepaymentTakeaway()),
                Boolean.TRUE.equals(a.getPrepaymentTable()), stripe.isConfigured() && canAcceptPayments(a));
    }

    /** @throws ResponseStatusException 400 se si prova ad attivare il prepagamento senza Stripe attivo */
    @Transactional
    public PrepaymentSettings updatePrepaymentSettings(long idAgency, boolean takeaway, boolean table) {
        AgencyJpa a = requireAgency(idAgency);
        boolean enabled = stripe.isConfigured() && canAcceptPayments(a);
        if ((takeaway || table) && !enabled) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Attiva prima i pagamenti online con Stripe per richiedere il prepagamento");
        }
        a.setPrepaymentTakeaway(takeaway);
        a.setPrepaymentTable(table);
        agencyRepository.save(a);
        return new PrepaymentSettings(takeaway, table, enabled);
    }

    /** Prepagamento effettivo: impostazione del locale AND pagamenti attivi (se Stripe si disattiva, decade). */
    public boolean isPrepaymentRequired(long idAgency, boolean takeaway) {
        if (!stripe.isConfigured()) return false;
        return agencyRepository.findByIdAndDeleted(idAgency, false)
                .filter(StripeConnectService::canAcceptPayments)
                .map(a -> Boolean.TRUE.equals(takeaway ? a.getPrepaymentTakeaway() : a.getPrepaymentTable()))
                .orElse(false);
    }

    public AgencyJpa requireAgency(long idAgency) {
        return agencyRepository.findByIdAndDeleted(idAgency, false)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Locale non trovato"));
    }

    /**
     * Crea l'account Express del locale se manca e restituisce il link di onboarding (monouso, scade in pochi minuti).
     */
    @Transactional
    public String createOnboardingLink(long idAgency) {
        AgencyJpa agency = requireAgency(idAgency);
        try {
            if (agency.getStripeAccountId() == null || agency.getStripeAccountId().isBlank()) {
                AccountCreateParams params = AccountCreateParams.builder()
                        .setType(AccountCreateParams.Type.EXPRESS)
                        .setCountry("IT")
                        .setDefaultCurrency("eur")
                        .setCapabilities(AccountCreateParams.Capabilities.builder()
                                .setCardPayments(AccountCreateParams.Capabilities.CardPayments.builder().setRequested(true).build())
                                .setTransfers(AccountCreateParams.Capabilities.Transfers.builder().setRequested(true).build())
                                .build())
                        .putMetadata("idAgency", String.valueOf(idAgency))
                        .build();
                // Idempotency: doppio click => stesso account, non due
                Account account = stripe.createAccount(params, "acct-create-agency-" + idAgency);
                agency.setStripeAccountId(account.getId());
                applyAccountFlags(agency, account);
                agencyRepository.save(agency);
            }

            String base = frontendBaseUrl + "/" + UriUtils.encodePathSegment(agency.getName(), StandardCharsets.UTF_8)
                    + "/Dashboard/PaymentsSettings";
            AccountLink link = stripe.createAccountLink(AccountLinkCreateParams.builder()
                    .setAccount(agency.getStripeAccountId())
                    .setRefreshUrl(base + "?stripe=refresh")
                    .setReturnUrl(base + "?stripe=return")
                    .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
                    .build());
            return link.getUrl();
        } catch (StripeException e) {
            ErrorLog.logger.error("Stripe Connect: errore onboarding agency {}", idAgency, e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Errore Stripe: " + e.getMessage());
        }
    }

    /** Stato dell'account del locale, riallineato da Stripe (se collegato). */
    @Transactional
    public ConnectStatusResponse refreshStatus(long idAgency) {
        AgencyJpa agency = requireAgency(idAgency);
        boolean connected = agency.getStripeAccountId() != null && !agency.getStripeAccountId().isBlank();
        if (connected && stripe.isConfigured()) {
            try {
                Account account = stripe.retrieveAccount(agency.getStripeAccountId());
                applyAccountFlags(agency, account);
                agencyRepository.save(agency);
            } catch (StripeException e) {
                // Mostra lo stato in cache: non bloccare la pagina impostazioni per un errore transitorio
                ErrorLog.logger.error("Stripe Connect: impossibile leggere account {}", agency.getStripeAccountId(), e);
            }
        }
        return new ConnectStatusResponse(
                stripe.isConfigured(),
                connected,
                Boolean.TRUE.equals(agency.getStripeChargesEnabled()),
                Boolean.TRUE.equals(agency.getStripeDetailsSubmitted()),
                effectiveFeeBps(agency));
    }

    /** Link monouso alla dashboard Express del locale (incassi, bonifici, dati fiscali). */
    public String createDashboardLink(long idAgency) {
        AgencyJpa agency = requireAgency(idAgency);
        if (agency.getStripeAccountId() == null || agency.getStripeAccountId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Account Stripe non collegato");
        }
        if (!Boolean.TRUE.equals(agency.getStripeDetailsSubmitted())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Completa prima la configurazione Stripe");
        }
        try {
            return stripe.createLoginLink(agency.getStripeAccountId()).getUrl();
        } catch (StripeException e) {
            ErrorLog.logger.error("Stripe Connect: errore login link agency {}", idAgency, e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Errore Stripe: " + e.getMessage());
        }
    }

    /**
     * Webhook Connect (eventi degli account collegati). Idempotente: imposta i flag al valore corrente dell'account.
     *
     * @throws SignatureVerificationException firma non valida o secret non configurato (→ 400)
     */
    @Transactional
    public void handleConnectWebhook(String payload, String sigHeader) throws SignatureVerificationException {
        if (!isConnectWebhookConfigured()) {
            ErrorLog.logger.warn("Stripe Connect webhook ricevuto ma stripe.connect-webhook-secret non è configurato");
            throw new SignatureVerificationException("Connect webhook secret not configured", sigHeader);
        }
        Event event = stripe.constructEvent(payload, sigHeader, connectWebhookSecret);
        if (!"account.updated".equals(event.getType())) return;

        StripeObject obj = deserialize(event);
        if (!(obj instanceof Account account) || account.getId() == null) return;

        Optional<AgencyJpa> agency = agencyRepository.findByStripeAccountId(account.getId());
        if (agency.isEmpty()) {
            ErrorLog.logger.info("Stripe Connect webhook: account {} non associato a nessun locale", account.getId());
            return;
        }
        AgencyJpa a = agency.get();
        applyAccountFlags(a, account);
        agencyRepository.save(a);
    }

    static void applyAccountFlags(AgencyJpa agency, Account account) {
        agency.setStripeChargesEnabled(Boolean.TRUE.equals(account.getChargesEnabled()));
        agency.setStripeDetailsSubmitted(Boolean.TRUE.equals(account.getDetailsSubmitted()));
    }

    static StripeObject deserialize(Event event) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        StripeObject obj = deserializer.getObject().orElse(null);
        if (obj == null) {
            // Versione API dell'evento diversa da quella della libreria: deserializza comunque
            try {
                obj = deserializer.deserializeUnsafe();
            } catch (Exception e) {
                throw new IllegalStateException("Impossibile deserializzare l'evento Stripe " + event.getId(), e);
            }
        }
        return obj;
    }
}
