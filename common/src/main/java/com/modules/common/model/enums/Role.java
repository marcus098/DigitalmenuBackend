package com.modules.common.model.enums;

public enum Role {
    ROLE_ADMIN,
    ROLE_USER,
    ROLE_WAITER,
    ROLE_DELIVERY,
    /** Amministratore della piattaforma: nessun locale associato. */
    ROLE_SUPERADMIN;

    /**
     * Restituisce il valore dell'enum corrispondente a una stringa.
     *
     * @param roleName il nome del ruolo come stringa
     * @return il valore dell'enum corrispondente
     * @throws IllegalArgumentException se non esiste un ruolo corrispondente
     */
    public static Role fromString(String roleName) {
        if (roleName == null || roleName.isEmpty()) {
            throw new IllegalArgumentException("Role name cannot be null or empty");
        }
        try {
            return Role.valueOf(roleName.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("No enum constant for the role name: " + roleName, e);
        }
    }
}
