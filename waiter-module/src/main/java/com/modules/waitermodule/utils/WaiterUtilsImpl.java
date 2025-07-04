package com.modules.waitermodule.utils;

import com.modules.common.dto.WaiterDto;
import com.modules.common.finders.WaiterUtils;
import com.modules.waitermodule.model.WaiterJpa;
import com.modules.waitermodule.repository.WaiterRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class WaiterUtilsImpl implements WaiterUtils {
    @Autowired
    private WaiterRepository waiterRepository;


    @Override
    public boolean saveConfirm(long id, boolean confirm, long idAgency) {
        WaiterJpa waiterJpa = waiterRepository.findByIdAndIdAgencyAndDeleted(id, idAgency, false).orElseThrow();
        waiterJpa.setConfirmed(confirm);
        waiterRepository.save(waiterJpa);
        return true;
    }

    @Override
    public long saveNew(long idUser, long idAgency, String code) {
        WaiterJpa waiterJpa = waiterRepository.save(new WaiterJpa(idAgency, idUser, code));
        return waiterJpa.getId();
    }


    @Override
    public Optional<WaiterDto> findByIdAndIdAgencyAndDeleted(long id, long idAgency) {
        return Optional.empty();
    }
}
