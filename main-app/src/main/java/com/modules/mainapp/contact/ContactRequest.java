package com.modules.mainapp.contact;

import lombok.Data;

/**
 * Payload del modulo "Contattaci" del sito vetrina (POST /api/public/contact).
 * {@code website} è un honeypot: invisibile agli utenti, se valorizzato la richiesta viene scartata.
 */
@Data
public class ContactRequest {
    private String name;
    private String venueName;
    private String email;
    private String phone;
    private String venueType;
    private String message;
    private boolean privacyAccepted;
    private String website;
}
