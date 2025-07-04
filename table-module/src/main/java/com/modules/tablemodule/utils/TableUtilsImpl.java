package com.modules.tablemodule.utils;

import com.modules.common.dto.TableDto;
import com.modules.common.finders.TableUtils;
import com.modules.tablemodule.model.TableEntityJpa;
import com.modules.tablemodule.repository.TableEntityRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class TableUtilsImpl implements TableUtils {
    @Autowired
    private TableEntityRepository tableEntityRepository;

    @Override
    public Optional<TableDto> findByIdAndIdAgencyAndDeleted(long idTable, long idAgency) {
        Optional<TableEntityJpa> tableEntityJpa = tableEntityRepository.findByIdAndIdAgencyAndDeleted(idTable, idAgency, false);
        return tableEntityJpa.map(TableDto::new);
    }

    @Override
    public TableDto save(TableDto tableDto, long idAgency) {
        TableEntityJpa tableEntityJpa = tableEntityRepository.save(new TableEntityJpa(tableDto, idAgency));
        return new TableDto(tableEntityJpa);
    }
}
