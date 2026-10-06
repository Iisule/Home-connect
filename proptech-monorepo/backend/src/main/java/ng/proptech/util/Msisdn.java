package ng.proptech.util;

/** Phone-number normalisation. Everything is stored and compared as E.164: +234XXXXXXXXXX. */
public final class Msisdn {

    private Msisdn() {}

    /**
     * "08031110001", "2348031110001", "+234 803 111 0001" -> "+2348031110001".
     * Returns "" for null/blank so callers can treat it as "unknown number".
     */
    public static String normalize(String raw) {
        if (raw == null) return "";
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return "";
        if (digits.startsWith("234")) return "+" + digits;
        if (digits.startsWith("0")) return "+234" + digits.substring(1);
        return "+" + digits;
    }
}
