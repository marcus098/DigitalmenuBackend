package com.modules.tablemodule.requests;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class UpdateTables {
    private long id;
    private String name;
    private List<UpdateTableRow> tables;

    public UpdateTables(long id, String name, List<UpdateTableRow> tables) {
        this.id = id;
        this.name = name;
        this.tables = tables;
    }

    @Override
    public String toString() {
        return "UpdateTables{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", tables=" + tables +
                '}';
    }
}
