package com.modules.mainapp.payment;

import com.modules.mainapp.payment.service.StripeConnectService;
import com.modules.ordermodule.service.PrepaymentPolicy;
import org.springframework.stereotype.Component;

/** Prepagamento richiesto = impostazione del locale per il canale AND pagamenti Stripe attivi. */
@Component
public class AgencyPrepaymentPolicy implements PrepaymentPolicy {

    private final StripeConnectService connectService;

    public AgencyPrepaymentPolicy(StripeConnectService connectService) {
        this.connectService = connectService;
    }

    @Override
    public boolean requiresPrepayment(long idAgency, Channel channel) {
        return connectService.isPrepaymentRequired(idAgency, channel == Channel.TAKEAWAY);
    }
}
