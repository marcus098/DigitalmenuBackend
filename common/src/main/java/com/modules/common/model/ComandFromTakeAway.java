package com.modules.common.model;

import com.modules.common.model.enums.ComandStatus;

import java.time.LocalDateTime;
import java.util.List;

public class ComandFromTakeAway extends Comand{
    private String userName;
    private String userPhoneNumber;
    private LocalDateTime dateDelivery;
    private boolean payed;

    public ComandFromTakeAway() {

    }

    public ComandFromTakeAway(long idAgency, String userName, String userPhoneNumber, LocalDateTime dateDelivery, boolean payed) {
        super(idAgency, ComandType.FROM_TAKEAWAY);
        this.userName = userName;
        this.userPhoneNumber = userPhoneNumber;
        this.dateDelivery = dateDelivery;
        this.payed = payed;
    }

    public ComandFromTakeAway(long idAgency, String userName, String userPhoneNumber, LocalDateTime dateDelivery, boolean payed, String tableSessionId) {
        super(idAgency, ComandType.FROM_TAKEAWAY, tableSessionId);
        this.userName = userName;
        this.userPhoneNumber = userPhoneNumber;
        this.dateDelivery = dateDelivery;
        this.payed = payed;
    }

    @Override
    public ComandStatus getStatus() {
        return super.getStatus();
    }

    @Override
    public Long getIdAgency() {
        return super.getIdAgency();
    }

    @Override
    public List<Order> getOrders() {
        return super.getOrders();
    }

    public String getUserPhoneNumber() {
        return userPhoneNumber;
    }

    @Override
    public LocalDateTime getUpdatedAt() {
        return super.getUpdatedAt();
    }

    @Override
    public LocalDateTime getCreatedAt() {
        return super.getCreatedAt();
    }

    public String getUserName() {
        return userName;
    }

    public LocalDateTime getDateDelivery() {
        return dateDelivery;
    }

    @Override
    public String getId() {
        return super.getId();
    }

    public boolean isPayed() {
        return payed;
    }

    @Override
    public ComandType getType() {
        return super.getType();
    }

    @Override
    public String getTableSessionId() {
        return super.getTableSessionId();
    }

    @Override
    public void setTableSessionId(String tableSessionId) {
        super.setTableSessionId(tableSessionId);
    }

    @Override
    public void setType(ComandType type) {
        super.setType(type);
    }

    @Override
    public void setCreatedAt(LocalDateTime createdAt) {
        super.setCreatedAt(createdAt);
    }

    @Override
    public void setIdAgency(Long idAgency) {
        super.setIdAgency(idAgency);
    }

    @Override
    public void setId(String id) {
        super.setId(id);
    }

    public void setUserPhoneNumber(String userPhoneNumber) {
        this.userPhoneNumber = userPhoneNumber;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    @Override
    public void setUpdatedAt(LocalDateTime updatedAt) {
        super.setUpdatedAt(updatedAt);
    }

    @Override
    public void setOrders(List<Order> orders) {
        super.setOrders(orders);
    }

    public void setDateDelivery(LocalDateTime dateDelivery) {
        this.dateDelivery = dateDelivery;
    }

    @Override
    public void setStatus(ComandStatus status) {
        super.setStatus(status);
    }

    public void setPayed(boolean payed) {
        this.payed = payed;
    }

    @Override
    public String toString() {
        return "ComandFromTakeAway{" +
                "userName='" + userName + '\'' +
                ", userPhoneNumber='" + userPhoneNumber + '\'' +
                ", dateDelivery=" + dateDelivery +
                ", payed=" + payed +
                '}';
    }
}
