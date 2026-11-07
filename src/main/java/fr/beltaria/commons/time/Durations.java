package fr.beltaria.commons.time;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Les durees des configurations et des commandes ({@code 30m}, {@code 1h30m}, {@code 2d}, {@code 45s}) et leur
 * affichage. Sans etat, utilisable depuis n'importe quel thread.
 */
public final class Durations {

    private static final Pattern FORMAT =
            Pattern.compile("(?:(\\d{1,3})d)?(?:(\\d{1,4})h)?(?:(\\d{1,5})m)?(?:(\\d{1,6})s)?");

    private Durations() {
    }

    /**
     * Lit {@code [Nd][Nh][Nm][Ns]}, au moins une partie, dans cet ordre, sans espace interne : {@code 30m},
     * {@code 1h30m}, {@code 2d}, {@code 90s}, {@code 0s}. Majuscules et espaces autour acceptes.
     *
     * @throws IllegalArgumentException avec le texte fautif et des exemples
     */
    public static Duration parse(String text) {
        String trimmed = text.trim().toLowerCase(Locale.ROOT);
        Matcher matcher = FORMAT.matcher(trimmed);
        if (trimmed.isEmpty() || !matcher.matches()) {
            throw new IllegalArgumentException("duree invalide \"" + text + "\" (exemples : 30m, 1h30m, 2d, 45s)");
        }
        long seconds = part(matcher.group(1)) * 86_400
                + part(matcher.group(2)) * 3_600
                + part(matcher.group(3)) * 60
                + part(matcher.group(4));
        return Duration.ofSeconds(seconds);
    }

    /** {@link #parse}, en refusant zero. */
    public static Duration parsePositive(String text) {
        Duration duration = parse(text);
        if (duration.isZero()) {
            throw new IllegalArgumentException("la duree \"" + text + "\" doit etre plus longue que zero");
        }
        return duration;
    }

    /**
     * Le texte le plus court que {@link #parse} relit a l'identique : {@code 30m}, {@code 1h30m}, {@code 2d3h},
     * {@code 0s}.
     *
     * @throws IllegalArgumentException si la duree est negative, a des fractions de seconde ou depasse 999 jours
     */
    public static String compact(Duration duration) {
        long total = duration.getSeconds();
        if (total < 0 || duration.getNano() != 0 || total >= 1000L * 86_400) {
            throw new IllegalArgumentException("pas un nombre entier de secondes entre 0 et 999 jours : " + duration);
        }
        if (total == 0) {
            return "0s";
        }
        StringBuilder text = new StringBuilder();
        append(text, total / 86_400, 'd');
        append(text, total % 86_400 / 3_600, 'h');
        append(text, total % 3_600 / 60, 'm');
        append(text, total % 60, 's');
        return text.toString();
    }

    /** Chrono arrondi a la seconde superieure : {@code 29:59}, {@code 1:02:03} ; negatif ou nul donne {@code 00:00}. */
    public static String clock(Duration duration) {
        long total = ceilSeconds(duration);
        long hours = total / 3_600;
        long minutes = total % 3_600 / 60;
        long seconds = total % 60;
        if (hours > 0) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
    }

    /**
     * En toutes lettres pour les annonces, arrondi a la seconde superieure : {@code 15 min}, {@code 1 h 05},
     * {@code 1 min 30 s}, {@code 30 s}, {@code 2 j 3 h}.
     */
    public static String words(Duration duration) {
        long total = ceilSeconds(duration);
        long days = total / 86_400;
        long hours = total % 86_400 / 3_600;
        long minutes = total % 3_600 / 60;
        long seconds = total % 60;
        if (days > 0) {
            return hours == 0 ? days + " j" : days + " j " + hours + " h";
        }
        if (hours > 0) {
            return minutes == 0 ? hours + " h" : String.format(Locale.ROOT, "%d h %02d", hours, minutes);
        }
        if (minutes > 0) {
            return seconds == 0 ? minutes + " min" : minutes + " min " + seconds + " s";
        }
        return seconds + " s";
    }

    /** Compte a rebours court, arrondi a la seconde superieure : {@code 4m 12s}, {@code 45s}, {@code 1h 05m}. */
    public static String countdown(Duration duration) {
        long total = ceilSeconds(duration);
        long hours = total / 3_600;
        long minutes = total % 3_600 / 60;
        long seconds = total % 60;
        if (hours > 0) {
            return String.format(Locale.ROOT, "%dh %02dm", hours, minutes);
        }
        if (minutes > 0) {
            return String.format(Locale.ROOT, "%dm %02ds", minutes, seconds);
        }
        return seconds + "s";
    }

    /** Secondes entieres, une fraction arrondie au-dessus : un chrono n'affiche jamais 00:00 tant qu'il reste du temps. */
    public static long ceilSeconds(Duration duration) {
        if (duration.isNegative() || duration.isZero()) {
            return 0;
        }
        long seconds = duration.getSeconds();
        return duration.getNano() > 0 ? seconds + 1 : seconds;
    }

    private static void append(StringBuilder text, long amount, char unit) {
        if (amount > 0) {
            text.append(amount).append(unit);
        }
    }

    private static long part(String group) {
        return group == null ? 0 : Long.parseLong(group);
    }
}
