package com.modules.mainapp.payment;

import com.modules.deliverymodule.repository.DeliverySettingsRepository;
import com.modules.mainapp.payment.service.PaymentAccountService;
import com.modules.ordermodule.service.PrepaymentPolicy;
import org.springframework.stereotype.Component;

/** Prepagamento richiesto = impostazione del locale per il canale AND provider di pagamento online attivo (Stripe o SumUp). */
@Component
public class AgencyPrepaymentPolicy implements PrepaymentPolicy {

    private final PaymentAccountService accountService;
    private final DeliverySettingsRepository deliverySettings;

    public AgencyPrepaymentPolicy(PaymentAccountService accountService, DeliverySettingsRepository deliverySettings) {
        this.accountService = accountService;
        this.deliverySettings = deliverySettings;
    }

    @Override
    public boolean requiresPrepayment(long idAgency, Channel channel) {
        if (channel == Channel.DELIVERY) {
            // Consegna: pagamento online obbligatorio solo con ONLINE_REQUIRED e provider attivo (altrimenti decade)
            boolean onlineRequired = deliverySettings.findByIdAgency(idAgency)
                    .map(s -> "ONLINE_REQUIRED".equals(s.getPaymentMode())).orElse(false);
            return onlineRequired && accountService.isOnlinePaymentEnabled(idAgency);
        }
        return accountService.isPrepaymentRequired(idAgency, channel == Channel.TAKEAWAY);
    }

    @Override
    public boolean onlinePaymentAvailable(long idAgency) {
        return accountService.isOnlinePaymentEnabled(idAgency);
    }
}
