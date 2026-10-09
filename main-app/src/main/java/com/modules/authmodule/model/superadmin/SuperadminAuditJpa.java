package com.modules.authmodule.model.superadmin;

import jakarta.persistence.*;

import java.time.Instant;

/** Registro delle azioni dei superadmin (impersonazione, modifiche abbonamento, note). */
@Entity
@Table(name = "superadmin_audit", indexes = {
        @Index(name = "idx_superadmin_audit_agency", columnList = "id_agency"),
        @Index(name = "idx_superadmin_audit_created", columnList = "created_at")
})
public class SuperadminAuditJpa {

    public static final int MAX_DETAIL = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "superadmin_id")
    private Long superadminId;

    @Column(name = "superadmin_email")
    private String superadminEmail;

    @Column(name = "id_agency")
    private Long idAgency;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 32)
    private SuperadminAction action;

    @Column(name = "detail", length = MAX_DETAIL)
    private String detail;

    @Column(name = "ip", length = 64)
    private String ip;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public SuperadminAuditJpa() {
    }

    public SuperadminAuditJpa(Long superadminId, String superadminEmail, Long idAgency, SuperadminAction action, String detail, String ip) {
        this.superadminId = superadminId;
        this.superadminEmail = superadminEmail;
        this.idAgency = idAgency;
        this.action = action;
        this.detail = truncate(detail, MAX_DETAIL);
        this.ip = truncate(ip, 64);
        this.createdAt = Instant.now();
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSuperadminId() { return superadminId; }
    public String getSuperadminEmail() { return superadminEmail; }
    public Long getIdAgency() { return idAgency; }
    public SuperadminAction getAction() { return action; }
    public String getDetail() { return detail; }
    public String getIp() { return ip; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
