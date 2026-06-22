package com.modules.mainapp.payment.service;

import com.modules.mainapp.payment.dto.PaymentIntentResponse;
import com.modules.mainapp.payment.entity.PaymentJpa;
import com.modules.mainapp.payment.repository.PaymentRepository;
import com.modules.servletconfiguration.security.AuthenticatedUserProvider;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class PaymentService {

    @Value("${stripe.secret-key:}")
    private String stripeSecretKey;

    @Value("${stripe.webhook-secret:}")
    private String webhookSecret;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private AuthenticatedUserProvider authUserProvider;

    public PaymentIntentResponse createIntent(long idAgency, Long idTable, String comandId, long amountCents, String currency) {
        if (stripeSecretKey == null || stripeSecretKey.isBlank()) {
            throw new IllegalStateException("Stripe secret key not configured");
        }
        Stripe.apiKey = stripeSecretKey;

        try {
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountCents)
                    .setCurrency(currency != null && !currency.isBlank() ? currency : "eur")
                    .putMetadata("idAgency", String.valueOf(idAgency))
                    .putMetadata("comandId", comandId != null ? comandId : "")
                    .putMetadata("idTable", idTable != null ? String.valueOf(idTable) : "")
                    .build();

            PaymentIntent intent = PaymentIntent.create(params);

            PaymentJpa payment = new PaymentJpa();
            payment.setIdAgency(idAgency);
            payment.setIdTable(idTable);
            payment.setComandId(comandId);
            payment.setAmountCents(amountCents);
            payment.setCurrency(currency != null && !currency.isBlank() ? currency : "eur");
            payment.setStripePaymentIntentId(intent.getId());
            payment.setStripeClientSecret(intent.getClientSecret());
            payment.setStatus("PENDING");
            paymentRepository.save(payment);

            return new PaymentIntentResponse(intent.getClientSecret(), intent.getId());
        } catch (Exception e) {
            throw new RuntimeException("Stripe error: " + e.getMessage(), e);
        }
    }

    public void handleWebhook(String payload, String sigHeader) {
        Stripe.apiKey = stripeSecretKey;
        try {
            Event event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
            if ("payment_intent.succeeded".equals(event.getType())) {
                event.getDataObjectDeserializer().getObject().ifPresent(obj -> {
                    PaymentIntent intent = (PaymentIntent) obj;
                    paymentRepository.findByStripePaymentIntentId(intent.getId())
                            .ifPresent(p -> { p.setStatus("COMPLETED"); paymentRepository.save(p); });
                });
            } else if ("payment_intent.payment_failed".equals(event.getType())) {
                event.getDataObjectDeserializer().getObject().ifPresent(obj -> {
                    PaymentIntent intent = (PaymentIntent) obj;
                    paymentRepository.findByStripePaymentIntentId(intent.getId())
                            .ifPresent(p -> { p.setStatus("FAILED"); paymentRepository.save(p); });
                });
            }
        } catch (SignatureVerificationException e) {
            throw new RuntimeException("Invalid Stripe webhook signature", e);
        } catch (Exception e) {
            throw new RuntimeException("Webhook processing error: " + e.getMessage(), e);
        }
    }

    public List<PaymentJpa> getPaymentsForAgency() {
        long idAgency = authUserProvider.getAgencyId();
        return paymentRepository.findByIdAgencyOrderByCreatedAtDesc(idAgency);
    }

    public long getTodayTotalCentsForAgency() {
        long idAgency = authUserProvider.getAgencyId();
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().plusDays(1).atStartOfDay();
        return paymentRepository.findByIdAgencyAndCreatedAtBetweenAndStatus(idAgency, startOfDay, endOfDay, "COMPLETED")
                .stream().mapToLong(PaymentJpa::getAmountCents).sum();
    }
}
