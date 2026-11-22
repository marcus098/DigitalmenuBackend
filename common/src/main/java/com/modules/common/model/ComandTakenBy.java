package com.modules.common.model;

/**
 * Operatore dello staff (cameriere o titolare) che ha inserito la comanda.
 * Impostato SEMPRE lato server dall'utente autenticato, mai dal payload del client.
 * Il nome è uno snapshot: resta leggibile anche se l'utente viene rinominato o eliminato.
 * Null per le comande dei clienti (tavolo, asporto, domicilio dal menu).
 */
public class ComandTakenBy {
    private Long id;
    private String name;

    public ComandTakenBy() {}

    public ComandTakenBy(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    /** "Nome Cognome" se presenti, altrimenti lo username. */
    public static String displayName(String name, String surname, String username) {
        String full = ((name == null ? "" : name.strip()) + " " + (surname == null ? "" : surname.strip())).strip();
        return !full.isEmpty() ? full : (username == null ? "" : username.strip());
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
