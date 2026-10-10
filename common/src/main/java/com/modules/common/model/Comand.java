package com.modules.common.model;

import com.modules.common.model.enums.ComandStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public abstract class Comand {
    private String id;
    private ComandType type;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<Order> orders;
    private ComandStatus status;
    private Long idAgency;
    private String tableSessionId;
    private String clientSessionId;
    /** Pagamento online confermato da Stripe (null/false = non pagata online). */
    private Boolean paid;
    private LocalDateTime paidAt;
    private String paymentIntentId;
    /** Ordine nella riserva dello slot: richiede l'approvazione del locale (anche dopo il prepagamento). */
    private Boolean approvalRequired;
    /** Scadenza entro cui il locale deve approvare (poi rifiuto automatico). */
    private LocalDateTime approvalDeadline;
    /** Motivo del rifiuto/annullamento mostrato al cliente. */
    private String rejectReason;
    /** Importo autorizzato su Stripe (capture manuale) ma non ancora incassato. */
    private Boolean paymentAuthorized;
    /** Autorizzazione annullata (ordine rifiutato): nessun addebito per il cliente. */
    private Boolean authorizationCanceled;
    /** Pagamento rimborsato integralmente. */
    private Boolean refunded;
    /** Chiusura del conto in cassa (null = non ancora chiuso in cassa). */
    private ComandCheckout checkout;

    public Comand() {}

    public ComandCheckout getCheckout() { return checkout; }
    public void setCheckout(ComandCheckout checkout) { this.checkout = checkout; }

    public Boolean getApprovalRequired() { return approvalRequired; }
    public void setApprovalRequired(Boolean approvalRequired) { this.approvalRequired = approvalRequired; }
    public LocalDateTime getApprovalDeadline() { return approvalDeadline; }
    public void setApprovalDeadline(LocalDateTime approvalDeadline) { this.approvalDeadline = approvalDeadline; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
    public Boolean getPaymentAuthorized() { return paymentAuthorized; }
    public void setPaymentAuthorized(Boolean paymentAuthorized) { this.paymentAuthorized = paymentAuthorized; }
    public Boolean getAuthorizationCanceled() { return authorizationCanceled; }
    public void setAuthorizationCanceled(Boolean authorizationCanceled) { this.authorizationCanceled = authorizationCanceled; }
    public Boolean getRefunded() { return refunded; }
    public void setRefunded(Boolean refunded) { this.refunded = refunded; }

    public Boolean getPaid() {
        return paid;
    }

    public void setPaid(Boolean paid) {
        this.paid = paid;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }

    public String getPaymentIntentId() {
        return paymentIntentId;
    }

    public void setPaymentIntentId(String paymentIntentId) {
        this.paymentIntentId = paymentIntentId;
    }

    public String getClientSessionId() {
        return clientSessionId;
    }

    public void setClientSessionId(String clientSessionId) {
        this.clientSessionId = clientSessionId;
    }

    public Comand(Long idAgency, ComandType comandType) {
        this.idAgency = idAgency;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.orders = new ArrayList<>();
        this.status = ComandStatus.AWAIT;
        this.type = comandType;
        this.tableSessionId = "";
    }

    public Comand(Long idAgency, ComandType comandType, String tableSessionId) {
        this.idAgency = idAgency;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.orders = new ArrayList<>();
        this.status = ComandStatus.AWAIT;
        this.type = comandType;
        this.tableSessionId = tableSessionId;
    }

    // per waiters
    public Comand(Long idAgency, List<Order> orders, ComandStatus status, ComandType comandType) {
        this.idAgency = idAgency;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.orders = orders;
        this.status = status;
        this.type = comandType;
    }

    public Comand(Long idAgency, List<Order> orders, ComandStatus status, ComandType comandType, String tableSessionId) {
        this.idAgency = idAgency;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.orders = orders;
        this.status = status;
        this.type = comandType;
        this.tableSessionId = tableSessionId;
    }

    public ComandType getType() {
        return type;
    }

    public String getTableSessionId() {
        return tableSessionId;
    }

    public void setTableSessionId(String tableSessionId) {
        this.tableSessionId = tableSessionId;
    }

    public void setType(ComandType type) {
        this.type = type;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Long getIdAgency() {
        return idAgency;
    }

    public String getId() {
        return id;
    }

    public ComandStatus getStatus() {
        return status;
    }

    public List<Order> getOrders() {
        return orders;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setIdAgency(Long idAgency) {
        this.idAgency = idAgency;
    }

    public void setStatus(ComandStatus status) {
        this.status = status;
    }

    public void setOrders(List<Order> orders) {
        this.orders = orders;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "Comand{" +
                "id='" + id + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", orders=" + orders +
                ", status=" + status +
                ", idAgency=" + idAgency +
                '}';
    }
}
