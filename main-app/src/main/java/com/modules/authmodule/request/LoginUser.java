package com.modules.authmodule.request;

import com.modules.common.model.Request;

public class LoginUser implements Request {
    private String emailUsername;
    private String password;

    public LoginUser(){

    }

    public LoginUser(String emailUsername, String password) {
        this.emailUsername = emailUsername;
        this.password = password;
    }

    public String getEmailUsername() {
        return emailUsername;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setEmailUsername(String emailUsername) {
        this.emailUsername = emailUsername;
    }

    @Override
    public String toString() {
        return "LoginUser{" +
                "emailUsername='" + emailUsername + '\'' +
                ", password='" + password + '\'' +
                '}';
    }

    @Override
    public boolean validate() {
        return emailUsername != null && password != null && !emailUsername.trim().isEmpty() && !password.isEmpty();
    }
}
