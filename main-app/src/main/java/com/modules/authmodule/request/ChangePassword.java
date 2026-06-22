package com.modules.authmodule.request;

import com.modules.common.model.Request;

public class ChangePassword implements Request {
    private String oldPassword;
    private String newPassword;

    public ChangePassword(String oldPassword, String newPassword) {
        this.oldPassword = oldPassword;
        this.newPassword = newPassword;
    }

    public ChangePassword() {}

    public String getOldPassword() {
        return oldPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setOldPassword(String oldPassword) {
        this.oldPassword = oldPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    @Override
    public String toString() {
        return "ChangePassword{" +
                "oldPassword='" + oldPassword + '\'' +
                ", newPassword='" + newPassword + '\'' +
                '}';
    }

    @Override
    public boolean validate() {
        boolean flag = oldPassword != null && newPassword != null && !oldPassword.trim().isEmpty() && !newPassword.trim().isEmpty() && newPassword.trim().length() > 6;
        if(!flag) return false;
        setNewPassword(newPassword.trim());
        setOldPassword(oldPassword.trim());
        return true;
    }
}
