package com.modules.common.responses;

public class AuthResponse {
    private long idAgency;
    private int status;
    private String localname;
    private String name;
    private String surname;
    private String email;
    private String accessToken;
    private boolean isNew;
    private Long time;

    public AuthResponse(int status, String localname, String name, String surname, String email, String accessToken, long idAgency, boolean isNew, Long time) {
        this.status = status;
        this.localname = localname;
        this.name = name;
        this.surname = surname;
        this.email = email;
        this.accessToken = accessToken;
        this.idAgency = idAgency;
        this.isNew = isNew;
        this.time = time;
    }

    public AuthResponse(){

    }

    public Long getTime() {
        return time;
    }

    public boolean isNew() {
        return isNew;
    }

    public void setNew(boolean aNew) {
        isNew = aNew;
    }

    public void setTime(Long time) {
        this.time = time;
    }

    public long getIdAgency() {
        return idAgency;
    }

    public void setIdAgency(long idAgency) {
        this.idAgency = idAgency;
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

    public void setLocalname(String localname) {
        this.localname = localname;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setSurname(String surname) {
        this.surname = surname;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
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
