package com.modules.mainapp.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

/** URL pubblici usati dai provider di pagamento (webhook verso il backend, ritorno del cliente verso il frontend). */
@Component
public class PaymentUrls {

    private final String publicBaseUrl;
    private final String frontendBaseUrl;

    public PaymentUrls(@Value("${app.public-base-url:http://localhost:8080}") String publicBaseUrl,
                       @Value("${app.frontend-base-url:${app.frontend.url:http://localhost:3000}}") String frontendBaseUrl) {
        this.publicBaseUrl = stripSlash(publicBaseUrl);
        this.frontendBaseUrl = stripSlash(frontendBaseUrl);
    }

    private static String stripSlash(String s) {
        return s != null && s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static String seg(String s) {
        return UriUtils.encodePathSegment(s, StandardCharsets.UTF_8);
    }

    public String stripeWebhookUrl(String webhookToken) {
        return publicBaseUrl + "/api/payments/webhook/stripe/" + seg(webhookToken);
    }

    public String sumupWebhookUrl(String webhookToken) {
        return publicBaseUrl + "/api/payments/webhook/sumup/" + seg(webhookToken);
    }

    /** Pagina del frontend a cui SumUp riporta il cliente dopo il checkout ospitato. */
    public String sumupRedirectUrl(String localname, String comandId) {
        return frontendBaseUrl + "/" + seg(localname) + "/payment/" + seg(comandId) + "?provider=sumup";
    }
}
