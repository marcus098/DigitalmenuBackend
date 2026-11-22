package com.modules.deliverymodule.otp;

import java.util.regex.Pattern;

/** Normalizzazione dei numeri di telefono in E.164 (prefisso predefinito +39). */
public final class PhoneNumbers {

    private static final Pattern E164 = Pattern.compile("^\\+[1-9]\\d{7,14}$");

    private PhoneNumbers() {}

    /**
     * "333 123 4567" → "+393331234567", "0039 333..." → "+39333...", "+44 20..." invariato.
     * @throws IllegalArgumentException se il numero non è valido
     */
    public static String normalize(String raw) {
        if (raw == null) throw new IllegalArgumentException("Numero di telefono mancante");
        String s = raw.strip().replaceAll("[\\s.\\-()/]", "");
        if (s.startsWith("00")) s = "+" + s.substring(2);
        // "39 333 1234567" senza "+": prefisso internazionale italiano scritto senza il più
        else if (s.matches("393\\d{8,9}")) s = "+" + s;
        if (!s.startsWith("+")) {
            if (!s.matches("\\d{6,12}")) throw new IllegalArgumentException("Numero di telefono non valido");
            s = "+39" + s;
        }
        if (!E164.matcher(s).matches()) throw new IllegalArgumentException("Numero di telefono non valido");
        // Italia: cellulari 3xx (9-10 cifre) e fissi 0x (6-11 cifre)
        if (s.startsWith("+39")) {
            String national = s.substring(3);
            boolean mobile = national.startsWith("3") && national.length() >= 9 && national.length() <= 10;
            boolean landline = national.startsWith("0") && national.length() >= 6 && national.length() <= 11;
            if (!mobile && !landline) throw new IllegalArgumentException("Numero di telefono non valido");
        }
        return s;
    }

    /** "+393331234567" → "***4567" (per i log: mai il numero intero). */
    public static String mask(String phone) {
        if (phone == null || phone.length() <= 4) return "****";
        return "***" + phone.substring(phone.length() - 4);
    }
}
