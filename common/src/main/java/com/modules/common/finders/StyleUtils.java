package com.modules.common.finders;

import com.modules.common.dto.StyleDto;

public interface StyleUtils {
    StyleDto saveNewStyle(StyleDto styleDto, long idAgency, long idUser);
}
