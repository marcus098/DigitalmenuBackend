package com.modules.authmodule.request;

import com.modules.common.model.Request;

public class SignupAgency implements Request {
    private String name;
    private String surname;
    private String email;
    private String phone;
    private String password;
    private String localname;

    public SignupAgency(){

    }

    public String getSurname() {
        return surname;
    }

    public String getPassword() {
        return password;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }

    public String getLocalname() {
        return localname;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public void setSurname(String surname) {
        this.surname = surname;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setLocalname(String localname) {
        this.localname = localname;
    }

    public boolean validate(){
        return !name.isEmpty() && !surname.isEmpty() && (!email.isEmpty() || !phone.isEmpty()) && !localname.isEmpty() && !password.isEmpty() && password.length() > 6;
    }

    @Override
    public String toString() {
        return "SignupAgency{" +
                "name='" + name + '\'' +
                ", surname='" + surname + '\'' +
                ", email='" + email + '\'' +
                ", phone='" + phone + '\'' +
                ", password='" + password + '\'' +
                ", localname='" + localname + '\'' +
                '}';
    }
}
