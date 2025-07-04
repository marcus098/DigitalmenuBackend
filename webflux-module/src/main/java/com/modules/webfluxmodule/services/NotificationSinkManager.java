package com.modules.webfluxmodule.services;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class NotificationSinkManager {

    // Struttura dati: Mappa per agenzia, che contiene una mappa di sessioni con i loro Sink personali.
    // Usiamo ConcurrentHashMap per la sicurezza in ambienti multi-thread.
    private final Map<Long, Map<String, Sinks.Many<Map<String, Object>>>> sinksByAgency = new ConcurrentHashMap<>();

    /**
     * Registra un nuovo client, crea un Sink personale per lui e lo restituisce.
     * Chiamato dal Controller SSE quando un client si connette.
     * @param agencyId L'ID dell'agenzia del client.
     * @param sessionId L'ID univoco per questa connessione SSE.
     * @return Il Sink personale del client.
     */
    public Sinks.Many<Map<String, Object>> register(Long agencyId, String sessionId) {
        // Assicura che esista una mappa per l'agenzia.
        sinksByAgency.computeIfAbsent(agencyId, k -> new ConcurrentHashMap<>());

        // Crea un nuovo Sink per questo specifico client/sessione.
        // .multicast().onBackpressureBuffer() è una buona scelta per i broadcast.
        Sinks.Many<Map<String, Object>> newSink = Sinks.many().multicast().onBackpressureBuffer();

        // Memorizza il sink associato alla sessione del client.
        sinksByAgency.get(agencyId).put(sessionId, newSink);

        System.out.println("REGISTRATO: Cliente con sessione " + sessionId + " per agenzia " + agencyId);
        return newSink;
    }

    /**
     * Rimuove un client quando si disconnette.
     * Chiamato dal Controller SSE quando la connessione si chiude.
     * @param agencyId L'ID dell'agenzia.
     * @param sessionId L'ID della sessione da rimuovere.
     */
    public void unregister(Long agencyId, String sessionId) {
        Map<String, Sinks.Many<Map<String, Object>>> agencySinks = sinksByAgency.get(agencyId);
        if (agencySinks != null) {
            // Rimuove il sink del client e lo termina.
            Sinks.Many<Map<String, Object>> removedSink = agencySinks.remove(sessionId);
            if (removedSink != null) {
                removedSink.tryEmitComplete(); // Termina il flusso per il client disconnesso.
                System.out.println("RIMOSSO: Cliente con sessione " + sessionId + " per agenzia " + agencyId);
            }
        }
    }

    /**
     * Invia un payload a tutti i client di un'agenzia, escludendo chi ha originato il messaggio.
     * Chiamato dal Kafka Listener.
     * @param agencyId L'agenzia a cui inviare la notifica.
     * @param originatingSessionId L'ID della sessione da escludere.
     * @param payload Il dato da inviare.
     */
    public void broadcast(Long agencyId, String originatingSessionId, Map<String, Object> payload) {
        Map<String, Sinks.Many<Map<String, Object>>> agencySinks = sinksByAgency.get(agencyId);
        if (agencySinks != null) {
            System.out.println("BROADCAST per agenzia " + agencyId + ", escludendo sessione " + originatingSessionId);

            // Itera su tutti i sink registrati per l'agenzia.
            agencySinks.forEach((sessionId, sink) -> {
                // Invia il messaggio solo se il sessionId è diverso da quello che ha originato l'evento.
                if (!sessionId.equals(originatingSessionId)) {
                    sink.tryEmitNext(payload);
                }
            });
        }
    }
}