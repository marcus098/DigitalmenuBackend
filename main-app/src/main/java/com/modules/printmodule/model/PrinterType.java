package com.modules.printmodule.model;

/**
 * STAR_CLOUDPRNT: stampanti Star (mC-Print2/3, TSP143IV) che interrogano direttamente il server via CloudPRNT.
 * ESCPOS_BRIDGE:  stampanti ESC/POS economiche in LAN raggiunte tramite il bridge Node (print-bridge/) sul PC cassa.
 * TABLET_RAWBT:   stampa dal tablet Android della dashboard tramite l'app RawBT (Bluetooth / USB / WiFi:9100).
 *                 Nessuna coda server: il browser scarica l'ESC/POS (GET /api/printers/tablet/ticket/{comandId}) e lo
 *                 passa a RawBT con un intent, solo su gesto dell'utente ("Accetta e stampa", "Ristampa").
 *                 Queste stampanti non ricevono mai job PENDING e non hanno un dispositivo che si autentica col token.
 */
public enum PrinterType {
    STAR_CLOUDPRNT,
    ESCPOS_BRIDGE,
    TABLET_RAWBT
}
