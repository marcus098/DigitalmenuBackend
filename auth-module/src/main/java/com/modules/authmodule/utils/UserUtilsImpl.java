package com.modules.authmodule.utils;

import com.modules.authmodule.model.User;
import com.modules.authmodule.repository.UserRepository;
import com.modules.common.dto.UserDto;
import com.modules.common.finders.UserUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class UserUtilsImpl implements UserUtils {
    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDto loadUserByEmail(String email) {
        return convertToDto(userRepository.findByEmailAndDeleted(email, false));
    }

    @Override
    public UserDto loadUserByUsername(String username) {
        return convertToDto(userRepository.findByUsernameAndDeleted(username, false));
    }

    private UserDto convertToDto(Optional<User> user) {
        if(user.isPresent()) {
            User u = user.get();
            return new UserDto(
                    u.getId(),
                    u.getUsername(),
                    u.getEmail(),
                    u.getRole(),
                    u.getPhoneNumber(),
                    u.getIdAgency(),
                    u.isEmailConfirmed(),
                    u.isNumberConfirmed(),
                    u.getOtpConfirmEmail(),
                    u.getOtpConfirmNumber(),
                    u.getGeneralOtp(),
                    u.getName(),
                    u.getSurname()
            );
        }
        return null;
    }
}
