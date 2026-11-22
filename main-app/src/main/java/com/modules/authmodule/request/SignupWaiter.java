package com.modules.authmodule.request;

public class SignupWaiter {
    private long idAgency;
    private String firstName;
    private String lastName;
    private String email;
    private String password;
    private String phone;
    private String code;
    /** ROLE_WAITER (predefinito) o ROLE_DELIVERY (rider). Nessun altro ruolo ammesso. */
    private String role;

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    /** Token Google reCAPTCHA v3 (azione "signup_staff"). Mai loggato. */
    private String recaptchaToken;

    public String getRecaptchaToken() { return recaptchaToken; }
    public void setRecaptchaToken(String recaptchaToken) { this.recaptchaToken = recaptchaToken; }

    public SignupWaiter(){

    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public long getIdAgency() {
        return idAgency;
    }

    public void setIdAgency(long idAgency) {
        this.idAgency = idAgency;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}
