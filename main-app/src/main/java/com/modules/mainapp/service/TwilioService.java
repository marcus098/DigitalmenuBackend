package com.modules.mainapp.service;

import com.modules.common.logs.errorlog.ErrorLog;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TwilioService {

    @Value("${twilio.account-sid:}")
    private String accountSid;

    @Value("${twilio.auth-token:}")
    private String authToken;

    @Value("${twilio.from-number:}")
    private String fromNumber;

    @Value("${twilio.enabled:false}")
    private boolean enabled;

    public void sendSms(String to, String body) {
        if (!enabled || accountSid.isBlank() || authToken.isBlank() || fromNumber.isBlank()) {
            ErrorLog.logger.debug("Twilio disabilitato o non configurato — SMS non inviato a {}", to);
            return;
        }
        try {
            Twilio.init(accountSid, authToken);
            Message.creator(new PhoneNumber(to), new PhoneNumber(fromNumber), body).create();
            ErrorLog.logger.info("SMS inviato a {}", to);
        } catch (Exception e) {
            ErrorLog.logger.error("Errore invio SMS Twilio a {}: {}", to, e.getMessage());
        }
    }
}
