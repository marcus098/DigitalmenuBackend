package com.modules.authmodule.request;

import com.modules.common.model.Request;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

@Getter
@Setter
public class UpdateData implements Request {
    @NotNull
    private String name;
    @NotNull
    private String email;
    private String phone;
    private String address;

    public UpdateData(){

    }

    @Override
    public boolean validate() {
        return !name.trim().isEmpty() && !email.trim().isEmpty();
    }

    @Override
    public String toString() {
        return "UpdateData{" +
                "name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", phone='" + phone + '\'' +
                ", address='" + address + '\'' +
                '}';
    }
}
