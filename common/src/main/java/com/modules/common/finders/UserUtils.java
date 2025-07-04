package com.modules.common.finders;

import com.modules.common.dto.UserDto;

public interface UserUtils {
    UserDto loadUserByEmail(String email);
    UserDto loadUserByUsername(String username);

}
