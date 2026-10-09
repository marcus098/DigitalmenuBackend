package com.modules.mainapp.superadmin;

import com.modules.authmodule.model.User;
import com.modules.authmodule.repository.UserRepository;
import com.modules.common.model.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SuperadminBootstrapTest {

    private static final String EMAIL = "boss@platform.it";
    private static final String PASSWORD = "a-very-long-password-123";

    private UserRepository users;
    private PasswordEncoder encoder;
    private User stored;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        encoder = new BCryptPasswordEncoder(4);
        stored = null;
        when(users.findFirstByEmail(EMAIL)).thenAnswer(i -> Optional.ofNullable(stored));
        when(users.save(any())).thenAnswer(i -> {
            stored = i.getArgument(0);
            stored.setId(1L);
            return stored;
        });
    }

    private SuperadminBootstrap bootstrap(String email, String password) {
        return new SuperadminBootstrap(users, encoder, null, email, password);
    }

    @Test
    void createsSuperadminOnce() {
        assertTrue(bootstrap(EMAIL, PASSWORD).bootstrap());

        ArgumentCaptor<User> c = ArgumentCaptor.forClass(User.class);
        verify(users).save(c.capture());
        User u = c.getValue();
        assertEquals(EMAIL, u.getUsername());
        assertEquals(EMAIL, u.getEmail());
        assertEquals(Role.ROLE_SUPERADMIN.name(), u.getRole());
        assertNull(u.getIdAgency());
        assertTrue(u.isEmailConfirmed());
        assertTrue(u.isGeneralConfirmed());
        assertTrue(encoder.matches(PASSWORD, u.getPassword()));
        assertNotEquals(PASSWORD, u.getPassword());

        // secondo avvio: esiste già -> niente
        assertFalse(bootstrap(EMAIL, PASSWORD).bootstrap());
        verify(users, times(1)).save(any());
    }

    @Test
    void neverOverwritesExistingPassword() {
        bootstrap(EMAIL, PASSWORD).bootstrap();
        String hash = stored.getPassword();

        assertFalse(bootstrap(EMAIL, "another-long-password-456").bootstrap());
        assertEquals(hash, stored.getPassword());
        verify(users, times(1)).save(any());
    }

    @Test
    void existingUserWithOtherRoleIsLeftUntouched() {
        User admin = new User("pizzeria", "Mario", "Rossi", EMAIL, "hash", Role.ROLE_ADMIN, 7L, null, null, null, true);
        stored = admin;
        assertFalse(bootstrap(EMAIL, PASSWORD).bootstrap());
        assertEquals("ROLE_ADMIN", admin.getRole());
        assertEquals("hash", admin.getPassword());
        verify(users, never()).save(any());
    }

    @Test
    void shortPasswordRefused() {
        assertFalse(bootstrap(EMAIL, "short123").bootstrap());
        verify(users, never()).save(any());
    }

    @Test
    void notConfiguredDoesNothing() {
        assertFalse(bootstrap("", "").bootstrap());
        assertFalse(bootstrap(EMAIL, "").bootstrap());
        assertFalse(bootstrap(null, null).bootstrap());
        verifyNoInteractions(users);
    }
}
