package com.modules.pushmodule.service;

import com.modules.common.model.Order;
import com.modules.common.model.ProductToOrder;
import com.modules.common.model.enums.ComandStatus;
import com.modules.common.model.enums.ComandWaiterType;
import com.modules.mainapp.reservation.entity.ReservationJpa;
import com.modules.ordermodule.model.ComandJpa;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Testi (brevi, in italiano) e payload delle notifiche push per lo staff. Funzioni pure, senza dipendenze. */
public final class StaffPushMessages {

    public static final String CHANNEL_ORDERS = "orders";
    public static final String CHANNEL_RESERVATIONS = "reservations";

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE dd/MM", Locale.ITALIAN);
    private static final int MAX_NAME = 40;

    private StaffPushMessages() {
    }

    /**
     * Nuovo ordine. {@code comand} è la comanda di riferimento (il tap apre questa), {@code articles} il totale
     * dei pezzi (somma su tutte le comande inviate insieme, es. invio del tavolo condiviso).
     *
     * <ul>
     *   <li>tavolo: "Nuovo ordine · Tavolo 5" / "3 articoli";</li>
     *   <li>asporto/domicilio: "Nuovo ordine" / "Asporto · Mario alle 20:30 · 3 articoli";</li>
     *   <li>da approvare: "Ordine da approvare" / "Da approvare entro le 20:15 · Asporto · Mario alle 20:30".</li>
     * </ul>
     */
    public static PushContent order(ComandJpa comand, int articles, String tableName) {
        ComandWaiterType type = comand.getComandWaiterType();
        boolean approval = comand.getStatus() == ComandStatus.AWAIT_APPROVAL;
        List<String> parts = new ArrayList<>();
        String title;

        if (type == ComandWaiterType.TABLE) {
            title = (approval ? "Ordine da approvare" : "Nuovo ordine") + " · " + tableLabel(tableName, comand.getIdTable());
            if (approval) parts.add(deadline(comand.getApprovalDeadline()));
            parts.add(articles(articles));
        } else {
            title = approval ? "Ordine da approvare" : "Nuovo ordine";
            if (approval) parts.add(deadline(comand.getApprovalDeadline()));
            parts.add(customerLine(type == ComandWaiterType.HOME ? "Domicilio" : "Asporto", comand.getName(), comand.getTime()));
            if (!approval) parts.add(articles(articles));
        }
        if (Boolean.TRUE.equals(comand.getPaid())) parts.add("pagato online");
        parts.removeIf(p -> p == null || p.isBlank());

        return new PushContent(title, String.join(" · ", parts), CHANNEL_ORDERS,
                Map.of("type", "order", "comandId", comand.getId()));
    }

    /** "Nuova prenotazione" / "Rossi · 4 persone · sab 12/10 20:30". */
    public static PushContent reservation(ReservationJpa r) {
        List<String> parts = new ArrayList<>();
        parts.add(shorten(r.getCustomerName()));
        parts.add(r.getPartySize() == 1 ? "1 persona" : r.getPartySize() + " persone");
        String when = (r.getReservationDate() != null ? r.getReservationDate().format(DAY) : "")
                + (r.getReservationTime() != null ? " " + r.getReservationTime().format(HHMM) : "");
        parts.add(when.trim());
        parts.removeIf(p -> p == null || p.isBlank());
        return new PushContent("Nuova prenotazione", String.join(" · ", parts), CHANNEL_RESERVATIONS,
                Map.of("type", "reservation", "reservationId", r.getId()));
    }

    /** Numero di pezzi (somma delle quantità) di una comanda. */
    public static int countArticles(ComandJpa comand) {
        int total = 0;
        if (comand == null || comand.getOrders() == null) return 0;
        for (Order o : comand.getOrders()) {
            if (o == null || o.getProducts() == null) continue;
            for (ProductToOrder p : o.getProducts()) {
                if (p != null) total += Math.max(0, p.getQuantity());
            }
        }
        return total;
    }

    static String articles(int n) {
        return n == 1 ? "1 articolo" : n + " articoli";
    }

    /** Il nome del tavolo può essere "5" oppure già "Tavolo 5": niente "Tavolo Tavolo 5". */
    static String tableLabel(String tableName, Long idTable) {
        String name = tableName == null ? "" : tableName.trim();
        if (name.isEmpty()) return idTable != null && idTable > 0 ? "Tavolo " + idTable : "Tavolo";
        return name.toLowerCase(Locale.ROOT).startsWith("tavolo") ? shorten(name) : "Tavolo " + shorten(name);
    }

    private static String customerLine(String channel, String name, String time) {
        String who = shorten(name);
        String at = hhmm(time);
        StringBuilder sb = new StringBuilder(channel);
        if (!who.isEmpty()) sb.append(" · ").append(who);
        if (!at.isEmpty()) sb.append(who.isEmpty() ? " · alle " : " alle ").append(at);
        return sb.toString();
    }

    private static String deadline(LocalDateTime deadline) {
        return deadline == null ? "Da approvare" : "Da approvare entro le " + deadline.format(HHMM);
    }

    /** Orario di ritiro: "2026-10-12T20:30" (asporto pubblico) oppure già "20:30". */
    static String hhmm(String time) {
        if (time == null || time.isBlank()) return "";
        String t = time.trim();
        int sep = t.indexOf('T');
        if (sep >= 0) t = t.substring(sep + 1);
        return t.length() >= 5 && t.charAt(2) == ':' ? t.substring(0, 5) : t;
    }

    private static String shorten(String s) {
        if (s == null) return "";
        String t = s.strip();
        return t.length() > MAX_NAME ? t.substring(0, MAX_NAME - 1) + "…" : t;
    }
}
