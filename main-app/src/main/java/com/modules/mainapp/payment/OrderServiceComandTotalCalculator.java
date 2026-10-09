package com.modules.mainapp.payment;

import com.modules.ordermodule.service.OrderComandService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Totale della comanda dai prezzi snapshot lato server (unitPriceCents * quantity),
 * calcolati da OrderComandService alla creazione dell'ordine.
 */
@Component
public class OrderServiceComandTotalCalculator implements ComandTotalCalculator {

    @Autowired
    private OrderComandService orderComandService;

    @Override
    public long computeTotalCents(String comandId) {
        return orderComandService.computeTotalCents(comandId);
    }
}
