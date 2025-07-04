package com.modules.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class UserDto {
    private Long id;
    private String username;
    private String email;
    private String role;
    @JsonIgnore
    private String phoneNumber;
    @JsonIgnore
    private long idAgency;
    @JsonIgnore
    private boolean emailConfirmed;
    @JsonIgnore
    private boolean numberConfirmed;
    @JsonIgnore
    private String otpConfirmEmail;
    @JsonIgnore
    private String otpConfirmNumber;
    @JsonIgnore
    private String generalOtp;
    private String name;
    private String surname;

    public UserDto(){

    }

    public UserDto(Long id, String username, String email, String role, String phoneNumber, long idAgency, boolean emailConfirmed, boolean numberConfirmed, String otpConfirmEmail, String otpConfirmNumber, String generalOtp, String name, String surname) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.role = role;
        this.phoneNumber = phoneNumber;
        this.idAgency = idAgency;
        this.emailConfirmed = emailConfirmed;
        this.numberConfirmed = numberConfirmed;
        this.otpConfirmEmail = otpConfirmEmail;
        this.otpConfirmNumber = otpConfirmNumber;
        this.generalOtp = generalOtp;
        this.name = name;
        this.surname = surname;
    }

    public long getIdAgency() {
        return idAgency;
    }

    public void setIdAgency(long idAgency) {
        this.idAgency = idAgency;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public boolean isEmailConfirmed() {
        return emailConfirmed;
    }

    public void setEmailConfirmed(boolean emailConfirmed) {
        this.emailConfirmed = emailConfirmed;
    }

    public boolean isNumberConfirmed() {
        return numberConfirmed;
    }

    public void setNumberConfirmed(boolean numberConfirmed) {
        this.numberConfirmed = numberConfirmed;
    }

    public String getOtpConfirmEmail() {
        return otpConfirmEmail;
    }

    public void setOtpConfirmEmail(String otpConfirmEmail) {
        this.otpConfirmEmail = otpConfirmEmail;
    }

    public String getOtpConfirmNumber() {
        return otpConfirmNumber;
    }

    public void setOtpConfirmNumber(String otpConfirmNumber) {
        this.otpConfirmNumber = otpConfirmNumber;
    }

    public String getGeneralOtp() {
        return generalOtp;
    }

    public void setGeneralOtp(String generalOtp) {
        this.generalOtp = generalOtp;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSurname() {
        return surname;
    }

    public void setSurname(String surname) {
        this.surname = surname;
    }

    @Override
    public String toString() {
        return "UserDto{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", role='" + role + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                ", emailConfirmed=" + emailConfirmed +
                ", numberConfirmed=" + numberConfirmed +
                ", otpConfirmEmail='" + otpConfirmEmail + '\'' +
                ", otpConfirmNumber='" + otpConfirmNumber + '\'' +
                ", generalOtp='" + generalOtp + '\'' +
                ", name='" + name + '\'' +
                ", surname='" + surname + '\'' +
                '}';
    }
}
