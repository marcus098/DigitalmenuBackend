package com.modules.authmodule.request;

import com.modules.common.model.Request;

public class SignupUser implements Request {
    private String name;
    private String surname;
    private String username;
    private String password;
    private String email;
    private String phone;

    public SignupUser(){

    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getName() {
        return name;
    }

    public String getPassword() {
        return password;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getSurname() {
        return surname;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setSurname(String surname) {
        this.surname = surname;
    }

    @Override
    public String toString() {
        return "SignupUser{" +
                "name='" + name + '\'' +
                ", surname='" + surname + '\'' +
                ", username='" + username + '\'' +
                ", password='" + password + '\'' +
                ", email='" + email + '\'' +
                '}';
    }

    @Override
    public boolean validate() {
        return surname != null && username != null && password != null && email != null && !email.trim().isEmpty() && !surname.trim().isEmpty() && !username.trim().isEmpty() && password.length() > 6;
    }
}
