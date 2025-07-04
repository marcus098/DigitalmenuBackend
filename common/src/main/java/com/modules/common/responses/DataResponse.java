package com.modules.common.responses;

public class DataResponse<T> {
    private String newAuthToken;
    private T data;

    public DataResponse(String newAuthToken, T data) {
        this.newAuthToken = newAuthToken;
        this.data = data;
    }

    public DataResponse(T data) {
        this.data = data;
        newAuthToken = null;
    }

    public T getData() {
        return data;
    }

    public String getNewAuthToken() {
        return newAuthToken;
    }

    public void setData(T data) {
        this.data = data;
    }

    public void setNewAuthToken(String newAuthToken) {
        this.newAuthToken = newAuthToken;
    }

    @Override
    public String toString() {
        return "DataResponse{" +
                "newAuthToken='" + newAuthToken + '\'' +
                ", data=" + data +
                '}';
    }
}
