package com.modules.ordermodule.service;

import com.modules.common.dto.UserDto;
import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.common.model.ComandHistoryEntry;
import com.modules.common.model.ComandTakenBy;
import com.modules.servletconfiguration.security.AuthenticatedUserProvider;
import org.springframework.data.mongodb.core.query.Update;

import java.util.function.Consumer;

/**
 * Storico delle comande: le voci si aggiungono con un $push limitato (ultime {@link ComandHistoryEntry#MAX_ENTRIES})
 * nello STESSO update condizionale della transizione, così una voce esiste solo se la transizione è avvenuta.
 */
public final class ComandHistory {

    public static final String FIELD = "history";

    private ComandHistory() {}

    /** $push della voce nel campo history, scartando le più vecchie oltre il limite. */
    public static Update push(Update u, ComandHistoryEntry entry) {
        if (entry != null) u.push(FIELD).slice(-ComandHistoryEntry.MAX_ENTRIES).each(entry);
        return u;
    }

    /** Extra per {@link ComandStatusUpdater#compareAndSet}: aggiunge la voce e poi gli eventuali altri campi. */
    public static Consumer<Update> with(ComandHistoryEntry entry, Consumer<Update> extra) {
        return u -> {
            push(u, entry);
            if (extra != null) extra.accept(u);
        };
    }

    /**
     * Chi sta facendo l'azione: l'utente autenticato {id, "Nome Cognome" o username}; "Sistema" se non c'è un utente
     * (job, webhook) o la lettura fallisce. Mai un errore: lo storico non deve bloccare l'operazione.
     */
    public static ComandTakenBy currentActor(AuthenticatedUserProvider auth) {
        if (auth == null) return ComandHistoryEntry.system();
        try {
            UserDto me = auth.getAuthenticatedUser();
            if (me == null) return ComandHistoryEntry.system();
            return new ComandTakenBy(me.getId(), ComandTakenBy.displayName(me.getName(), me.getSurname(), me.getUsername()));
        } catch (Exception e) {
            ErrorLog.logger.warn("Storico comanda: utente non disponibile ({})", e.getMessage());
            return ComandHistoryEntry.system();
        }
    }
}
