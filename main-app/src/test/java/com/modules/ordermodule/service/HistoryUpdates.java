package com.modules.ordermodule.service;

import com.modules.common.model.ComandHistoryEntry;
import org.bson.Document;
import org.springframework.data.mongodb.core.query.Update;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/** Test: legge le voci di storico aggiunte con $push (ComandHistory.push) da un Update di Spring Data. */
final class HistoryUpdates {

    private HistoryUpdates() {}

    static List<ComandHistoryEntry> pushed(Update u) {
        Object push = u.getUpdateObject().get("$push");
        if (!(push instanceof Document d) || !(d.get(ComandHistory.FIELD) instanceof Update.Modifiers m)) return List.of();
        List<ComandHistoryEntry> out = new ArrayList<>();
        for (Update.Modifier mod : m.getModifiers()) {
            if (!"$each".equals(mod.getKey())) continue;
            Object v = mod.getValue();
            if (v instanceof Object[] arr) for (Object o : arr) out.add((ComandHistoryEntry) o);
            else if (v instanceof Collection<?> c) for (Object o : c) out.add((ComandHistoryEntry) o);
        }
        return out;
    }

    /** Valore del $slice del $push (limite dello storico), null se assente. */
    static Object slice(Update u) {
        Object push = u.getUpdateObject().get("$push");
        if (!(push instanceof Document d) || !(d.get(ComandHistory.FIELD) instanceof Update.Modifiers m)) return null;
        return m.getModifiers().stream().filter(x -> "$slice".equals(x.getKey())).map(Update.Modifier::getValue)
                .findFirst().orElse(null);
    }

    /** Applica l'extra passato a ComandStatusUpdater.compareAndSet e restituisce le voci aggiunte. */
    static List<ComandHistoryEntry> pushed(Consumer<Update> extra) {
        Update u = new Update();
        if (extra != null) extra.accept(u);
        return pushed(u);
    }
}
