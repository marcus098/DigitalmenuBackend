package com.modules.authmodule.service;

import com.modules.authmodule.model.AgencyJpa;
import com.modules.authmodule.model.User;
import com.modules.authmodule.repository.AgencyRepository;
import com.modules.authmodule.repository.UserRepository;
import com.modules.authmodule.request.ChangePassword;
import com.modules.authmodule.request.UpdateData;
import com.modules.common.dto.WaiterDto;
import com.modules.common.email.EmailRequest;
import com.modules.common.email.EmailService;
import com.modules.common.email.LanguageEmail;
import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.common.responses.AuthResponse;
import com.modules.common.responses.DataResponse;
import com.modules.common.utilities.Utilities;
import com.modules.servletconfiguration.security.AuthenticatedUserProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.modules.common.utilities.Constants.ROLE_ADMIN;
import static com.modules.common.utilities.Constants.ROLE_WAITER;

@Service
public class UserService implements UserDetailsService {
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EmailService emailService;
    @Autowired
    private AgencyRepository agencyRepository;
    @Autowired
    private AuthenticatedUserProvider authUserProvider;
    @Autowired
    private Utilities utilities;
    @Value("${client.url}")
    private String CLIENT_BASE_URL;

    private final PasswordEncoder passwordEncoder;

    public UserService(@Lazy PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    public String getAgencyNameById(long id) {
        AgencyJpa agencyJpa = agencyRepository.findByIdAndDeleted(id, false).orElseThrow();
        // todo effettuare controlli. Esempio se non ha pagato mando null
        return agencyJpa.getName();
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Optional<User> optionalUser = userRepository.findByUsernameAndDeleted(username, false);
        if (optionalUser.isEmpty()) {
            throw new UsernameNotFoundException("User not found");
        }
        return optionalUser.get();
    }

    public UserDetails loadUserByEmail(String email) throws UsernameNotFoundException {
        Optional<User> optionalUser = userRepository.findByEmailAndDeleted(email, false);
        if (optionalUser.isEmpty()) {
            throw new UsernameNotFoundException("User not found");
        }
        return optionalUser.get();
    }

    public User loadUserById(long id) throws UsernameNotFoundException {
        Optional<User> optionalUser = userRepository.findByIdAndDeleted(id, false);
        if (optionalUser.isEmpty()) {
            throw new UsernameNotFoundException("User not found");
        }
        return optionalUser.get();
    }


    public int closeAccount(long idUser, String verificationCode) {
        try {
            Optional<User> optionalUser = userRepository.findByIdAndDeleted(idUser, false);
            if (optionalUser.isEmpty())
                return 401;

            User user = optionalUser.get();
            if (!user.getGeneralOtp().equals(verificationCode))
                return 403;

            user.setDeleted(true);
            user.setDeletedAt(OffsetDateTime.now());
            // todo gestire i log su mongodb
            // todo inviare email post eliminazione
            return 200;
        } catch (Exception e) {
            ErrorLog.logger.error("Errore chiusura account per utente con id " + idUser, e);
            return 400;
        }
    }

    public int verifyOtp(String otp, long idUser) {
        try {

            Optional<User> optionalUser = userRepository.findByIdAndDeleted(idUser, false);
            if (optionalUser.isEmpty())
                return 404;

            User user = optionalUser.get();
            if (!user.getGeneralOtp().equals(otp))
                return 402;

            user.setGeneralOtp(utilities.generateCode());
            userRepository.save(user);
            // todo log per verifica otp
            return 200;
        } catch (Exception e) {
            ErrorLog.logger.error("Errore verifica otp per utente con id " + idUser, e);
            return 400;
        }
    }

    //todo gestire modifica numero di telefono

    //public int changePhone(String phone, String newPhone, long idUser, String otp) {
    //    try {
    //        // verifico che l'utente esiste nel db
    //        Optional<User> optionalUser = userRepository.findByIdAndDeleted(idUser, false);
    //        if (optionalUser.isEmpty())
    //            return 404;
    //        User user = optionalUser.get();
    //        if (!user.getGeneralOtp().equals(otp))
    //            return 402;
    //        user.setPhoneNumber(newPhone);
//
    //    } catch (Exception e) {
    //        ErrorLog.logger.error("Errore cambio numero telefono per utente " + idUser, e);
    //        return 400;
    //    }
    //    return 200;
    //}

    public int changeEmail(String oldEmail, String newEmail, long idUser) {
        try {
            // todo gestire modifica email
            return 200;
        } catch (Exception e) {
            ErrorLog.logger.error("Errore cambio email per utente con id " + idUser, e);
            return 400;
        }
    }

    public int sendOtpToPhone(long idUser) {
        return 404; // todo gestire invio otp a telefono
    }

    public int sendOtpToEmail(long idUser) {
        try {
            Optional<User> optionalUser = userRepository.findByIdAndDeleted(idUser, false);
            if (optionalUser.isEmpty())
                return 401;

            User user = optionalUser.get();
            String otp = utilities.generateCode();
            Map<String, String> map = new HashMap<>();
            map.put("code", otp);
            map.put("name", user.getName());
            map.put("surname", user.getSurname());
            EmailRequest emailRequest = new EmailRequest(user.getEmail(), "Conferma account", "test");
            String response = emailService.sendEmail(emailRequest, "CodeRegistration", map); // todo modificare template

            return 200;
        } catch (Exception e) {
            ErrorLog.logger.error("Errore invio otp a email dell'utente con id " + idUser, e);
            return 400;
        }
    }

    public int confirmEmail(long idUser, String confirmationCode) {
        try {
            Optional<User> optionalUser = userRepository.findByIdAndDeleted(idUser, false);
            if (optionalUser.isEmpty())
                return 401;

            User user = optionalUser.get();

            if (!user.getOtpConfirmEmail().equals(confirmationCode))
                return 402;

            user.setEmailConfirmed(true);
            userRepository.save(user);
            // todo log per conferma email
            return 200;
        } catch (Exception e) {
            ErrorLog.logger.error("Errore conferma email per utente con id " + idUser, e);
            return 400;
        }
    }

    public int confirmPhone(long idUser, String confirmationCode) {
        try {
            Optional<User> optionalUser = userRepository.findByIdAndDeleted(idUser, false);
            if (optionalUser.isEmpty())
                return 401;

            User user = optionalUser.get();

            if (!user.getOtpConfirmNumber().equals(confirmationCode))
                return 402;

            user.setNumberConfirmed(true);
            userRepository.save(user);
            // todo log per conferma phone
            return 200;
        } catch (Exception e) {
            ErrorLog.logger.error("Errore conferma numero per utente con id " + idUser, e);
            return 400;
        }
    }

    public int recoverPassword(String email) {
        try {
            Optional<User> optionalUser = userRepository.findByEmailAndDeleted(email, false);
            if (optionalUser.isEmpty())
                return 404;

            // todo implmenentare invio email per indirizzare alla pagina per recuperare la password
            return 200;
        } catch (Exception e) {
            ErrorLog.logger.error("Errore recupero password per utente con email " + email, e);
            return 400;
        }
    }

    public DataResponse<Boolean> updateProfile(UpdateData updateData) {
        try {
            long idUser = authUserProvider.getUserId();
            long idAgency = authUserProvider.getAgencyId();
            Optional<User> optionalUser = userRepository.findByIdAndIdAgencyAndDeleted(idUser, idAgency, false);
            if (optionalUser.isEmpty()) {
                return new DataResponse<>(null, false);
            }

            User user = optionalUser.get();

            user.setName(updateData.getName());
            if (!user.getEmail().equals(updateData.getEmail())) {
                user.setEmail(updateData.getEmail());
                user.setEmailConfirmed(false);
                user.setOtpConfirmEmail(utilities.generateCode());
            }

            if (!user.getPhoneNumber().equals(updateData.getPhone())) {
                user.setPhoneNumber(updateData.getPhone());
                user.setNumberConfirmed(false);
                user.setOtpConfirmNumber(utilities.generateCode());
            }

            userRepository.save(user);

            return new DataResponse<Boolean>(null, true);

        } catch (Exception e) {
            ErrorLog.logger.error("Errore updateProfile ", e);
            return new DataResponse<Boolean>(null, false);
        }
    }

    public DataResponse<Boolean> changePassword(ChangePassword changePassword) {
        try {
            long idUser = authUserProvider.getUserId();
            long idAgency = authUserProvider.getAgencyId();

            Optional<User> optionalUser = userRepository.findByIdAndIdAgencyAndDeleted(idUser, idAgency, false);
            if (optionalUser.isEmpty()) {
                return new DataResponse<>(null, false);
            }

            User user = optionalUser.get();
            if (!passwordEncoder.matches(changePassword.getOldPassword(), user.getPassword())) {
                return new DataResponse<>(null, false);
            }
            user.setPassword(passwordEncoder.encode(changePassword.getNewPassword()));

            userRepository.save(user);

            return new DataResponse<>(null, true);
        } catch (Exception e) {
            ErrorLog.logger.error("Errore changePassword ", e);
            return new DataResponse<>(null, false);
        }
    }

    public List<WaiterDto> getAllWaiters() {
        long idAgency = authUserProvider.getAgencyId();
        return userRepository
                .findAllByIdAgencyAndDeletedAndRole(idAgency, false, ROLE_WAITER)
                .stream()
                .map(waiter -> new WaiterDto(waiter.getId(), waiter.getName(), waiter.getSurname(), waiter.getEmail(), waiter.getPhoneNumber()))
                .collect(Collectors.toList());
    }

    public List<WaiterDto> getAllAdmins() {
        long idAgency = authUserProvider.getAgencyId();
        return userRepository
                .findAllByIdAgencyAndDeletedAndRole(idAgency, false, ROLE_ADMIN)
                .stream()
                .map(waiter -> new WaiterDto(waiter.getId(), waiter.getName(), waiter.getSurname(), waiter.getEmail(), waiter.getPhoneNumber()))
                .collect(Collectors.toList());
    }

    public boolean deleteWaiter(long id) {
        long idAgency = authUserProvider.getAgencyId();
        long idUser = authUserProvider.getUserId();
        // todo log su mongodb

        User user = userRepository.findByIdAndIdAgencyAndDeleted(id, idAgency, false).orElseThrow();
        user.setDeletedAt(OffsetDateTime.now());
        user.setDeleted(true);
        String oldEmail = user.getEmail();
        String oldName = user.getName();
        user.setEmail("deleted_" + user.getEmail() + "_" + OffsetDateTime.now());
        user.setName("deleted_" + user.getName() + "_" + OffsetDateTime.now());
        user.setUsername("deleted_" + user.getUsername() + "_" + OffsetDateTime.now());
        userRepository.save(user);

        try {
            Map<String, String> context = new HashMap<>();
            context.put("name", oldName);
            emailService.sendEmail(new EmailRequest(oldEmail, "Account disattivato", ""), "WaiterAccountDeleted", context);
        } catch (Exception e) {
            ErrorLog.logger.error("Errore invio email ", e);
        }

        return true;
    }

    public boolean resendEmailVerification(long id, String code){
        // cerco l'utente per id, generalOtp, deleted e emailConfirmed false
        User user = userRepository.findByIdAndGeneralOtpAndEmailConfirmedAndDeleted(id, code, false, false).orElseThrow();

        String otp = utilities.generateOtpEmailCode();
        user.setOtpConfirmEmail(otp);
        userRepository.save(user);

        try{
            Map<String, String> map = LanguageEmail.getCodeRegistration("IT");
            map.put("title", "Conferma email");
            map.put("confermaUrl", CLIENT_BASE_URL + "/confirmAccount/" + user.getId() + "/" + otp);
            map.put("nomeProprietario", user.getName() + " " + user.getSurname());
            EmailRequest emailRequest = new EmailRequest(user.getEmail(), "Conferma email", "");
            String response = emailService.sendEmail(emailRequest, user.getRole().equals(ROLE_ADMIN) ? "AdminRegisterConfirm" : "WaiterRegisterConfirm", map);
            if (!response.equals("SUCCESS"))
                ErrorLog.logger.warn("Errore invio email");

        }catch (Exception e){
            ErrorLog.logger.error("Errore invio email ", e);
        }

        return true;
    }

}
