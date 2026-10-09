package com.modules.mainapp.payment.service;

import com.modules.mainapp.payment.ComandTotalCalculator;
import com.modules.mainapp.payment.PaymentUrls;
import com.modules.mainapp.payment.entity.PaymentJpa;
import com.modules.mainapp.payment.entity.PaymentProvider;
import com.modules.mainapp.payment.repository.PaymentRepository;
import com.modules.mainapp.payment.stripe.StripeGateway;
import com.modules.mainapp.payment.sumup.SumUpGateway;
import com.modules.ordermodule.repository.MongoComandRepository;
import com.stripe.Stripe;
import com.stripe.model.Event;
import com.stripe.net.ApiResource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.mock;

/** Wiring di PaymentService con provider reali e gateway/repository mockati (niente Spring/DB/rete). */
class PaymentTestSupport {

    final PaymentRepository repo = mock(PaymentRepository.class);
    final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    final StripeGateway stripe = mock(StripeGateway.class);
    final SumUpGateway sumup = mock(SumUpGateway.class);
    final PaymentAccountService accounts = mock(PaymentAccountService.class);
    final MongoComandRepository comands = mock(MongoComandRepository.class);
    final PaymentUrls urls = new PaymentUrls("https://api.example.com", "https://app.example.com");
    final PaymentTransitions transitions = new PaymentTransitions(repo, publisher);
    final StripePaymentProvider stripeProvider = new StripePaymentProvider(stripe, accounts, repo, transitions);
    final SumUpPaymentProvider sumupProvider = new SumUpPaymentProvider(sumup, accounts, repo, transitions, urls);
    final PaymentService service = new PaymentService();

    PaymentTestSupport() {
        ReflectionTestUtils.setField(service, "paymentRepository", repo);
        ReflectionTestUtils.setField(service, "accountService", accounts);
        ReflectionTestUtils.setField(service, "transitions", transitions);
        ReflectionTestUtils.setField(service, "stripe", stripe);
        ReflectionTestUtils.setField(service, "stripeProvider", stripeProvider);
        ReflectionTestUtils.setField(service, "sumupProvider", sumupProvider);
        ReflectionTestUtils.setField(service, "mongoComandRepository", comands);
        ReflectionTestUtils.setField(service, "comandTotalCalculator", (ComandTotalCalculator) id -> 1500L);
    }

    static Event event(String type, String objectJson) {
        String json = "{\"id\":\"evt_1\",\"object\":\"event\",\"api_version\":\"" + Stripe.API_VERSION + "\","
                + "\"type\":\"" + type + "\",\"data\":{\"object\":" + objectJson + "}}";
        return ApiResource.GSON.fromJson(json, Event.class);
    }

    static PaymentJpa stripePayment(String status) {
        PaymentJpa p = new PaymentJpa();
        p.setId(5);
        p.setProvider(PaymentProvider.STRIPE);
        p.setIdAgency(7);
        p.setComandId("cmd_1");
        p.setAmountCents(1500);
        p.setStripePaymentIntentId("pi_1");
        p.setStripeAccountId("acct_own");
        p.setStatus(status);
        return p;
    }

    static PaymentJpa sumupPayment(String status, boolean approvalRequired) {
        PaymentJpa p = new PaymentJpa();
        p.setId(9);
        p.setProvider(PaymentProvider.SUMUP);
        p.setIdAgency(7);
        p.setComandId("cmd_1");
        p.setAmountCents(1500);
        p.setSumupCheckoutId("co_1");
        p.setSumupMerchantCode("MC123");
        p.setApprovalRequired(approvalRequired);
        p.setStatus(status);
        return p;
    }
}
