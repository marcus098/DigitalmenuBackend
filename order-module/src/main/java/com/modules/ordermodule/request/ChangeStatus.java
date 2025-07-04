package com.modules.ordermodule.request;

import com.modules.common.model.Request;
import com.modules.common.model.enums.ComandStatus;

public class ChangeStatus implements Request {
    private String comandId;
    private ComandStatus status;

    public ChangeStatus(String comandId, ComandStatus comandStatus) {
        this.comandId = comandId;
        this.status = comandStatus;
    }

    public ChangeStatus(){

    }

    public String getComandId() {
        return comandId;
    }

    public ComandStatus getStatus() {
        return status;
    }

    public void setStatus(ComandStatus status) {
        this.status = status;
    }

    public void setComandId(String comandId) {
        this.comandId = comandId;
    }

    @Override
    public String toString() {
        return "ChangeStatus{" +
                "comandId='" + comandId + '\'' +
                ", comandStatus=" + status +
                '}';
    }

    @Override
    public boolean validate() {
        return comandId != null && status != null && !comandId.trim().isEmpty();
    }
}
