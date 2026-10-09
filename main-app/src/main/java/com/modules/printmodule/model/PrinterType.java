package com.modules.printmodule.model;

/**
 * STAR_CLOUDPRNT: stampanti Star (mC-Print2/3, TSP143IV) che interrogano direttamente il server via CloudPRNT.
 * ESCPOS_BRIDGE:  stampanti ESC/POS economiche in LAN raggiunte tramite il bridge Node (print-bridge/) sul PC cassa.
 */
public enum PrinterType {
    STAR_CLOUDPRNT,
    ESCPOS_BRIDGE
}
