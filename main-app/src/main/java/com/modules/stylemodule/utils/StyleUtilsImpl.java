package com.modules.stylemodule.utils;

import com.modules.common.dto.StyleDto;
import com.modules.common.finders.StyleUtils;
import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.stylemodule.models.StyleJpa;
import com.modules.stylemodule.repository.StyleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
public class StyleUtilsImpl implements StyleUtils {
    @Autowired
    private StyleRepository styleRepository;

    @Override
    public StyleDto saveNewStyle(StyleDto styleDto, long idAgency, long idUser) {
        StyleJpa styleJpa = new StyleJpa(
                styleDto.getRestaurantName(),
                styleDto.getLogoUrl(),
                idAgency,
                OffsetDateTime.now()
        );
        styleJpa = styleRepository.save(styleJpa);
        ErrorLog.logger.info("New style created for agency {}", idAgency);
        return new StyleDto(styleJpa);
    }
}
