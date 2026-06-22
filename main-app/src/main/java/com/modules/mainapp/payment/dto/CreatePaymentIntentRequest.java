package com.modules.mainapp.payment.dto;

public class CreatePaymentIntentRequest {
    private String comandId;
    private long amountCents;
    private Long idTable;
    private String currency;

    public String getComandId() { return comandId; }
    public void setComandId(String comandId) { this.comandId = comandId; }
    public long getAmountCents() { return amountCents; }
    public void setAmountCents(long amountCents) { this.amountCents = amountCents; }
    public Long getIdTable() { return idTable; }
    public void setIdTable(Long idTable) { this.idTable = idTable; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}
