package com.modules.servletconfiguration.security;

/**
 * Collegamento tra il filtro JWT e il modulo superadmin (implementato in com.modules.mainapp.superadmin), così il
 * filtro non dipende dai repository.
 */
public interface ImpersonationHooks {

    /** true se l'utente esiste, non è eliminato ed è ancora ROLE_SUPERADMIN. */
    boolean isActiveSuperadmin(long superadminId);

    /** Registra (in modo asincrono) una richiesta mutante fatta durante l'impersonazione: solo metodo e path, mai il body. */
    void recordImpersonatedRequest(long superadminId, String superadminEmail, Long idAgency, String method, String path, String ip);
}
