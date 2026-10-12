package com.modules.pushmodule.repository;

import com.modules.pushmodule.model.PushTokenJpa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static com.modules.common.utilities.Constants.ROLE_ADMIN;
import static com.modules.common.utilities.Constants.ROLE_WAITER;

public interface PushTokenRepository extends JpaRepository<PushTokenJpa, Long> {

    Optional<PushTokenJpa> findByToken(String token);

    /**
     * Token dei dispositivi dello staff del locale: titolare e camerieri approvati, non eliminati e ancora
     * appartenenti al locale (un cameriere rimosso o spostato smette subito di ricevere le notifiche).
     */
    @Query("SELECT p.token FROM PushTokenJpa p, User u " +
            "WHERE u.id = p.userId AND p.idAgency = :idAgency AND u.idAgency = :idAgency AND u.deleted = false " +
            "AND (u.role = :adminRole OR (u.role = :waiterRole AND u.generalConfirmed = true))")
    List<String> findStaffTokens(@Param("idAgency") long idAgency,
                                 @Param("adminRole") String adminRole,
                                 @Param("waiterRole") String waiterRole);

    default List<String> findStaffTokens(long idAgency) {
        return findStaffTokens(idAgency, ROLE_ADMIN, ROLE_WAITER);
    }

    @Modifying
    @Transactional
    @Query("DELETE FROM PushTokenJpa p WHERE p.token IN :tokens")
    int deleteByTokens(@Param("tokens") Collection<String> tokens);

    @Modifying
    @Transactional
    @Query("DELETE FROM PushTokenJpa p WHERE p.token = :token AND p.userId = :userId")
    int deleteByTokenAndUserId(@Param("token") String token, @Param("userId") long userId);
}
