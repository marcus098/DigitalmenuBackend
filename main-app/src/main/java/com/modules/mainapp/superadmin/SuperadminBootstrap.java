package com.modules.mainapp.superadmin;

import com.modules.authmodule.model.User;
import com.modules.authmodule.repository.UserRepository;
import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.common.model.enums.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Optional;

/**
 * Crea il superadmin all'avvio se app.superadmin.email / app.superadmin.password sono impostate
 * (env APP_SUPERADMIN_EMAIL / APP_SUPERADMIN_PASSWORD) e non esiste ancora un utente con quella email.
 * Non sovrascrive mai la password di un utente esistente. Nessuna registrazione pubblica per i superadmin.
 */
@Component
public class SuperadminBootstrap implements ApplicationRunner {

    public static final int MIN_PASSWORD_LENGTH = 12;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DataSource dataSource;
    private final String email;
    private final String password;

    public SuperadminBootstrap(UserRepository userRepository,
                               PasswordEncoder passwordEncoder,
                               DataSource dataSource,
                               @Value("${app.superadmin.email:}") String email,
                               @Value("${app.superadmin.password:}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.dataSource = dataSource;
        this.email = email == null ? "" : email.trim();
        this.password = password == null ? "" : password;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            bootstrap();
        } catch (Exception e) {
            // Non deve mai impedire l'avvio del backend
            ErrorLog.logger.error("SUPERADMIN: creazione non riuscita: {}", e.getMessage());
        }
    }

    /** @return true se l'utente è stato creato. */
    boolean bootstrap() {
        if (email.isEmpty() || password.isEmpty()) {
            return false;
        }
        if (!email.contains("@")) {
            ErrorLog.logger.error("SUPERADMIN: app.superadmin.email non valida, utente non creato");
            return false;
        }
        Optional<User> existing = userRepository.findFirstByEmail(email);
        if (existing.isPresent()) {
            User u = existing.get();
            if (!Role.ROLE_SUPERADMIN.name().equals(u.getRole()) || u.isDeleted()) {
                ErrorLog.logger.warn("SUPERADMIN: esiste già un utente {} con email {} (ruolo {}): nessuna modifica",
                        u.getId(), email, u.getRole());
            }
            // Mai sovrascrivere la password: si può togliere APP_SUPERADMIN_PASSWORD da .env dopo il primo avvio.
            return false;
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            ErrorLog.logger.error("SUPERADMIN: password troppo corta (minimo {} caratteri), utente non creato", MIN_PASSWORD_LENGTH);
            return false;
        }
        allowNullAgency();
        User user = new User(email, "Superadmin", "", email, passwordEncoder.encode(password),
                Role.ROLE_SUPERADMIN, null, null, null, null, true);
        user.setEmailConfirmed(true);
        user = userRepository.save(user);
        ErrorLog.logger.info("SUPERADMIN: creato utente {} ({})", user.getId(), email);
        return true;
    }

    /**
     * users.id_agency era NOT NULL e ddl-auto=update non rimuove il vincolo: lo togliamo qui (idempotente su Postgres).
     * Su altri DB / in test l'errore viene solo loggato.
     */
    void allowNullAgency() {
        if (dataSource == null) return;
        try {
            new JdbcTemplate(dataSource).execute("ALTER TABLE users ALTER COLUMN id_agency DROP NOT NULL");
        } catch (Exception e) {
            ErrorLog.logger.warn("SUPERADMIN: impossibile rendere nullable users.id_agency: {}", e.getMessage());
        }
    }
}
