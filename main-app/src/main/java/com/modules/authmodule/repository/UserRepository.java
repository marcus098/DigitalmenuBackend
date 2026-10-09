package com.modules.authmodule.repository;

import com.modules.authmodule.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsernameAndDeleted(String username, boolean deleted);
    Optional<User> findByEmailAndDeleted(String email, boolean deleted);
    Optional<User> findByIdAndDeleted(long id, boolean deleted);
    Optional<User> findByIdAndIdAgencyAndDeleted(long id, long idAgency, boolean deleted);
    Optional<User> findByOtpConfirmEmailAndDeleted(String otp, boolean deleted);
    Optional<User> findByOtpConfirmNumberAndDeleted(String otp, boolean deleted);
    Optional<User> findByGeneralOtpAndDeleted(String otp, boolean deleted);
    List<User> findAllByIdAgencyAndDeletedAndRole(long idAgency, boolean deleted, String role);
    Optional<User> findByIdAndGeneralOtpAndEmailConfirmedAndDeleted(long id, String otp, boolean confirmed, boolean deleted);

    // ── superadmin ──
    /** Qualsiasi utente con questa email, anche eliminato (bootstrap del superadmin). */
    Optional<User> findFirstByEmail(String email);
    /** Tutti gli utenti di un locale, anche eliminati, dal più vecchio. */
    List<User> findAllByIdAgencyOrderByCreatedAtAscIdAsc(Long idAgency);
    /** Utenti attivi con un ruolo, dal più vecchio (per trovare l'admin "principale" di ogni locale). */
    List<User> findAllByRoleAndDeletedOrderByCreatedAtAscIdAsc(String role, boolean deleted);
    Optional<User> findFirstByIdAgencyAndRoleAndDeletedOrderByCreatedAtAscIdAsc(Long idAgency, String role, boolean deleted);
}
