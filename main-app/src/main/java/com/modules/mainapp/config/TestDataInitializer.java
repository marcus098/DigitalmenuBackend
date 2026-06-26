package com.modules.mainapp.config;

import com.modules.authmodule.model.AgencyJpa;
import com.modules.authmodule.model.User;
import com.modules.authmodule.repository.AgencyRepository;
import com.modules.authmodule.repository.UserRepository;
import com.modules.categorymodule.model.CategoryJpa;
import com.modules.categorymodule.repository.CategoryRepository;
import com.modules.common.dto.StyleDto;
import com.modules.common.finders.StyleUtils;
import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.common.model.enums.Role;
import com.modules.stylemodule.repository.StyleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class TestDataInitializer implements CommandLineRunner {

    private static final String TEST_LOCALNAME = "ristorantetest";
    private static final String TEST_EMAIL     = "test@test.com";
    private static final String TEST_PASSWORD  = "Test1234!";

    @Autowired private AgencyRepository  agencyRepository;
    @Autowired private UserRepository    userRepository;
    @Autowired private PasswordEncoder   passwordEncoder;
    @Autowired private StyleUtils        styleUtils;
    @Autowired private StyleRepository   styleRepository;
    @Autowired private CategoryRepository categoryRepository;

    @Override
    public void run(String... args) {
        AgencyJpa agency = agencyRepository.findByNameAndDeleted(TEST_LOCALNAME, false)
                .orElseGet(() -> {
                    AgencyJpa created = agencyRepository.save(new AgencyJpa(
                            "Ristorante Test",
                            TEST_LOCALNAME,
                            UUID.randomUUID().toString(),
                            UUID.randomUUID().toString()
                    ));
                    ErrorLog.logger.info("[TestData] Creato locale '{}'", TEST_LOCALNAME);
                    return created;
                });

        userRepository.findByUsernameAndDeleted(TEST_LOCALNAME, false)
                .or(() -> userRepository.findByEmailAndDeleted(TEST_EMAIL, false))
                .orElseGet(() -> {
                    User created = new User(
                            TEST_LOCALNAME,
                            "Admin",
                            "Test",
                            TEST_EMAIL,
                            passwordEncoder.encode(TEST_PASSWORD),
                            Role.ROLE_ADMIN,
                            agency.getId(),
                            "+39000000000",
                            UUID.randomUUID().toString(),
                            UUID.randomUUID().toString(),
                            true
                    );
                    created.setEmailConfirmed(true);
                    userRepository.save(created);
                    if (styleRepository.findByIdAgencyAndDeleted(agency.getId(), false).isEmpty()) {
                        styleUtils.saveNewStyle(new StyleDto(), agency.getId(), created.getId());
                    }
                    ErrorLog.logger.info("[TestData] Creato utente '{}' (password: {}) per locale '{}'",
                            TEST_EMAIL, TEST_PASSWORD, TEST_LOCALNAME);
                    return created;
                });

        seedCategorie(agency.getId());
    }

    private void seedCategorie(Long agencyId) {
        List<CategoryJpa> existing = categoryRepository
                .findAllByIdAgencyAndDeletedOrderByPositionProgressive(agencyId, false);
        if (!existing.isEmpty()) {
            ErrorLog.logger.info("[TestData] Categorie già presenti, skip.");
            return;
        }

        categoryRepository.saveAll(List.of(
                new CategoryJpa("Pizzeria",  "Pizza classiche e speciali", agencyId, 1, true, ""),
                new CategoryJpa("Panineria", "Panini e tramezzini",        agencyId, 2, true, ""),
                new CategoryJpa("Bevande",   "Bibite, succhi e acqua",     agencyId, 3, true, "")
        ));

        ErrorLog.logger.info("[TestData] Create categorie di test: Pizzeria, Panineria, Bevande");
    }
}
