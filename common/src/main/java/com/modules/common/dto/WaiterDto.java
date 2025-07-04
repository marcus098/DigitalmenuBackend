package com.modules.common.dto;

import com.fasterxml.jackson.annotation.JsonView;
import com.modules.common.model.db.Waiter;

public class WaiterDto {
    private Long id;
    private String name;
    private String surname;
    private String email;
    private String phone;
    @JsonView(Views.Updating.class)
    private String sessionUpdating;
    @JsonView(Views.Updating.class)
    private String changeType;

    public WaiterDto(){

    }

    public WaiterDto(Long id, String name, String surname, String email, String phone) {
        this.id = id;
        this.name = name;
        this.surname = surname;
        this.email = email;
        this.phone = phone;
    }

    public WaiterDto(Long id, String name, String surname, String email, String phone, String sessionUpdating, String changeType) {
        this.id = id;
        this.name = name;
        this.surname = surname;
        this.email = email;
        this.phone = phone;
        this.sessionUpdating = sessionUpdating;
        this.changeType = changeType;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public String getSurname() {
        return surname;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public void setSurname(String surname) {
        this.surname = surname;
    }

    public String getChangeType() {
        return changeType;
    }

    public String getSessionUpdating() {
        return sessionUpdating;
    }

    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }

    public void setSessionUpdating(String sessionUpdating) {
        this.sessionUpdating = sessionUpdating;
    }

    @Override
    public String toString() {
        return "WaiterDto{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", surname='" + surname + '\'' +
                ", email='" + email + '\'' +
                ", phone='" + phone + '\'' +
                ", sessionUpdating='" + sessionUpdating + '\'' +
                ", changeType='" + changeType + '\'' +
                '}';
    }
}
