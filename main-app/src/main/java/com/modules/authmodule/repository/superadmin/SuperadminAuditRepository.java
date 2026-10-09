package com.modules.authmodule.repository.superadmin;

import com.modules.authmodule.model.superadmin.SuperadminAuditJpa;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SuperadminAuditRepository extends JpaRepository<SuperadminAuditJpa, Long> {

    List<SuperadminAuditJpa> findByIdAgencyOrderByCreatedAtDescIdDesc(Long idAgency, Pageable pageable);

    List<SuperadminAuditJpa> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
