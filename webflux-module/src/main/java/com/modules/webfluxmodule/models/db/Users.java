package com.modules.webfluxmodule.models.db;

import com.modules.common.model.enums.Role;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

@Table(name = "users")
public class Users implements UserDetails {
    @Id
    private Long id;
    private String username;
    private String email;
    private String password;
    private String role;
    private Long id_agency;
    private OffsetDateTime created_at;
    private OffsetDateTime deleted_at;
    private boolean deleted;
    private String phone_number;
    private boolean email_confirmed;
    private boolean number_confirmed;
    private String otp_confirm_email;
    private String otp_confirm_number;
    private String general_otp;
    private String name;
    private String surname;

    public Users() {

    }

    public Users(String username, String name, String surname, String email, String password, Role role, Long idAgency, String phoneNumber, String otpConfirmEmail, String otpConfirmNumber) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.role = role.toString();
        this.id_agency = idAgency;
        this.created_at = OffsetDateTime.now();
        this.deleted = false;
        this.phone_number = phoneNumber;
        this.email_confirmed = false;
        this.number_confirmed = false;
        this.otp_confirm_email = otpConfirmEmail;
        this.otp_confirm_number = otpConfirmNumber;
        this.name = name;
        this.surname = surname;
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

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Long getId_agency() {
        return id_agency;
    }

    public void setId_agency(Long id_agency) {
        this.id_agency = id_agency;
    }

    public OffsetDateTime getCreated_at() {
        return created_at;
    }

    public void setCreated_at(OffsetDateTime created_at) {
        this.created_at = created_at;
    }

    public OffsetDateTime getDeleted_at() {
        return deleted_at;
    }

    public void setDeleted_at(OffsetDateTime deleted_at) {
        this.deleted_at = deleted_at;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public String getPhone_number() {
        return phone_number;
    }

    public void setPhone_number(String phone_number) {
        this.phone_number = phone_number;
    }

    public boolean isEmail_confirmed() {
        return email_confirmed;
    }

    public void setEmail_confirmed(boolean email_confirmed) {
        this.email_confirmed = email_confirmed;
    }

    public boolean isNumber_confirmed() {
        return number_confirmed;
    }

    public void setNumber_confirmed(boolean number_confirmed) {
        this.number_confirmed = number_confirmed;
    }

    public String getOtp_confirm_email() {
        return otp_confirm_email;
    }

    public void setOtp_confirm_email(String otp_confirm_email) {
        this.otp_confirm_email = otp_confirm_email;
    }

    public String getOtp_confirm_number() {
        return otp_confirm_number;
    }

    public void setOtp_confirm_number(String otp_confirm_number) {
        this.otp_confirm_number = otp_confirm_number;
    }

    public String getGeneral_otp() {
        return general_otp;
    }

    public void setGeneral_otp(String general_otp) {
        this.general_otp = general_otp;
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
        return "Users{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", password='" + password + '\'' +
                ", role='" + role + '\'' +
                ", id_agency=" + id_agency +
                ", created_at=" + created_at +
                ", deleted_at=" + deleted_at +
                ", deleted=" + deleted +
                ", phone_number='" + phone_number + '\'' +
                ", email_confirmed=" + email_confirmed +
                ", number_confirmed=" + number_confirmed +
                ", otp_confirm_email='" + otp_confirm_email + '\'' +
                ", otp_confirm_number='" + otp_confirm_number + '\'' +
                ", general_otp='" + general_otp + '\'' +
                ", name='" + name + '\'' +
                ", surname='" + surname + '\'' +
                '}';
    }
}
