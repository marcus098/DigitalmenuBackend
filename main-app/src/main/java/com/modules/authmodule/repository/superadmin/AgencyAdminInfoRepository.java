package com.modules.authmodule.repository.superadmin;

import com.modules.authmodule.model.superadmin.AgencyAdminInfoJpa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AgencyAdminInfoRepository extends JpaRepository<AgencyAdminInfoJpa, Long> {

    /** [idAgency, count] dei tavoli non eliminati, per tutti i locali (una sola query per la lista superadmin). */
    @Query("select t.idAgency, count(t) from TableEntityJpa t where t.deleted = false group by t.idAgency")
    List<Object[]> countTablesByAgency();

    /** [idAgency, count] degli utenti non eliminati con il ruolo dato, per tutti i locali. */
    @Query("select u.idAgency, count(u) from User u where u.deleted = false and u.role = :role and u.idAgency is not null group by u.idAgency")
    List<Object[]> countUsersByAgencyAndRole(@Param("role") String role);
}
