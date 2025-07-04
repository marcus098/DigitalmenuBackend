package com.modules.common.model.db;
/*
import com.modules.common.model.enums.Role;
import jakarta.persistence.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "users")
public class User implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String role;

    @Column(nullable = false, name = "id_agency")
    private Long idAgency;

    @Column(nullable = false, name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name="deleted_at")
    private OffsetDateTime deletedAt;

    @Column(nullable = false)
    private boolean deleted;

    @Column(name="phone_number")
    private String phoneNumber;

    @Column(name="email_confirmed")
    private boolean emailConfirmed;

    @Column(name="number_confirmed")
    private boolean numberConfirmed;

    @Column(name="otp_confirm_email")
    private String otpConfirmEmail;

    @Column(name="otp_confirm_number")
    private String otpConfirmNumber;

    @Column(name="general_otp")
    private String generalOtp;

    private String name;

    private String surname;

    public User() {

    }

    public User(String username, String name, String surname, String email, String password, Role role, Long idAgency, String phoneNumber, String otpConfirmEmail, String otpConfirmNumber) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.role = role.toString();
        this.idAgency = idAgency;
        this.createdAt = OffsetDateTime.now();
        this.deleted = false;
        this.phoneNumber = phoneNumber;
        this.emailConfirmed = false;
        this.numberConfirmed = false;
        this.otpConfirmEmail = otpConfirmEmail;
        this.otpConfirmNumber = otpConfirmNumber;
        this.name = name;
        this.surname = surname;
    }

    public String getSurname() {
        return surname;
    }

    public String getName() {
        return name;
    }

    public void setSurname(String surname) {
        this.surname = surname;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getGeneralOtp() {
        return generalOtp;
    }

    public String getOtpConfirmEmail() {
        return otpConfirmEmail;
    }

    public String getOtpConfirmNumber() {
        return otpConfirmNumber;
    }

    public void setOtpConfirmEmail(String otpConfirmEmail) {
        this.otpConfirmEmail = otpConfirmEmail;
    }

    public void setOtpConfirmNumber(String otpConfirmNumber) {
        this.otpConfirmNumber = otpConfirmNumber;
    }

    public void setGeneralOtp(String generalOtp) {
        this.generalOtp = generalOtp;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setEmailConfirmed(boolean emailConfirmed) {
        this.emailConfirmed = emailConfirmed;
    }

    public void setNumberConfirmed(boolean numberConfirmed) {
        this.numberConfirmed = numberConfirmed;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public boolean isEmailConfirmed() {
        return emailConfirmed;
    }

    public boolean isNumberConfirmed() {
        return numberConfirmed;
    }

    public Long getIdAgency() {
        return idAgency;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return !deleted;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !deleted;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return !deleted;
    }

    @Override
    public boolean isEnabled() {
        return !deleted;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        GrantedAuthority authority = new SimpleGrantedAuthority(role);
        return List.of(authority);
    }

    public String getPassword() {
        return password;
    }

    public String getRole() {
        return role;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setIdAgency(Long idAgency) {
        this.idAgency = idAgency;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public void setDeletedAt(OffsetDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setRole(String role) {
        this.role = role;
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", password='" + password + '\'' +
                ", role='" + role + '\'' +
                ", idAgency=" + idAgency +
                ", createdAt=" + createdAt +
                ", deletedAt=" + deletedAt +
                ", deleted=" + deleted +
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
*/