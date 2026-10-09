package com.modules.servletconfiguration.security;

import org.springframework.util.AntPathMatcher;

import java.util.List;
import java.util.Locale;

/**
 * Operazioni vietate quando un superadmin usa un locale con un token di impersonazione ("accesso come supporto").
 * Unico punto di verità, usato da {@link JwtRequestFilter}: aggiungere qui ogni nuovo endpoint sensibile.
 */
public final class ImpersonationPolicy {

    public static final String BLOCKED_MESSAGE = "Operazione non consentita durante l'accesso come supporto";

    /** Attributo della request con l'id del superadmin che sta impersonando (Long). */
    public static final String REQUEST_ATTR_IMPERSONATED_BY = "impersonatedBy";

    private record Rule(String method, String pattern) {
        boolean matches(String m, String path) {
            return (method == null || method.equals(m)) && MATCHER.match(pattern, path);
        }
    }

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    /** method null = qualsiasi metodo. */
    private static final List<Rule> BLOCKED = List.of(
            // credenziali / profilo dell'account (password, email, telefono)
            new Rule("POST", "/api/users/changePassword"),
            new Rule("POST", "/api/users/updateData"),
            // endpoint futuri di cambio email / chiusura account: bloccati in anticipo
            new Rule(null, "/api/users/changeEmail/**"),
            new Rule(null, "/api/users/closeAccount/**"),
            new Rule(null, "/api/users/deleteAccount/**"),
            new Rule(null, "/api/agency/delete/**"),
            // credenziali del provider di pagamento del locale
            new Rule("PUT", "/api/payments/provider/**"),
            new Rule("DELETE", "/api/payments/provider/**"),
            // rimborsi
            new Rule("POST", "/api/payments/*/refund")
    );

    private ImpersonationPolicy() {
    }

    public static boolean isBlocked(String method, String path) {
        if (method == null || path == null) return false;
        String m = method.toUpperCase(Locale.ROOT);
        String p = normalize(path);
        for (Rule r : BLOCKED) {
            if (r.matches(m, p)) return true;
        }
        return false;
    }

    public static boolean isMutating(String method) {
        if (method == null) return false;
        return switch (method.toUpperCase(Locale.ROOT)) {
            case "POST", "PUT", "PATCH", "DELETE" -> true;
            default -> false;
        };
    }

    /** Toglie lo slash finale e i doppi slash, così "/api/users/changePassword/" non aggira la regola. */
    static String normalize(String path) {
        String p = path.replaceAll("/{2,}", "/");
        while (p.length() > 1 && p.endsWith("/")) p = p.substring(0, p.length() - 1);
        return p;
    }
}
