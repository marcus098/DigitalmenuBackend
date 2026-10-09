package com.modules.mainapp.payment;

import com.modules.mainapp.payment.service.PaymentAccountService;
import com.modules.ordermodule.service.PrepaymentPolicy;
import org.springframework.stereotype.Component;

/** Prepagamento richiesto = impostazione del locale per il canale AND provider di pagamento online attivo (Stripe o SumUp). */
@Component
public class AgencyPrepaymentPolicy implements PrepaymentPolicy {

    private final PaymentAccountService accountService;

    public AgencyPrepaymentPolicy(PaymentAccountService accountService) {
        this.accountService = accountService;
    }

    @Override
    public boolean requiresPrepayment(long idAgency, Channel channel) {
        return accountService.isPrepaymentRequired(idAgency, channel == Channel.TAKEAWAY);
    }
}
