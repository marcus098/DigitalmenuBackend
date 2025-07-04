package com.modules.authmodule.model.dto;

import com.modules.common.dto.UserDto;

public class WaiterDto extends UserDto {
    private boolean confirmed;

    public WaiterDto(boolean confirmed) {
        this.confirmed = confirmed;
    }

    public WaiterDto(Long id, String username, String email, String role, String phoneNumber, long idAgency, boolean emailConfirmed, boolean numberConfirmed, String otpConfirmEmail, String otpConfirmNumber, String generalOtp, String name, String surname, boolean confirmed) {
        super(id, username, email, role, phoneNumber, idAgency, emailConfirmed, numberConfirmed, otpConfirmEmail, otpConfirmNumber, generalOtp, name, surname);
        this.confirmed = confirmed;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public void setConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
    }

    @Override
    public String toString() {
        return "WaiterDto{" +
                "confirmed=" + confirmed +
                '}';
    }
    
}
