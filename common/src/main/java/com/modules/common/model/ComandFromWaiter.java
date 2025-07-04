package com.modules.common.model;

import com.modules.common.model.enums.ComandStatus;
import com.modules.common.model.enums.ComandWaiterType;

import java.time.LocalDateTime;
import java.util.List;

public class ComandFromWaiter extends Comand {
    private long idTable = -1;
    private String name;
    private long idWaiter;
    private String time;
    private String phone;
    private String address;
    private ComandWaiterType comandWaiterType;

    public ComandFromWaiter(){

    }

    // type Home
    public ComandFromWaiter(long idAgency, long idWaiter, ComandStatus status, List<Order> orders, String name, String address, String time, String phone) {
        super(idAgency, orders, status, ComandType.FROM_WAITER);
        this.idTable = -1;
        this.idWaiter = idWaiter;
        this.name = name;
        this.time = time;
        this.phone = phone;
        this.address = address;
        this.comandWaiterType = ComandWaiterType.HOME;
    }

    // type take away
    public ComandFromWaiter(long idAgency, long idWaiter, ComandStatus status, List<Order> orders, String name, String time, String phone) {
        super(idAgency, orders, status, ComandType.FROM_WAITER);
        this.idTable = -1;
        this.idWaiter = idWaiter;
        this.name = name;
        this.time = time;
        this.phone = phone;
        this.address = "";
        this.comandWaiterType = ComandWaiterType.TAKE_AWAY;
    }

    // table
    public ComandFromWaiter(long idAgency, long idTable, long idWaiter, ComandStatus status, List<Order> orders) {
        super(idAgency, orders, status, ComandType.FROM_WAITER);
        this.idTable = idTable;
        this.idWaiter = idWaiter;
        this.name = "";
        this.time = "";
        this.phone = "";
        this.address = "";
        this.comandWaiterType = ComandWaiterType.TABLE;
    }


    // type take away
    // type Home
    public ComandFromWaiter(long idAgency, long idWaiter, ComandStatus status, List<Order> orders, String name, String address, String time, String phone, String tableSessionId) {
        super(idAgency, orders, status, ComandType.FROM_WAITER, tableSessionId);
        this.idTable = -1;
        this.idWaiter = idWaiter;
        this.name = name;
        this.time = time;
        this.phone = phone;
        this.address = address;
        this.comandWaiterType = address == null ? ComandWaiterType.TAKE_AWAY : ComandWaiterType.HOME;
    }


    // table
    public ComandFromWaiter(long idAgency, long idTable, long idWaiter, ComandStatus status, List<Order> orders, String tableSessionId) {
        super(idAgency, orders, status, ComandType.FROM_WAITER, tableSessionId);
        this.idTable = idTable;
        this.idWaiter = idWaiter;
        this.name = "";
        this.time = "";
        this.phone = "";
        this.address = "";
        this.comandWaiterType = ComandWaiterType.TABLE;
    }

    public String getPhone() {
        return phone;
    }

    public String getAddress() {
        return address;
    }

    public ComandWaiterType getComandWaiterType() {
        return comandWaiterType;
    }

    public void setComandWaiterType(ComandWaiterType comandWaiterType) {
        this.comandWaiterType = comandWaiterType;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getTime() {
        return time;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public LocalDateTime getCreatedAt() {
        return super.getCreatedAt();
    }

    @Override
    public List<Order> getOrders() {
        return super.getOrders();
    }

    @Override
    public LocalDateTime getUpdatedAt() {
        return super.getUpdatedAt();
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
    public String getId() {
        return super.getId();
    }

    public long getIdTable() {
        return idTable;
    }

    public long getIdWaiter() {
        return idWaiter;
    }

    @Override
    public void setUpdatedAt(LocalDateTime updatedAt) {
        super.setUpdatedAt(updatedAt);
    }

    @Override
    public void setIdAgency(Long idAgency) {
        super.setIdAgency(idAgency);
    }

    @Override
    public void setStatus(ComandStatus status) {
        super.setStatus(status);
    }

    @Override
    public void setCreatedAt(LocalDateTime createdAt) {
        super.setCreatedAt(createdAt);
    }

    @Override
    public void setOrders(List<Order> orders) {
        super.setOrders(orders);
    }

    @Override
    public void setId(String id) {
        super.setId(id);
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

    public void setIdTable(long idTable) {
        this.idTable = idTable;
    }

    public void setIdWaiter(long idWaiter) {
        this.idWaiter = idWaiter;
    }

    @Override
    public String toString() {
        return "ComandFromWaiter{" +
                "idTable=" + idTable +
                ", idWaiter='" + idWaiter + '\'' +
                '}';
    }
}
