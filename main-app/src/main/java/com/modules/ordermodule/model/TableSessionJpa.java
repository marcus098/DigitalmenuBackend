package com.modules.ordermodule.model;

import com.modules.common.model.enums.SessionStatus;
import com.modules.ordermodule.request.AddComandOrder;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "table_sessions")
public class TableSessionJpa {

    @Id
    private String id;

    @Indexed
    private long tableId;

    @Indexed
    private long idAgency;

    private String localname;

    private String accessCode;

    private int seats;

    private SessionStatus status;

    private List<SessionClient> clients = new ArrayList<>();

    private List<SubmittedComand> comands = new ArrayList<>();

    private OffsetDateTime createdAt;
    private OffsetDateTime submittedAt;
    private OffsetDateTime closedAt;

    public TableSessionJpa() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public long getTableId() { return tableId; }
    public void setTableId(long tableId) { this.tableId = tableId; }
    public long getIdAgency() { return idAgency; }
    public void setIdAgency(long idAgency) { this.idAgency = idAgency; }
    public String getLocalname() { return localname; }
    public void setLocalname(String localname) { this.localname = localname; }
    public String getAccessCode() { return accessCode; }
    public void setAccessCode(String accessCode) { this.accessCode = accessCode; }
    public int getSeats() { return seats; }
    public void setSeats(int seats) { this.seats = seats; }
    public SessionStatus getStatus() { return status; }
    public void setStatus(SessionStatus status) { this.status = status; }
    public List<SessionClient> getClients() { return clients; }
    public void setClients(List<SessionClient> clients) { this.clients = clients; }
    public List<SubmittedComand> getComands() { return comands; }
    public void setComands(List<SubmittedComand> comands) { this.comands = comands; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(OffsetDateTime submittedAt) { this.submittedAt = submittedAt; }
    public OffsetDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(OffsetDateTime closedAt) { this.closedAt = closedAt; }

    public static class SessionClient {
        private String clientSessionId;
        private String label;
        private boolean ready;
        private List<AddComandOrder> draftOrder = new ArrayList<>();
        private OffsetDateTime joinedAt;
        private OffsetDateTime lastReadyAt;

        public SessionClient() {}

        public String getClientSessionId() { return clientSessionId; }
        public void setClientSessionId(String clientSessionId) { this.clientSessionId = clientSessionId; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public boolean isReady() { return ready; }
        public void setReady(boolean ready) { this.ready = ready; }
        public List<AddComandOrder> getDraftOrder() { return draftOrder; }
        public void setDraftOrder(List<AddComandOrder> draftOrder) { this.draftOrder = draftOrder; }
        public OffsetDateTime getJoinedAt() { return joinedAt; }
        public void setJoinedAt(OffsetDateTime joinedAt) { this.joinedAt = joinedAt; }
        public OffsetDateTime getLastReadyAt() { return lastReadyAt; }
        public void setLastReadyAt(OffsetDateTime lastReadyAt) { this.lastReadyAt = lastReadyAt; }
    }

    public static class SubmittedComand {
        private String clientSessionId;
        private String comandId;

        public SubmittedComand() {}

        public SubmittedComand(String clientSessionId, String comandId) {
            this.clientSessionId = clientSessionId;
            this.comandId = comandId;
        }

        public String getClientSessionId() { return clientSessionId; }
        public void setClientSessionId(String clientSessionId) { this.clientSessionId = clientSessionId; }
        public String getComandId() { return comandId; }
        public void setComandId(String comandId) { this.comandId = comandId; }
    }
}
