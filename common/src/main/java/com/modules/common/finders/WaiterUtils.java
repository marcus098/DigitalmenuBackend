package com.modules.common.finders;

import com.modules.common.dto.WaiterDto;

import java.util.Optional;

public interface WaiterUtils {
    boolean saveConfirm(long id, boolean confirm, long idAgency);
    long saveNew(long idUser, long idAgency, String code);
    Optional<WaiterDto> findByIdAndIdAgencyAndDeleted(long id, long idAgency);
}
