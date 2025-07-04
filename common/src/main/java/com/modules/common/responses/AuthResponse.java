package com.modules.common.responses;

public class AuthResponse {
    private final long idAgency;
    private int status;
    private final String localname;
    private final String name;
    private final String surname;
    private final String email;
    private final String accessToken;
    private final boolean isNew;
    private final Long time;
    private final int controlsVariable;

    public AuthResponse(int status, String localname, String name, String surname, String email, String accessToken, long idAgency, boolean isNew, Long time, int controlsVariable) {
        this.status = status;
        this.localname = localname;
        this.name = name;
        this.surname = surname;
        this.email = email;
        this.accessToken = accessToken;
        this.idAgency = idAgency;
        this.isNew = isNew;
        this.time = time;
        this.controlsVariable = controlsVariable;
    }

    /*public AuthResponse(){

    }*/

    public int getControlsVariable() {
        return controlsVariable;
    }

    public boolean check(int value){
        return controlsVariable % value == 0;
    }

    public Long getTime() {
        return time;
    }

    public boolean isNew() {
        return isNew;
    }

    public long getIdAgency() {
        return idAgency;
    }

    public String getSurname() {
        return surname;
    }

    public String getLocalname() {
        return localname;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "AuthResponse{" +
                "status=" + status +
                ", localname='" + localname + '\'' +
                ", name='" + name + '\'' +
                ", surname='" + surname + '\'' +
                ", email='" + email + '\'' +
                ", accessToken='" + accessToken + '\'' +
                '}';
    }
}
