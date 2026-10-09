package com.modules.mainapp.superadmin;

import com.modules.authmodule.model.superadmin.SuperadminAction;
import com.modules.authmodule.model.superadmin.SuperadminAuditJpa;
import com.modules.authmodule.repository.UserRepository;
import com.modules.authmodule.repository.superadmin.SuperadminAuditRepository;
import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.common.utilities.Constants;
import com.modules.servletconfiguration.security.ImpersonationHooks;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/** Scrittura del registro superadmin + hook usati dal filtro JWT per i token di impersonazione. */
@Service
public class SuperadminAuditService implements ImpersonationHooks {

    private final SuperadminAuditRepository auditRepository;
    private final UserRepository userRepository;

    public SuperadminAuditService(SuperadminAuditRepository auditRepository, UserRepository userRepository) {
        this.auditRepository = auditRepository;
        this.userRepository = userRepository;
    }

    public SuperadminAuditJpa record(long superadminId, String superadminEmail, Long idAgency, SuperadminAction action,
                                     String detail, String ip) {
        return auditRepository.save(new SuperadminAuditJpa(superadminId, superadminEmail, idAgency, action, detail, ip));
    }

    @Override
    public boolean isActiveSuperadmin(long superadminId) {
        return userRepository.findByIdAndDeleted(superadminId, false)
                .map(u -> Constants.ROLE_SUPERADMIN.equals(u.getRole()))
                .orElse(false);
    }

    @Async
    @Override
    public void recordImpersonatedRequest(long superadminId, String superadminEmail, Long idAgency, String method,
                                          String path, String ip) {
        try {
            record(superadminId, superadminEmail, idAgency, SuperadminAction.IMPERSONATE_REQUEST, method + " " + path, ip);
        } catch (Exception e) {
            ErrorLog.logger.error("Superadmin: errore audit richiesta impersonata {} {}", method, path, e);
        }
    }
}
