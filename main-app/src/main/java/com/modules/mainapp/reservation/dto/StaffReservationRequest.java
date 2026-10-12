package com.modules.mainapp.reservation.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Prenotazione inserita/modificata dallo staff (telefonata o cliente senza prenotazione).
 * Stessi campi del flusso pubblico; in modifica (PUT) i campi null restano invariati.
 * {@code notifyCustomer}: se true e c'è un telefono, invia l'SMS di conferma (di default nessun messaggio).
 * {@code status}: solo in creazione, PENDING / CONFIRMED (default) / ARRIVED.
 */
public class StaffReservationRequest {
    private String customerName;
    private String customerPhone;
    private String customerEmail;
    private Integer partySize;
    private LocalDate reservationDate;
    private LocalTime reservationTime;
    private String specialRequests;
    private Boolean notifyCustomer;
    private String status;

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }

    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }

    public Integer getPartySize() { return partySize; }
    public void setPartySize(Integer partySize) { this.partySize = partySize; }

    public LocalDate getReservationDate() { return reservationDate; }
    public void setReservationDate(LocalDate reservationDate) { this.reservationDate = reservationDate; }

    public LocalTime getReservationTime() { return reservationTime; }
    public void setReservationTime(LocalTime reservationTime) { this.reservationTime = reservationTime; }

    public String getSpecialRequests() { return specialRequests; }
    public void setSpecialRequests(String specialRequests) { this.specialRequests = specialRequests; }

    public Boolean getNotifyCustomer() { return notifyCustomer; }
    public void setNotifyCustomer(Boolean notifyCustomer) { this.notifyCustomer = notifyCustomer; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
