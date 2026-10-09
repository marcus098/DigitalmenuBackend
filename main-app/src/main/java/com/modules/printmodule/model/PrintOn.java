package com.modules.printmodule.model;

/**
 * CREATED:  stampa appena la comanda viene creata.
 * ACCEPTED: stampa quando la comanda passa da AWAIT/PENDING a PROGRESS (o se nasce già in PROGRESS).
 */
public enum PrintOn {
    CREATED,
    ACCEPTED
}
