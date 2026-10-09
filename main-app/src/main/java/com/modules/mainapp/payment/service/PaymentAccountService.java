package com.modules.mainapp.payment.service;

import com.modules.authmodule.model.AgencyJpa;
import com.modules.authmodule.repository.AgencyRepository;
import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.mainapp.payment.PaymentUrls;
import com.modules.mainapp.payment.crypto.SecretCipher;
import com.modules.mainapp.payment.dto.PrepaymentSettings;
import com.modules.mainapp.payment.dto.ProviderRequests;
import com.modules.mainapp.payment.dto.ProviderSettings;
import com.modules.mainapp.payment.entity.AgencyPaymentAccountJpa;
import com.modules.mainapp.payment.entity.PaymentProvider;
import com.modules.mainapp.payment.repository.AgencyPaymentAccountRepository;
import com.modules.mainapp.payment.stripe.StripeCredentials;
import com.modules.mainapp.payment.stripe.StripeGateway;
import com.modules.mainapp.payment.sumup.SumUpCredentials;
import com.modules.mainapp.payment.sumup.SumUpException;
import com.modules.mainapp.payment.sumup.SumUpGateway;
import com.stripe.exception.AuthenticationException;
import com.stripe.exception.PermissionException;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.WebhookEndpoint;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;

/**
 * Account di pagamento dei locali: ogni locale incassa sul PROPRIO account Stripe o SumUp (la piattaforma non tocca
 * mai i soldi, niente Connect né commissioni). Gestisce credenziali cifrate, verifica delle chiavi, webhook Stripe
 * automatico, provider attivo e impostazioni di prepagamento.
 */
@Service
public class PaymentAccountService {

    static final String WEBHOOK_AUTO = "AUTO";
    static final String WEBHOOK_MANUAL = "MANUAL";
    static final String WEBHOOK_MISSING = "MISSING";

    private final AgencyPaymentAccountRepository accounts;
    private final AgencyRepository agencyRepository;
    private final SecretCipher cipher;
    private final StripeGateway stripe;
    private final SumUpGateway sumup;
    private final PaymentUrls urls;
    private final SecureRandom random = new SecureRandom();

    public PaymentAccountService(AgencyPaymentAccountRepository accounts, AgencyRepository agencyRepository,
                                 SecretCipher cipher, StripeGateway stripe, SumUpGateway sumup, PaymentUrls urls) {
        this.accounts = accounts;
        this.agencyRepository = agencyRepository;
        this.cipher = cipher;
        this.stripe = stripe;
        this.sumup = sumup;
        this.urls = urls;
    }

    // ── Lookup ──────────────────────────────────────────────────────────────

    public AgencyJpa requireAgency(long idAgency) {
        return agencyRepository.findByIdAndDeleted(idAgency, false)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Locale non trovato"));
    }

    public Optional<AgencyPaymentAccountJpa> findAccount(long idAgency) {
        return accounts.findById(idAgency);
    }

    public Optional<AgencyPaymentAccountJpa> findByWebhookToken(String token) {
        if (token == null || token.isBlank() || token.length() > 64) return Optional.empty();
        return accounts.findByWebhookToken(token);
    }

    @Transactional
    public AgencyPaymentAccountJpa getOrCreate(long idAgency) {
        return accounts.findById(idAgency).orElseGet(() -> {
            AgencyPaymentAccountJpa a = new AgencyPaymentAccountJpa();
            a.setIdAgency(idAgency);
            a.setActiveProvider(PaymentProvider.NONE);
            a.setWebhookToken(newToken());
            return accounts.save(a);
        });
    }

    private String newToken() {
        byte[] b = new byte[32];
        random.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    /** Provider attivo E utilizzabile (credenziali configurate, cifratura disponibile); NONE altrimenti. */
    public PaymentProvider enabledProvider(AgencyPaymentAccountJpa a) {
        if (a == null || !cipher.isAvailable()) return PaymentProvider.NONE;
        PaymentProvider p = a.getActiveProvider();
        return p != PaymentProvider.NONE && a.isConfigured(p) ? p : PaymentProvider.NONE;
    }

    public PaymentProvider enabledProvider(long idAgency) {
        return enabledProvider(accounts.findById(idAgency).orElse(null));
    }

    public boolean isOnlinePaymentEnabled(long idAgency) {
        return enabledProvider(idAgency) != PaymentProvider.NONE;
    }

    /** Credenziali Stripe correnti del locale (vuoto se assenti o non decifrabili). */
    public Optional<StripeCredentials> stripeCredentials(long idAgency) {
        return accounts.findById(idAgency).flatMap(this::stripeCredentials);
    }

    public Optional<StripeCredentials> stripeCredentials(AgencyPaymentAccountJpa a) {
        String key = decryptQuiet(a.getStripeSecretKeyEnc(), a.getIdAgency(), "stripe secret key");
        return key == null ? Optional.empty() : Optional.of(new StripeCredentials(a.getIdAgency(), key, a.getStripeAccountId()));
    }

    public Optional<String> stripeWebhookSecret(AgencyPaymentAccountJpa a) {
        return Optional.ofNullable(decryptQuiet(a.getStripeWebhookSecretEnc(), a.getIdAgency(), "stripe webhook secret"));
    }

    /** Credenziali SumUp correnti del locale (vuoto se assenti o non decifrabili). */
    public Optional<SumUpCredentials> sumupCredentials(long idAgency) {
        return accounts.findById(idAgency).flatMap(a -> {
            String key = decryptQuiet(a.getSumupApiKeyEnc(), idAgency, "sumup api key");
            return key == null ? Optional.empty() : Optional.of(new SumUpCredentials(idAgency, key, a.getSumupMerchantCode()));
        });
    }

    private String decryptQuiet(String enc, Long idAgency, String what) {
        if (enc == null || !cipher.isAvailable()) return null;
        try {
            return cipher.decrypt(enc);
        } catch (RuntimeException e) {
            ErrorLog.logger.error("Impossibile decifrare {} del locale {} (chiave di cifratura cambiata?)", what, idAgency);
            return null;
        }
    }

    // ── Impostazioni (dashboard) ───────────────────────────────────────────

    @Transactional
    public ProviderSettings getSettings(long idAgency) {
        AgencyJpa agency = requireAgency(idAgency);
        return toSettings(agency, getOrCreate(idAgency));
    }

    ProviderSettings toSettings(AgencyJpa agency, AgencyPaymentAccountJpa a) {
        boolean anyStripe = a.getStripeSecretKeyEnc() != null || a.getStripePublishableKey() != null;
        String webhookStatus = !anyStripe ? null
                : a.getStripeWebhookSecretEnc() == null ? WEBHOOK_MISSING
                : a.getStripeWebhookEndpointId() != null ? WEBHOOK_AUTO : WEBHOOK_MANUAL;
        ProviderSettings.Stripe s = new ProviderSettings.Stripe(
                a.isStripeConfigured(), a.getStripePublishableKey(), a.getStripeSecretKeyLast4(), a.getStripeLivemode(),
                a.getStripeAccountId(), a.getStripeAccountName(), webhookStatus, urls.stripeWebhookUrl(a.getWebhookToken()),
                a.getStripeLastError(), iso(a.getStripeVerifiedAt()));
        ProviderSettings.SumUp u = new ProviderSettings.SumUp(
                a.isSumupConfigured(), a.getSumupMerchantCode(), a.getSumupApiKeyLast4(), a.getSumupLastError(),
                iso(a.getSumupVerifiedAt()));
        return new ProviderSettings(a.getActiveProvider().name(), enabledProvider(a) != PaymentProvider.NONE,
                cipher.isAvailable(), Boolean.TRUE.equals(agency.getPrepaymentTakeaway()),
                Boolean.TRUE.equals(agency.getPrepaymentTable()), s, u);
    }

    private static String iso(Instant i) {
        return i == null ? null : i.truncatedTo(ChronoUnit.SECONDS).toString();
    }

    private static String trim(String s) {
        if (s == null) return null;
        String t = s.strip();
        return t.isEmpty() ? null : t;
    }

    private static String last4(String s) {
        return s.length() <= 4 ? s : s.substring(s.length() - 4);
    }

    private static ResponseStatusException badRequest(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }

    /**
     * Salva e verifica le chiavi Stripe del locale; crea (se possibile) il webhook sul suo account.
     *
     * @throws ResponseStatusException 400 formato non valido / chiave rifiutata, 502 Stripe non raggiungibile,
     *                                 503 cifratura non configurata
     */
    @Transactional
    public ProviderSettings saveStripe(long idAgency, ProviderRequests.StripeKeys req) {
        cipher.requireAvailable();
        AgencyJpa agency = requireAgency(idAgency);
        AgencyPaymentAccountJpa a = getOrCreate(idAgency);

        String pk = trim(req != null ? req.publishableKey() : null);
        if (pk == null || !(pk.startsWith("pk_live_") || pk.startsWith("pk_test_"))) {
            throw badRequest("Chiave pubblicabile non valida: deve iniziare con pk_live_ o pk_test_");
        }
        String oldSecret = decryptQuiet(a.getStripeSecretKeyEnc(), idAgency, "stripe secret key");
        String sk = trim(req.secretKey());
        if (sk == null) {
            sk = oldSecret;
            if (sk == null) throw badRequest("Inserisci la chiave segreta (sk_...) o la chiave limitata (rk_...)");
        } else if (!(sk.startsWith("sk_live_") || sk.startsWith("sk_test_")
                || sk.startsWith("rk_live_") || sk.startsWith("rk_test_"))) {
            throw badRequest("Chiave segreta non valida: deve iniziare con sk_live_, sk_test_, rk_live_ o rk_test_");
        }
        boolean live = sk.startsWith("sk_live_") || sk.startsWith("rk_live_");
        if (pk.startsWith("pk_live_") != live) {
            throw badRequest("Le chiavi devono essere entrambe live o entrambe di test");
        }
        String whsec = trim(req.webhookSecret());
        if (whsec != null && !whsec.startsWith("whsec_")) {
            throw badRequest("Il signing secret del webhook deve iniziare con whsec_");
        }

        // Verifica della chiave sull'account del locale
        StripeCredentials creds = new StripeCredentials(idAgency, sk, null);
        String accountId = null;
        String accountName = null;
        try {
            Account acc = stripe.retrieveOwnAccount(creds);
            accountId = acc.getId();
            accountName = accountName(acc);
        } catch (PermissionException e) {
            // Chiave limitata senza lettura account: autenticata ma con meno permessi, si prova il saldo
            try {
                stripe.retrieveBalance(creds);
            } catch (PermissionException e2) {
                ErrorLog.logger.info("Stripe: chiave limitata del locale {} senza permessi di lettura account/saldo", idAgency);
            } catch (AuthenticationException e2) {
                throw badRequest("Chiave segreta rifiutata da Stripe: verifica di averla copiata correttamente");
            } catch (StripeException e2) {
                throw stripeUnavailable(idAgency, e2);
            }
        } catch (AuthenticationException e) {
            throw badRequest("Chiave segreta rifiutata da Stripe: verifica di averla copiata correttamente");
        } catch (StripeException e) {
            throw stripeUnavailable(idAgency, e);
        }
        if (accountId != null) creds = new StripeCredentials(idAgency, sk, accountId);

        boolean keyChanged = oldSecret == null || !oldSecret.equals(sk);
        String oldEndpoint = a.getStripeWebhookEndpointId();
        StripeCredentials oldCreds = oldSecret != null ? new StripeCredentials(idAgency, oldSecret, a.getStripeAccountId()) : null;
        String lastError = null;

        boolean autoStillValid = !keyChanged && oldEndpoint != null && a.getStripeWebhookSecretEnc() != null;
        if (!autoStillValid) {
            try {
                WebhookEndpoint ep = stripe.createWebhookEndpoint(creds, urls.stripeWebhookUrl(a.getWebhookToken()),
                        "Digitalmenu - pagamenti ordini " + agency.getName());
                if (ep == null || ep.getSecret() == null || ep.getId() == null) {
                    throw new IllegalStateException("Stripe non ha restituito il signing secret del webhook");
                }
                if (oldEndpoint != null) deleteEndpointQuietly(oldCreds, oldEndpoint);
                a.setStripeWebhookEndpointId(ep.getId());
                a.setStripeWebhookSecretEnc(cipher.encrypt(ep.getSecret()));
            } catch (StripeException | RuntimeException e) {
                String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                ErrorLog.logger.warn("Stripe: webhook automatico non creato per il locale {}: {}", idAgency, reason);
                boolean keepPreviousManual = !keyChanged && oldEndpoint == null && a.getStripeWebhookSecretEnc() != null;
                if (whsec != null) {
                    if (oldEndpoint != null) deleteEndpointQuietly(oldCreds, oldEndpoint);
                    a.setStripeWebhookEndpointId(null);
                    a.setStripeWebhookSecretEnc(cipher.encrypt(whsec));
                } else if (!keepPreviousManual) {
                    if (oldEndpoint != null) deleteEndpointQuietly(oldCreds, oldEndpoint);
                    a.setStripeWebhookEndpointId(null);
                    a.setStripeWebhookSecretEnc(null);
                    lastError = "Webhook non creato automaticamente (" + reason + "). Crea un endpoint nella "
                            + "Dashboard Stripe (Sviluppatori → Webhook) con l'URL indicato e incolla qui il signing "
                            + "secret (whsec_...).";
                }
            }
        }

        a.setStripePublishableKey(pk);
        if (keyChanged) {
            a.setStripeSecretKeyEnc(cipher.encrypt(sk));
            a.setStripeSecretKeyLast4(last4(sk));
        }
        a.setStripeAccountId(accountId);
        a.setStripeAccountName(accountName);
        a.setStripeLivemode(live);
        a.setStripeVerifiedAt(Instant.now());
        a.setStripeLastError(lastError);
        accounts.save(a);
        stripe.invalidate(idAgency);
        return toSettings(agency, a);
    }

    private static ResponseStatusException stripeUnavailable(long idAgency, StripeException e) {
        ErrorLog.logger.error("Stripe: verifica chiavi del locale {} fallita: {}", idAgency, e.getMessage());
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Impossibile verificare le chiavi con Stripe: " + (e.getUserMessage() != null ? e.getUserMessage() : "riprova tra poco"));
    }

    private static String accountName(Account acc) {
        if (acc.getSettings() != null && acc.getSettings().getDashboard() != null
                && acc.getSettings().getDashboard().getDisplayName() != null) {
            return acc.getSettings().getDashboard().getDisplayName();
        }
        if (acc.getBusinessProfile() != null && acc.getBusinessProfile().getName() != null) {
            return acc.getBusinessProfile().getName();
        }
        return acc.getEmail();
    }

    private void deleteEndpointQuietly(StripeCredentials creds, String endpointId) {
        if (creds == null || endpointId == null) return;
        try {
            stripe.deleteWebhookEndpoint(creds, endpointId);
        } catch (StripeException | RuntimeException e) {
            ErrorLog.logger.warn("Stripe: impossibile eliminare il webhook {} del locale {}: {}",
                    endpointId, creds.idAgency(), e.getMessage());
        }
    }

    /**
     * Salva e verifica la chiave API SumUp del locale (GET /v0.1/me). merchantCode omesso = quello del profilo.
     *
     * @throws ResponseStatusException 400 chiave mancante/rifiutata, 502 SumUp non raggiungibile, 503 cifratura
     */
    @Transactional
    public ProviderSettings saveSumUp(long idAgency, ProviderRequests.SumUpKeys req) {
        cipher.requireAvailable();
        AgencyJpa agency = requireAgency(idAgency);
        AgencyPaymentAccountJpa a = getOrCreate(idAgency);

        String oldKey = decryptQuiet(a.getSumupApiKeyEnc(), idAgency, "sumup api key");
        String key = trim(req != null ? req.apiKey() : null);
        if (key == null) {
            key = oldKey;
            if (key == null) throw badRequest("Inserisci la chiave API SumUp");
        } else if (key.length() < 16 || key.chars().anyMatch(Character::isWhitespace)) {
            throw badRequest("Chiave API SumUp non valida");
        }
        String merchantCode = trim(req != null ? req.merchantCode() : null);
        if (merchantCode != null) merchantCode = merchantCode.toUpperCase(Locale.ROOT);

        String profileCode;
        try {
            profileCode = sumup.fetchMerchantCode(key);
        } catch (SumUpException e) {
            if (e.isUnauthorized()) {
                throw badRequest("Chiave API rifiutata da SumUp: verifica di averla copiata correttamente");
            }
            ErrorLog.logger.error("SumUp: verifica chiave del locale {} fallita: {}", idAgency, e.getMessage());
            if (e.isTransient()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Impossibile contattare SumUp: riprova tra poco");
            }
            throw badRequest("SumUp ha rifiutato la chiave API (" + e.getMessage() + ")");
        }
        String effective = merchantCode != null ? merchantCode : profileCode;
        if (effective == null) {
            throw badRequest("Codice esercente SumUp non trovato nel profilo: inseriscilo a mano");
        }
        String warning = null;
        if (merchantCode != null && profileCode != null && !merchantCode.equalsIgnoreCase(profileCode)) {
            warning = "Il codice esercente inserito (" + merchantCode + ") è diverso da quello del profilo SumUp ("
                    + profileCode + "): verifica che sia corretto";
        }

        if (oldKey == null || !oldKey.equals(key)) {
            a.setSumupApiKeyEnc(cipher.encrypt(key));
            a.setSumupApiKeyLast4(last4(key));
        }
        a.setSumupMerchantCode(effective);
        a.setSumupVerifiedAt(Instant.now());
        a.setSumupLastError(warning);
        accounts.save(a);
        return toSettings(agency, a);
    }

    /**
     * Cambia il provider attivo. NONE disattiva anche il prepagamento. I pagamenti già aperti mantengono il proprio
     * provider (incasso/rimborso/webhook continuano a funzionare finché le credenziali restano salvate).
     */
    @Transactional
    public ProviderSettings setActive(long idAgency, String provider) {
        PaymentProvider target;
        try {
            target = PaymentProvider.valueOf(provider == null ? "" : provider.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw badRequest("Provider non valido: usa NONE, STRIPE o SUMUP");
        }
        AgencyJpa agency = requireAgency(idAgency);
        AgencyPaymentAccountJpa a = getOrCreate(idAgency);
        if (target != PaymentProvider.NONE) {
            cipher.requireAvailable();
            if (!a.isConfigured(target)) {
                if (target == PaymentProvider.STRIPE && a.hasStripeKeys()) {
                    throw badRequest("Webhook Stripe non configurato: incolla il signing secret (whsec_...) prima di attivare Stripe");
                }
                throw badRequest(target == PaymentProvider.STRIPE
                        ? "Configura prima le chiavi Stripe" : "Configura prima la chiave API SumUp");
            }
        }
        a.setActiveProvider(target);
        accounts.save(a);
        if (target == PaymentProvider.NONE) disablePrepayment(agency);
        return toSettings(agency, a);
    }

    @Transactional
    public ProviderSettings deleteStripe(long idAgency) {
        AgencyJpa agency = requireAgency(idAgency);
        AgencyPaymentAccountJpa a = getOrCreate(idAgency);
        if (a.getStripeWebhookEndpointId() != null) {
            stripeCredentials(a).ifPresent(c -> deleteEndpointQuietly(c, a.getStripeWebhookEndpointId()));
        }
        a.setStripePublishableKey(null);
        a.setStripeSecretKeyEnc(null);
        a.setStripeSecretKeyLast4(null);
        a.setStripeWebhookSecretEnc(null);
        a.setStripeWebhookEndpointId(null);
        a.setStripeAccountId(null);
        a.setStripeAccountName(null);
        a.setStripeLivemode(null);
        a.setStripeVerifiedAt(null);
        a.setStripeLastError(null);
        deactivateIf(agency, a, PaymentProvider.STRIPE);
        accounts.save(a);
        stripe.invalidate(idAgency);
        return toSettings(agency, a);
    }

    @Transactional
    public ProviderSettings deleteSumUp(long idAgency) {
        AgencyJpa agency = requireAgency(idAgency);
        AgencyPaymentAccountJpa a = getOrCreate(idAgency);
        a.setSumupApiKeyEnc(null);
        a.setSumupApiKeyLast4(null);
        a.setSumupMerchantCode(null);
        a.setSumupVerifiedAt(null);
        a.setSumupLastError(null);
        deactivateIf(agency, a, PaymentProvider.SUMUP);
        accounts.save(a);
        return toSettings(agency, a);
    }

    private void deactivateIf(AgencyJpa agency, AgencyPaymentAccountJpa a, PaymentProvider removed) {
        if (a.getActiveProvider() == removed) {
            a.setActiveProvider(PaymentProvider.NONE);
            disablePrepayment(agency);
        }
    }

    private void disablePrepayment(AgencyJpa agency) {
        if (Boolean.TRUE.equals(agency.getPrepaymentTakeaway()) || Boolean.TRUE.equals(agency.getPrepaymentTable())) {
            agency.setPrepaymentTakeaway(false);
            agency.setPrepaymentTable(false);
            agencyRepository.save(agency);
        }
    }

    // ── Prepagamento ────────────────────────────────────────────────────────

    public PrepaymentSettings getPrepaymentSettings(long idAgency) {
        AgencyJpa a = requireAgency(idAgency);
        return new PrepaymentSettings(Boolean.TRUE.equals(a.getPrepaymentTakeaway()),
                Boolean.TRUE.equals(a.getPrepaymentTable()), isOnlinePaymentEnabled(idAgency));
    }

    /** @throws ResponseStatusException 400 se si prova ad attivare il prepagamento senza un provider attivo */
    @Transactional
    public PrepaymentSettings updatePrepaymentSettings(long idAgency, boolean takeaway, boolean table) {
        AgencyJpa a = requireAgency(idAgency);
        boolean enabled = isOnlinePaymentEnabled(idAgency);
        if ((takeaway || table) && !enabled) {
            throw badRequest("Attiva prima i pagamenti online (Stripe o SumUp) per richiedere il prepagamento");
        }
        a.setPrepaymentTakeaway(takeaway);
        a.setPrepaymentTable(table);
        agencyRepository.save(a);
        return new PrepaymentSettings(takeaway, table, enabled);
    }

    /** Prepagamento effettivo: impostazione del locale AND pagamenti attivi (se il provider si disattiva, decade). */
    public boolean isPrepaymentRequired(long idAgency, boolean takeaway) {
        if (!isOnlinePaymentEnabled(idAgency)) return false;
        return agencyRepository.findByIdAndDeleted(idAgency, false)
                .map(a -> Boolean.TRUE.equals(takeaway ? a.getPrepaymentTakeaway() : a.getPrepaymentTable()))
                .orElse(false);
    }
}
