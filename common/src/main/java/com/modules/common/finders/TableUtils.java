package com.modules.common.finders;

import com.modules.common.dto.TableDto;

import java.util.Optional;

public interface TableUtils {
    Optional<TableDto> findByIdAndIdAgencyAndDeleted(long idTable, long idAgency);
    Optional<TableDto> findById(long id);
    TableDto save(TableDto tableDto, long iAgency);

}
