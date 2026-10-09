package com.modules.mainapp.superadmin;

import com.modules.common.responses.AuthResponse;

import java.util.List;

/** Contratto JSON delle API /api/superadmin/** (date sempre come stringhe ISO-8601 o null). */
public final class SuperadminDtos {

    private SuperadminDtos() {
    }

    public record AgencySummary(
            long id,
            String name,
            String localname,
            String adminEmail,
            String adminName,
            String phone,
            String createdAt,
            String activeFrom,
            String subscriptionNumber,
            String plan,
            String subscriptionStatus,
            boolean trial,
            String trialEndAt,
            String billingEndAt,
            boolean blocked,
            String paymentProvider,
            long tablesCount,
            long waitersCount,
            String lastOrderAt,
            long ordersLast30Days,
            long notesCount,
            boolean deleted
    ) {
    }

    public record Note(long id, String text, String authorName, String authorEmail, String createdAt, boolean pinned) {
    }

    public record AuditEntry(long id, String superadminEmail, Long idAgency, String agencyName, String action,
                             String detail, String ip, String createdAt) {
    }

    public record UserRow(long id, String username, String email, String role, String name, String surname,
                          boolean confirmed, boolean deleted) {
    }

    public record AgencyDetail(AgencySummary summary, List<Note> notes, List<AuditEntry> audit, List<UserRow> users) {
    }

    public record AgencyRef(long id, String name, String localname) {
    }

    public record ImpersonationResponse(String token, String expiresAt, AgencyRef agency, AuthResponse auth) {
    }

    public record Stats(long agencies, long active, long trial, long suspended, long cancelled, long blocked,
                        long ordersToday, long ordersLast30Days) {
    }

    // ── request ──

    public static class SubscriptionUpdate {
        private String activeFrom;
        private String subscriptionNumber;
        private String plan;
        private String subscriptionStatus;
        private Boolean trial;
        private String trialEndAt;
        private String billingEndAt;

        public String getActiveFrom() { return activeFrom; }
        public void setActiveFrom(String activeFrom) { this.activeFrom = activeFrom; }
        public String getSubscriptionNumber() { return subscriptionNumber; }
        public void setSubscriptionNumber(String subscriptionNumber) { this.subscriptionNumber = subscriptionNumber; }
        public String getPlan() { return plan; }
        public void setPlan(String plan) { this.plan = plan; }
        public String getSubscriptionStatus() { return subscriptionStatus; }
        public void setSubscriptionStatus(String subscriptionStatus) { this.subscriptionStatus = subscriptionStatus; }
        public Boolean getTrial() { return trial; }
        public void setTrial(Boolean trial) { this.trial = trial; }
        public String getTrialEndAt() { return trialEndAt; }
        public void setTrialEndAt(String trialEndAt) { this.trialEndAt = trialEndAt; }
        public String getBillingEndAt() { return billingEndAt; }
        public void setBillingEndAt(String billingEndAt) { this.billingEndAt = billingEndAt; }
    }

    public record NoteCreate(String text, Boolean pinned) {
    }

    public record NotePatch(Boolean pinned) {
    }

    public record ImpersonateRequest(String reason) {
    }
}
