package com.modules.printmodule.service;

import com.modules.authmodule.repository.AgencyRepository;
import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.common.model.ComandType;
import com.modules.common.model.Order;
import com.modules.common.model.ProductToOrder;
import com.modules.common.model.enums.ComandWaiterType;
import com.modules.ordermodule.model.ComandJpa;
import com.modules.printmodule.model.PrinterDoc;
import com.modules.printmodule.render.TicketData;
import com.modules.printmodule.render.TicketRenderer;
import com.modules.tablemodule.repository.TableEntityRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Costruisce i TicketData a partire da una comanda (risolvendo nome locale e nome tavolo). */
@Component
public class TicketFactory {

    private final AgencyRepository agencyRepository;
    private final TableEntityRepository tableRepository;

    public TicketFactory(AgencyRepository agencyRepository, TableEntityRepository tableRepository) {
        this.agencyRepository = agencyRepository;
        this.tableRepository = tableRepository;
    }

    /** Dati comuni alla comanda, calcolati una volta sola per tutte le stampanti. */
    public record ComandContext(ComandJpa comand, String restaurantName, String tableName) {}

    public ComandContext context(ComandJpa comand) {
        String tableName = null;
        Long idTable = comand.getIdTable();
        if (idTable != null && idTable > 0) {
            try {
                tableName = tableRepository.findById(idTable).map(t -> t.getName()).orElse(String.valueOf(idTable));
            } catch (Exception e) {
                ErrorLog.logger.error("Stampa: errore lettura tavolo " + idTable, e);
                tableName = String.valueOf(idTable);
            }
        }
        return new ComandContext(comand, restaurantName(comand.getIdAgency()), tableName);
    }

    public String restaurantName(Long idAgency) {
        if (idAgency == null) return "";
        try {
            return agencyRepository.findById(idAgency)
                    .map(a -> a.getOriginalName() != null && !a.getOriginalName().isBlank() ? a.getOriginalName() : a.getName())
                    .orElse("");
        } catch (Exception e) {
            ErrorLog.logger.error("Stampa: errore lettura agency " + idAgency, e);
            return "";
        }
    }

    /** Ticket filtrato per le categorie della stampante; empty se nessun prodotto corrisponde. */
    public Optional<TicketData> ticket(ComandContext ctx, PrinterDoc printer, String banner) {
        ComandJpa c = ctx.comand();
        List<TicketData.Item> items = new ArrayList<>();
        if (c.getOrders() != null) {
            for (Order order : c.getOrders()) {
                if (order == null || order.getProducts() == null) continue;
                for (ProductToOrder p : order.getProducts()) {
                    if (p == null || !matchesFilter(p, printer.getCategoryFilter())) continue;
                    items.add(new TicketData.Item(
                            p.getCategoryName(),
                            p.getQuantity(),
                            p.getProductName(),
                            p.getProductOption() != null ? p.getProductOption().getName() : null,
                            p.getIngredientsMinus() == null ? List.of() : p.getIngredientsMinus().stream().map(i -> i.getName()).toList(),
                            p.getIngredientsPlus() == null ? List.of() : p.getIngredientsPlus().stream().map(i -> i.getName()).toList(),
                            p.getNote()));
                }
            }
        }
        if (items.isEmpty()) return Optional.empty();
        return Optional.of(new TicketData(
                ctx.restaurantName(),
                orderKind(c),
                ctx.tableName(),
                c.getName(),
                c.getPhone(),
                c.getAddress(),
                c.getTime(),
                c.getCreatedAt(),
                TicketRenderer.shortId(c.getId()),
                banner,
                items));
    }

    public static boolean matchesFilter(ProductToOrder p, List<String> filter) {
        if (filter == null || filter.isEmpty()) return true;
        String id = String.valueOf(p.getIdCategory());
        String name = p.getCategoryName();
        for (String f : filter) {
            if (f == null) continue;
            String v = f.trim();
            if (v.equals(id) || (name != null && v.equalsIgnoreCase(name.trim()))) return true;
        }
        return false;
    }

    static TicketData.OrderKind orderKind(ComandJpa c) {
        ComandWaiterType wt = c.getComandWaiterType();
        if (wt != null) {
            return switch (wt) {
                case TABLE -> TicketData.OrderKind.TABLE;
                case TAKE_AWAY -> TicketData.OrderKind.TAKE_AWAY;
                case HOME -> TicketData.OrderKind.HOME;
            };
        }
        if (c.getType() == ComandType.FROM_TAKEAWAY) return TicketData.OrderKind.TAKE_AWAY;
        if (c.getType() == ComandType.FROM_HOME) return TicketData.OrderKind.HOME;
        return TicketData.OrderKind.TABLE;
    }
}
