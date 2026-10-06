package ng.proptech.util;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Order-insensitive, accent-insensitive person-name comparison used by the simulated KYC loop.
 * "MUSA IBRAHIM DANLADI" matches "Danladi Musa" (2 shared tokens) but not "Yakubu Adamu Lawal".
 */
public final class NameMatcher {

    private NameMatcher() {}

    public static boolean matches(String a, String b) {
        Set<String> ta = tokens(a);
        Set<String> tb = tokens(b);
        if (ta.isEmpty() || tb.isEmpty()) return false;
        Set<String> shared = new HashSet<>(ta);
        shared.retainAll(tb);
        // Nigerian names are frequently recorded with 2 or 3 tokens in different orders; two shared tokens
        // (or every token of a single-token name) is a strong enough signal for this prototype.
        return shared.size() >= Math.min(2, Math.min(ta.size(), tb.size()));
    }

    private static Set<String> tokens(String s) {
        Set<String> out = new HashSet<>();
        if (s == null) return out;
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        for (String t : n.split("[^a-z]+")) {
            if (t.length() > 1) out.add(t);
        }
        return out;
    }
}
