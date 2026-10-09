package com.modules.authmodule.repository.superadmin;

import com.modules.authmodule.model.superadmin.AgencyNoteJpa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AgencyNoteRepository extends JpaRepository<AgencyNoteJpa, Long> {

    /** Note fissate prima, poi le più recenti. */
    List<AgencyNoteJpa> findAllByIdAgencyOrderByPinnedDescCreatedAtDescIdDesc(Long idAgency);

    long countByIdAgency(Long idAgency);

    /** [idAgency, count] per tutti i locali. */
    @Query("select n.idAgency, count(n) from AgencyNoteJpa n group by n.idAgency")
    List<Object[]> countByAgency();
}
