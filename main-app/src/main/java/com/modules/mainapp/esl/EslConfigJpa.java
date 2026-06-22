package com.modules.mainapp.esl;

import jakarta.persistence.*;

@Entity
@Table(name = "esl_config")
public class EslConfigJpa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "table_id", nullable = false, unique = true)
    private Long tableId;

    @Column(name = "id_agency", nullable = false)
    private Long idAgency;

    /** MAC address of the e-ink tag assigned to this table (e.g. "AA:BB:CC:DD:EE:FF") */
    @Column(name = "esl_tag_mac", length = 17)
    private String eslTagMac;

    /** Override AP URL for this table; if null, falls back to esl.ap.default-url config */
    @Column(name = "esl_ap_url", length = 100)
    private String eslApUrl;

    public EslConfigJpa() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTableId() { return tableId; }
    public void setTableId(Long tableId) { this.tableId = tableId; }

    public Long getIdAgency() { return idAgency; }
    public void setIdAgency(Long idAgency) { this.idAgency = idAgency; }

    public String getEslTagMac() { return eslTagMac; }
    public void setEslTagMac(String eslTagMac) { this.eslTagMac = eslTagMac; }

    public String getEslApUrl() { return eslApUrl; }
    public void setEslApUrl(String eslApUrl) { this.eslApUrl = eslApUrl; }
}
