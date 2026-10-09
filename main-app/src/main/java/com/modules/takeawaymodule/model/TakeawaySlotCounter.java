package com.modules.takeawaymodule.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Contatore atomico di occupazione di uno slot asporto (agency, giorno, orario di inizio slot).
 * Incrementato con findAndModify condizionato (count < capacità) → il controllo capacità è
 * race-safe anche con più istanze del backend. Conta i comand TAKE_AWAY non DELETED (online + cameriere);
 * gli ordini "manuali" della dashboard restano nell'override JPA e vengono sottratti dalla capacità.
 */
@Document(collection = "takeaway_slot_counters")
public class TakeawaySlotCounter {

    /** "{idAgency}|{yyyy-MM-dd}|{HH:mm}" */
    @Id
    private String id;
    private long idAgency;
    private String slotDate;
    private String slotTime;
    private int orders;
    private int products;

    public TakeawaySlotCounter() {}

    public TakeawaySlotCounter(String id, long idAgency, String slotDate, String slotTime, int orders, int products) {
        this.id = id;
        this.idAgency = idAgency;
        this.slotDate = slotDate;
        this.slotTime = slotTime;
        this.orders = orders;
        this.products = products;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public long getIdAgency() { return idAgency; }
    public void setIdAgency(long idAgency) { this.idAgency = idAgency; }
    public String getSlotDate() { return slotDate; }
    public void setSlotDate(String slotDate) { this.slotDate = slotDate; }
    public String getSlotTime() { return slotTime; }
    public void setSlotTime(String slotTime) { this.slotTime = slotTime; }
    public int getOrders() { return orders; }
    public void setOrders(int orders) { this.orders = orders; }
    public int getProducts() { return products; }
    public void setProducts(int products) { this.products = products; }
}
