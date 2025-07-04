package com.modules.common.model;

import com.modules.common.model.enums.ComandStatus;

import java.time.LocalDateTime;
import java.util.List;

// Domicilio
public class ComandFromHome extends Comand{
    private String userName;
    private String userAddress;
    private LocalDateTime dateDelivery;
    private boolean payed;
    private String userPhoneNumber;

    public ComandFromHome() {

    }

    public ComandFromHome(long idAgency, String userName, String userAddress, LocalDateTime dateDelivery, boolean payed, String userPhoneNumber) {
        super(idAgency, ComandType.FROM_HOME);
        this.userName = userName;
        this.userAddress = userAddress;
        this.dateDelivery = dateDelivery;
        this.payed = payed;
        this.userPhoneNumber = userPhoneNumber;
    }

    public ComandFromHome(long idAgency, String userName, String userAddress, LocalDateTime dateDelivery, boolean payed, String userPhoneNumber, String tableSessionId) {
        super(idAgency, ComandType.FROM_HOME, tableSessionId);
        this.userName = userName;
        this.userAddress = userAddress;
        this.dateDelivery = dateDelivery;
        this.payed = payed;
        this.userPhoneNumber = userPhoneNumber;
    }

    @Override
    public ComandStatus getStatus() {
        return super.getStatus();
    }

    @Override
    public String getId() {
        return super.getId();
    }

    @Override
    public List<Order> getOrders() {
        return super.getOrders();
    }

    @Override
    public String getTableSessionId() {
        return super.getTableSessionId();
    }

    @Override
    public ComandType getType() {
        return super.getType();
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
    public Long getIdAgency() {
        return super.getIdAgency();
    }

    @Override
    public LocalDateTime getCreatedAt() {
        return super.getCreatedAt();
    }

    @Override
    public LocalDateTime getUpdatedAt() {
        return super.getUpdatedAt();
    }

    public LocalDateTime getDateDelivery() {
        return dateDelivery;
    }

    public String getUserAddress() {
        return userAddress;
    }

    public String getUserName() {
        return userName;
    }

    public String getUserPhoneNumber() {
        return userPhoneNumber;
    }

    public boolean isPayed() {
        return payed;
    }

    @Override
    public void setCreatedAt(LocalDateTime createdAt) {
        super.setCreatedAt(createdAt);
    }

    @Override
    public void setId(String id) {
        super.setId(id);
    }

    @Override
    public void setIdAgency(Long idAgency) {
        super.setIdAgency(idAgency);
    }

    @Override
    public void setOrders(List<Order> orders) {
        super.setOrders(orders);
    }

    @Override
    public void setStatus(ComandStatus status) {
        super.setStatus(status);
    }

    @Override
    public void setUpdatedAt(LocalDateTime updatedAt) {
        super.setUpdatedAt(updatedAt);
    }

    public void setDateDelivery(LocalDateTime dateDelivery) {
        this.dateDelivery = dateDelivery;
    }

    public void setPayed(boolean payed) {
        this.payed = payed;
    }

    public void setUserAddress(String userAddress) {
        this.userAddress = userAddress;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public void setUserPhoneNumber(String userPhoneNumber) {
        this.userPhoneNumber = userPhoneNumber;
    }

    @Override
    public String toString() {
        return "ComandFromHome{" +
                "userName='" + userName + '\'' +
                ", userAddress='" + userAddress + '\'' +
                ", dateDelivery=" + dateDelivery +
                ", payed=" + payed +
                ", userPhoneNumber='" + userPhoneNumber + '\'' +
                '}';
    }
}
