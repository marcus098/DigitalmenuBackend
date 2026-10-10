package com.modules.mainapp.contact;

import com.modules.common.email.EmailService;
import com.modules.common.logs.errorlog.ErrorLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Modulo "Contattaci" del sito vetrina: valida la richiesta e la inoltra via email a {@code app.contact.to}.
 * Il contenuto inserito dall'utente viene reso nel template solo con th:text (escape HTML di Thymeleaf).
 */
@Service
public class ContactService {

    public static final List<String> VENUE_TYPES = List.of(
            "Ristorante", "Pizzeria", "Bar", "Pub / Birreria", "Cocktail bar", "Trattoria / Osteria",
            "Caffè / Pasticceria", "Street food", "Sushi / Etnico", "Altro");

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@<>()\\[\\],;:\"]+@[^\\s@<>()\\[\\],;:\"]+\\.[^\\s@<>()\\[\\],;:\"]{2,}$");
    private static final Pattern PHONE = Pattern.compile("^[0-9+()./\\-\\s]{6,30}$");
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public enum Result { SENT, NOT_CONFIGURED, FAILED }

    @Value("${app.contact.to:}")
    private String contactTo;

    @Autowired
    private EmailService emailService;

    public boolean isConfigured() {
        return contactTo != null && !contactTo.isBlank();
    }

    /** Ritorna il messaggio d'errore per l'utente, oppure null se la richiesta è valida. */
    public String validate(ContactRequest r) {
        if (r == null) return "Richiesta non valida";
        if (isBlank(r.getName()) || r.getName().trim().length() > 100) return "Inserisci il tuo nome (max 100 caratteri)";
        if (isBlank(r.getVenueName()) || r.getVenueName().trim().length() > 150) return "Inserisci il nome del locale (max 150 caratteri)";
        if (isBlank(r.getEmail()) || r.getEmail().trim().length() > 254 || !EMAIL.matcher(r.getEmail().trim()).matches())
            return "Inserisci un indirizzo email valido";
        if (!isBlank(r.getPhone()) && !PHONE.matcher(r.getPhone().trim()).matches()) return "Numero di telefono non valido";
        if (isBlank(r.getVenueType()) || !VENUE_TYPES.contains(r.getVenueType().trim())) return "Seleziona il tipo di locale";
        if (isBlank(r.getMessage()) || r.getMessage().trim().length() < 10) return "Scrivi un messaggio (almeno 10 caratteri)";
        if (r.getMessage().length() > 4000) return "Messaggio troppo lungo (max 4000 caratteri)";
        if (!r.isPrivacyAccepted()) return "È necessario accettare l'informativa privacy";
        return null;
    }

    public Result send(ContactRequest r, String remoteAddr) {
        if (!isConfigured()) {
            ErrorLog.logger.warn("CONTACT: app.contact.to non configurato, richiesta da {} non inoltrata", remoteAddr);
            return Result.NOT_CONFIGURED;
        }
        String name = oneLine(r.getName());
        String venue = oneLine(r.getVenueName());
        String email = r.getEmail().trim();

        Context ctx = new Context();
        ctx.setVariable("name", name);
        ctx.setVariable("venueName", venue);
        ctx.setVariable("email", email);
        ctx.setVariable("phone", isBlank(r.getPhone()) ? "—" : oneLine(r.getPhone()));
        ctx.setVariable("venueType", r.getVenueType().trim());
        ctx.setVariable("message", r.getMessage().trim());
        ctx.setVariable("receivedAt", ZonedDateTime.now(ZoneId.of("Europe/Rome")).format(TS));
        ctx.setVariable("remoteAddr", remoteAddr);

        String subject = "Nuova richiesta di contatto: " + venue;
        if (subject.length() > 150) subject = subject.substring(0, 150);

        boolean ok = emailService.sendEmailWithReplyTo(contactTo.trim(), email, subject, "ContactRequest", ctx);
        if (ok) {
            ErrorLog.logger.info("CONTACT: richiesta inoltrata (locale '{}', ip {})", venue, remoteAddr);
            return Result.SENT;
        }
        return Result.FAILED;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** Toglie a capo e caratteri di controllo (oggetto email / campi su una riga). */
    private static String oneLine(String s) {
        return s == null ? "" : s.replaceAll("[\\p{Cntrl}]+", " ").trim();
    }
}
