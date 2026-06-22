package com.modules.filemodule;

import com.modules.common.model.enums.LogOperation;

import java.time.LocalDateTime;

public class FileManagerLog<T> {
    private String id;
    private LogOperation operation;
    private T oldValue;
    private T newValue;
    private String note;
    private long idUser;
    private long idAgency;
    private LocalDateTime loggedAt;

    public FileManagerLog() {}

    public FileManagerLog(String id, LogOperation operation, T oldValue, T newValue, String note, long idUser, long idAgency, LocalDateTime loggedAt) {
        this.id = id;
        this.operation = operation;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.note = note;
        this.idUser = idUser;
        this.idAgency = idAgency;
        this.loggedAt = loggedAt;
    }

    public FileManagerLog(LogOperation operation, T oldValue, T newValue, String note, long idUser, long idAgency, LocalDateTime loggedAt) {
        this.operation = operation;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.note = note;
        this.idUser = idUser;
        this.idAgency = idAgency;
        this.loggedAt = loggedAt;
    }

    public FileManagerLog(LogOperation operation, T oldValue, T newValue, String note, long idUser, long idAgency) {
        this.operation = operation;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.note = note;
        this.idUser = idUser;
        this.idAgency = idAgency;
        this.loggedAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public LogOperation getOperation() {
        return operation;
    }

    public void setOperation(LogOperation operation) {
        this.operation = operation;
    }

    public T getOldValue() {
        return oldValue;
    }

    public void setOldValue(T oldValue) {
        this.oldValue = oldValue;
    }

    public T getNewValue() {
        return newValue;
    }

    public void setNewValue(T newValue) {
        this.newValue = newValue;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public long getIdUser() {
        return idUser;
    }

    public void setIdUser(long idUser) {
        this.idUser = idUser;
    }

    public long getIdAgency() {
        return idAgency;
    }

    public void setIdAgency(long idAgency) {
        this.idAgency = idAgency;
    }

    public LocalDateTime getLoggedAt() {
        return loggedAt;
    }

    public void setLoggedAt(LocalDateTime loggedAt) {
        this.loggedAt = loggedAt;
    }

    @Override
    public String toString() {
        return "FileManagerLog{" +
                "id='" + id + '\'' +
                ", operation=" + operation +
                ", oldValue=" + oldValue +
                ", newValue=" + newValue +
                ", note='" + note + '\'' +
                ", idUser=" + idUser +
                ", idAgency=" + idAgency +
                ", loggedAt=" + loggedAt +
                '}';
    }
}
