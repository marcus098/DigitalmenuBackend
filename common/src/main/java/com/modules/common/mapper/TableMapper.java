package com.modules.common.mapper;

import com.modules.common.dto.TableDto;
import com.modules.common.model.db.TableEntity;
import org.springframework.stereotype.Component;

@Component
public class TableMapper {
    public TableDto toDto(TableEntity tableEntity) {
        if (tableEntity == null) {
            return null;
        }

        return new TableDto(tableEntity);
    }
}
