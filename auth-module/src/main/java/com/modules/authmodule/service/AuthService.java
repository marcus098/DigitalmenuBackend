package com.modules.authmodule.service;

import com.modules.authmodule.model.AgencyJpa;
import com.modules.authmodule.model.User;
import com.modules.authmodule.repository.AgencyRepository;
import com.modules.authmodule.repository.UserRepository;
import com.modules.authmodule.request.SignupAdmin;
import com.modules.authmodule.request.SignupAgency;
import com.modules.authmodule.request.SignupWaiter;
import com.modules.common.dto.StyleDto;
import com.modules.common.dto.UserDto;
import com.modules.common.email.EmailRequest;
import com.modules.common.email.EmailService;
import com.modules.common.email.LanguageEmail;
import com.modules.common.finders.FileUtils;
import com.modules.common.finders.StyleUtils;
import com.modules.common.finders.WaiterUtils;
import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.common.model.db.Waiter;
import com.modules.common.model.enums.Role;
import com.modules.common.responses.AuthResponse;
import com.modules.common.responses.DataResponse;
import com.modules.common.utilities.Utilities;
import com.modules.servletconfiguration.security.AuthenticatedUserProvider;
import com.modules.servletconfiguration.security.JwtService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

import static com.modules.common.utilities.Constants.*;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    private AgencyRepository agencyRepository;
    @Value("${client.url}")
    private String CLIENT_BASE_URL;
    @Autowired
    private UserService userService;
    @Autowired
    private EmailService emailService;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private Utilities utilities;
    @Autowired
    private AuthenticatedUserProvider authUserProvider;
    @Autowired
    private StyleUtils styleUtils;
    @Autowired
    private FileUtils fileUtils;
    @Autowired
    private WaiterUtils waiterUtils;

    AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public AuthResponse checkToken(String token) {
        try {
            long idUser = authUserProvider.getUserId();
            // Carica i dettagli dell'utente
            User userDetails = userService.loadUserById(idUser);
            AgencyJpa agencyJpa = agencyRepository.findByIdAndDeleted(userDetails.getIdAgency(), false).orElseThrow();
            if (!agencyJpa.isTrial() && agencyJpa.getBillingEndAt().isBefore(OffsetDateTime.now())) {
                //return null; // todo attivare in prod
            }
            UserDto userDto = new UserDto(
                    userDetails.getId(),
                    userDetails.getUsername(),
                    userDetails.getEmail(),
                    userDetails.getRole(),
                    userDetails.getPhoneNumber(),
                    userDetails.getIdAgency(),
                    userDetails.isEmailConfirmed(),
                    userDetails.isNumberConfirmed(),
                    userDetails.getOtpConfirmEmail(),
                    userDetails.getOtpConfirmNumber(),
                    userDetails.getGeneralOtp(),
                    userDetails.getName(),
                    userDetails.getSurname()
            );
            // Verifica se il token è valido
            if (!jwtService.isTokenValid(token, userDto)) {
                return null;
            }

            // Controlla se il token è vicino alla scadenza e genera un nuovo token se necessario
            String newToken = "";
            if (jwtService.isTokenExpiringSoon(token)) {
                newToken = jwtService.generateToken(userDto);
            }
            long days = 0;
            // Crea una risposta che includa un eventuale nuovo token
            if (!newToken.isEmpty()) {
                Date date = jwtService.extractExpiration(newToken);
                Date now = new Date();
                days = (now.getTime() - date.getTime()) / (24 * 60 * 60 * 1000);
            }
            // todo gestire log di accesso o di errore su mongo
            int value = check(userDto.isEmailConfirmed(), userDto.getRole(), agencyJpa.isTrial(), agencyJpa.getBillingEndAt());
            return new AuthResponse(200, agencyJpa.getName(), userDto.getName(), userDto.getSurname(), userDto.getEmail(), newToken, userDto.getIdAgency(), !newToken.isEmpty(), Math.abs(days), value);
        } catch (Exception e) {
            e.printStackTrace(); // todo gestire meglio errore
            return null;
        }
    }

    private int check(boolean emailConfirmed, String role, boolean trial, OffsetDateTime endAt) {
        int value = NO_CHECK;
        if (emailConfirmed)
            value = value * IS_EMAIL_CONFIRMED;
        if (role.equals(ROLE_ADMIN)) {
            value = value * IS_ADMIN;
        } else if (role.equals(ROLE_WAITER)) {
            value = value * IS_WAITER;
        }
        if (trial && endAt.isAfter(OffsetDateTime.now())) {
            value = value * IS_TRIAL;
        }
        return value;
    }


    public DataResponse<AuthResponse> login(String username, String password) {
        try {
            Optional<User> optionalUser = userRepository.findByUsernameAndDeleted(username, false);
            if (optionalUser.isEmpty())
                optionalUser = userRepository.findByEmailAndDeleted(username, false);

            if (optionalUser.isEmpty())
                return new DataResponse<>("", new AuthResponse(401, "-1", "-1", "-1", "-1", "-1", -1, false, null, NO_CHECK));

            User user = optionalUser.get();
            if (!passwordEncoder.matches(password, user.getPassword()))
                return new DataResponse<>("", new AuthResponse(401, "-1", "-1", "-1", "-1", "-1", -1, false, null, NO_CHECK));

            if(!user.isEmailConfirmed()){
                String resendOtp = utilities.generateOtpEmailCode();
                user.setGeneralOtp(resendOtp);
                userRepository.save(user);
                return new DataResponse<>("", new AuthResponse(408, resendOtp, user.getId().toString(), "-1", "-1", "-1", -1, false, null, NO_CHECK));
            }

            if(user.getRole().equals(ROLE_WAITER) && !user.isGeneralConfirmed()){
                return new DataResponse<>("", new AuthResponse(406, "-1", "-1", "-1", "-1", "-1", -1, false, null, NO_CHECK));
            }


            AgencyJpa agencyJpa = agencyRepository.findByIdAndDeleted(user.getIdAgency(), false).orElseThrow();
            if (!agencyJpa.isTrial() && agencyJpa.getBillingEndAt().isBefore(OffsetDateTime.now())) {
                return new DataResponse<>("", new AuthResponse(402, "-1", "-1", "-1", "-1", "-1", -1, false, null, NO_CHECK));
            }

            String accessToken = jwtService.generateToken(new UserDto(
                    user.getId(),
                    user.getUsername(),
                    user.getEmail(),
                    user.getRole(),
                    user.getPhoneNumber(),
                    user.getIdAgency(),
                    user.isEmailConfirmed(),
                    user.isNumberConfirmed(),
                    user.getOtpConfirmEmail(),
                    user.getOtpConfirmNumber(),
                    user.getGeneralOtp(),
                    user.getName(),
                    user.getSurname()
            ));
            Date date = jwtService.extractExpiration(accessToken);
            Date now = new Date();
            long days = (now.getTime() - date.getTime()) / (24 * 60 * 60 * 1000);
            // todo gestire log di accesso o di errore su mongo
            // gestisco piu utenti con username il nome del locale
            //String localname = user.getUsername();
            //String[] localsString = localname.split("_@_");
            //if (localsString.length > 1) {
            //    localname = localsString[0];
            //}
            int value = check(user.isEmailConfirmed(), user.getRole(), agencyJpa.isTrial(), agencyJpa.getBillingEndAt());
            return new DataResponse<>(accessToken, new AuthResponse(200, agencyJpa.getName(), user.getName(), user.getSurname(), user.getEmail(), accessToken, user.getIdAgency(), true, Math.abs(days), value));

        } catch (Exception e) {
            ErrorLog.logger.error("Errore login utente con username o email " + username, e);
            return new DataResponse<>("", new AuthResponse(400, "-1", "-1", "-1", "-1", "-1", -1, false, null, NO_CHECK));
        }
    }

    /*
        Devo creare prima l'azienda e dopo l'utente
     */
    @Transactional
    public DataResponse<Boolean> signupAgency(SignupAgency signupAgency, MultipartFile file) {
        if (!signupAgency.validate())
            return new DataResponse<>("Dati non validi", false);
        try {
            //verifico che non esista lo stesso localname
            boolean exists = true;
            String localname = signupAgency.getLocalname();
            String[] localsPart = localname.split(" ");
            if (localsPart.length > 1) {
                localname = "";
                for (String s : localsPart) {
                    localname += s.concat("_");
                }
                localname = localname.substring(0, localname.length() - 1);
            }
            int count = 0;
            while (exists) {
                if (count > 0)
                    localname = localname + count;
                exists = agencyRepository.existsByNameAndDeleted(localname, false);
                count++;
            }

            AgencyJpa agency = new AgencyJpa(signupAgency.getName(), localname, utilities.generateOtpEmailCode(), utilities.generateOtpEmailCode());
            agency = agencyRepository.save(agency);

            String optEmail = utilities.generateOtpEmailCode();
            String optPhone = utilities.generateOtpEmailCode();

            User user = new User(
                    localname,
                    signupAgency.getName(),
                    signupAgency.getSurname(),
                    signupAgency.getEmail(),
                    passwordEncoder.encode(signupAgency.getPassword()),
                    Role.ROLE_ADMIN,
                    agency.getId(),
                    signupAgency.getPhone(),
                    optEmail,
                    optPhone,
                    false
            );

            user = userRepository.save(user);

            String logoUrl = "";
            if (file != null)
                logoUrl = fileUtils.uploadImageWithBucket(file, user.getIdAgency(), user.getId(), "img");

            StyleDto styleDto = styleUtils.saveNewStyle(logoUrl.isEmpty() ? new StyleDto() : new StyleDto(logoUrl), user.getIdAgency(), user.getId());

            try {
                // invio email
                Map<String, String> map = LanguageEmail.getCodeRegistration("IT");
                map.put("title", "Conferma email");
                map.put("confermaUrl", CLIENT_BASE_URL + "/confirmAccount/" + user.getId() + "/" + optEmail);
                map.put("nomeProprietario", user.getName() + " " + user.getSurname());
                EmailRequest emailRequest = new EmailRequest(user.getEmail(), "Conferma email", "");
                String response = emailService.sendEmail(emailRequest, "AdminRegisterConfirm", map);
                if (!response.equals("SUCCESS"))
                    ErrorLog.logger.warn("Errore invio email");

                // sms con twilio Todo
            } catch (Exception e) {
                ErrorLog.logger.warn("Errore invio email");
            }

        } catch (Exception e) {
            ErrorLog.logger.error("Errore creazione utente azienda ", e);
            return new DataResponse<>("Errore", false);
        }
        return new DataResponse<>("", true);
    }

    @Transactional
    public DataResponse<Boolean> signupWaiter(SignupWaiter signupWaiter) {
        AgencyJpa agencyJpa = agencyRepository.findByIdAndDeleted(signupWaiter.getIdAgency(), false).orElseThrow();
        if ((!agencyJpa.isTrial() && agencyJpa.getBillingEndAt().isBefore(OffsetDateTime.now())) || !agencyJpa.getWaitersUrl().equals(signupWaiter.getCode())) {
            return new DataResponse<>(false);
        }
        String localname = agencyJpa.getName();
        boolean exists = true;
        int count = 1;
        while (exists) {
            localname = localname + count;
            exists = agencyRepository.existsByNameAndDeleted(localname, false);
            count++;
        }

        String optEmail = utilities.generateOtpEmailCode();
        String optPhone = utilities.generateOtpEmailCode();

        User user = new User(
                localname,
                signupWaiter.getFirstName(),
                signupWaiter.getLastName(),
                signupWaiter.getEmail(),
                passwordEncoder.encode(signupWaiter.getPassword()),
                Role.ROLE_WAITER,
                agencyJpa.getId(),
                signupWaiter.getPhone(),
                optEmail,
                optPhone,
                false
        );
        user = userRepository.save(user);

        // todo ho tolto per valutare la possibilita di togliere il modulo dei waiter
        // todo gestisco tanto le conferme nella tabella users al momento
//        waiterUtils.saveNew(user.getId(), user.getIdAgency(), optEmail);

        try {
            // invio email
            Map<String, String> map = LanguageEmail.getCodeRegistration("IT");
            map.put("title", "Conferma email");
            map.put("confermaUrl", CLIENT_BASE_URL + "/confirmAccount/" + user.getId() + "/" + optEmail);
            map.put("nomeProprietario", user.getName() + " " + user.getSurname());
            EmailRequest emailRequest = new EmailRequest(user.getEmail(), "Conferma email", "");
            String response = emailService.sendEmail(emailRequest, "WaiterRegisterConfirm", map);
            if (!response.equals("SUCCESS"))
                ErrorLog.logger.warn("Errore invio email");

            // sms con twilio Todo
        } catch (Exception e) {
            ErrorLog.logger.warn("Errore invio email");
        }

        return new DataResponse<>(true);
    }

    public boolean signupOtherAdmin(SignupAdmin signupAdmin) {
        AgencyJpa agencyJpa = agencyRepository.findByIdAndDeleted(signupAdmin.getIdAgency(), false).orElseThrow();
        if ((!agencyJpa.isTrial() && agencyJpa.getBillingEndAt().isBefore(OffsetDateTime.now())) || !agencyJpa.getWaitersUrl().equals(signupAdmin.getCode())) {
            return false;
        }
        String localname = agencyJpa.getName();
        boolean exists = true;
        int count = 1;
        while (exists) {
            localname = localname + count;
            exists = agencyRepository.existsByNameAndDeleted(localname, false);
            count++;
        }
        User user = new User(
                localname,
                signupAdmin.getFirstName(),
                signupAdmin.getLastName(),
                signupAdmin.getEmail(),
                passwordEncoder.encode(signupAdmin.getPassword()),
                Role.ROLE_ADMIN,
                agencyJpa.getId(),
                signupAdmin.getPhone(),
                utilities.generateOtpEmailCode(),
                utilities.generateOtpEmailCode(),
                false
        );
        user = userRepository.save(user);
        return true;
    }

    public boolean confirmEmailOtpCodeLink(String code) {
        LocalDateTime localDateTime = utilities.decodeOtpEmailCode_getdate(code);
        if (localDateTime.plusDays(1).isAfter(LocalDateTime.now())) {
            return false;
        }
        User user = userRepository.findByOtpConfirmEmailAndDeleted(code, false).orElseThrow();
        if (user.getOtpConfirmEmail().equals(code)) {
            user.setEmailConfirmed(true);
            if (user.getRole().equals("ADMIN") && !user.isGeneralConfirmed()) {
                user.setGeneralConfirmed(true);
            }
            userRepository.save(user);
            return true;
        }
        return false;
    }

    public boolean confirmPhoneOtpCodeLink(String code) {
        LocalDateTime localDateTime = utilities.decodeOtpEmailCode_getdate(code);
        if (localDateTime.plusDays(1).isAfter(LocalDateTime.now())) {
            return false;
        }
        User user = userRepository.findByOtpConfirmNumberAndDeleted(code, false).orElseThrow();
        if (user.getOtpConfirmNumber().equals(code)) {
            user.setNumberConfirmed(true);
            userRepository.save(user);
            return true;
        }
        return false;
    }

    public boolean confirmGeneralOtpCodeLink(String code) {
        LocalDateTime localDateTime = utilities.decodeOtpEmailCode_getdate(code);
        if (localDateTime.plusDays(1).isAfter(LocalDateTime.now())) {
            return false;
        }
        User user = userRepository.findByGeneralOtpAndDeleted(code, false).orElseThrow();
        if (user.getGeneralOtp() != null && user.getGeneralOtp().equals(code)) {
            user.setGeneralOtp(null);
            userRepository.save(user);
            return true;
        }
        return false;
    }

    public boolean confirmWaiterAdminRegistration(long idInvited) {
        long idUser = authUserProvider.getUserId();
        long idAgency = authUserProvider.getAgencyId();
        //todo log
        User user = userRepository.findByIdAndIdAgencyAndDeleted(idInvited, idAgency, false).orElseThrow();
        if (user.isGeneralConfirmed()) {
            return true;
        }
        user.setGeneralConfirmed(true);
        userRepository.save(user);
        return true;
    }

    public String getUrlInvite(boolean admin) {
        long idAgency = authUserProvider.getAgencyId();
        AgencyJpa agencyJpa = agencyRepository.findByIdAndDeleted(idAgency, false).orElseThrow();
        return admin ? agencyJpa.getAdminsUrl() : agencyJpa.getWaitersUrl();
    }

}
