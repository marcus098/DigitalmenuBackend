package com.modules.webfluxmodule.models.db;
import com.modules.common.model.db.Card;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Table("cards")
public class CardR2dbc extends Card {
    public CardR2dbc() {
        super();
    }

    @Id
    @Override
    public long getId() {
        return super.getId();
    }

    @Override
    public long getIdAgency() {
        return super.getIdAgency();
    }

    @Override
    public void setIdAgency(long idAgency) {
        super.setIdAgency(idAgency);
    }

    @Override
    public OffsetDateTime getCreatedAt() {
        return super.getCreatedAt();
    }

    @Override
    public void setCreatedAt(OffsetDateTime createdAt) {
        super.setCreatedAt(createdAt);
    }

    @Override
    public OffsetDateTime getDeletedAt() {
        return super.getDeletedAt();
    }

    @Override
    public void setDeletedAt(OffsetDateTime deletedAt) {
        super.setDeletedAt(deletedAt);
    }

    @Override
    public boolean isDeleted() {
        return super.isDeleted();
    }

    @Override
    public void setDeleted(boolean deleted) {
        super.setDeleted(deleted);
    }

    @Override
    public String toString() {
        return super.toString();
    }
}
