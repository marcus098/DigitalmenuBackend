package com.modules.ordermodule.model;

import com.modules.common.model.ComandFromWaiter;
import com.modules.common.model.ComandType;
import com.modules.common.model.Order;
import com.modules.common.model.enums.ComandStatus;
import com.modules.common.model.enums.ComandWaiterType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Setter
@Getter
@Document(collection = "comand")
public class ComandFromWaiterJpa extends ComandFromWaiter {

    public ComandFromWaiterJpa() {

    }

    //table
    public ComandFromWaiterJpa(long idAgency, long idTable, long idWaiter, ComandStatus status, List<Order> orders) {
        super(idAgency, idTable, idWaiter, status, orders);
    }

    //take away (asporto)
    public ComandFromWaiterJpa(long idAgency, long idWaiter, ComandStatus status, List<Order> orders, String name, String time, String phone) {
        super(idAgency, idWaiter, status, orders, name, time, phone);
    }

    //home (domicilio)
    public ComandFromWaiterJpa(long idAgency, long idWaiter, ComandStatus status, List<Order> orders, String name, String address, String time, String phone) {
        super(idAgency, idWaiter, status, orders, name, address, time, phone);
    }

    //table
    public ComandFromWaiterJpa(long idAgency, long idTable, long idWaiter, ComandStatus status, List<Order> orders, String tableSessionId) {
        super(idAgency, idTable, idWaiter, status, orders, tableSessionId);
    }

    @Id
    @Override
    public String getId() {
        return super.getId();
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
    public String getPhone() {
        return super.getPhone();
    }

    @Override
    public String getAddress() {
        return super.getAddress();
    }

    @Override
    public ComandWaiterType getComandWaiterType() {
        return super.getComandWaiterType();
    }

    @Override
    public void setComandWaiterType(ComandWaiterType comandWaiterType) {
        super.setComandWaiterType(comandWaiterType);
    }

    @Override
    public void setTime(String time) {
        super.setTime(time);
    }

    @Override
    public void setPhone(String phone) {
        super.setPhone(phone);
    }

    @Override
    public void setAddress(String address) {
        super.setAddress(address);
    }

    @Override
    public String getTime() {
        return super.getTime();
    }

    @Override
    public String getName() {
        return super.getName();
    }

    @Override
    public void setName(String name) {
        super.setName(name);
    }

    @Override
    public LocalDateTime getCreatedAt() {
        return super.getCreatedAt();
    }

    @Override
    public Long getIdAgency() {
        return super.getIdAgency();
    }

    @Override
    public long getIdTable() {
        return super.getIdTable();
    }

    @Override
    public long getIdWaiter() {
        return super.getIdWaiter();
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
    public void setTableSessionId(String tableSessionId) {
        super.setTableSessionId(tableSessionId);
    }

    @Override
    public void setType(ComandType type) {
        super.setType(type);
    }

    @Override
    public void setIdTable(long idTable) {
        super.setIdTable(idTable);
    }

    @Override
    public void setIdWaiter(long idWaiter) {
        super.setIdWaiter(idWaiter);
    }

}
