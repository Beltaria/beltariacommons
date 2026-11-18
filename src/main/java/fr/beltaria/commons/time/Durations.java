package fr.beltaria.commons.time;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Les durees des configurations et des commandes ({@code 30m}, {@code 1h30m}, {@code 2d}, {@code 45s},
 * {@code 1.5s}) et leur affichage. Sans etat, utilisable depuis n'importe quel thread.
 */
public final class Durations {

    /** Un tick serveur : 50 ms, 20 par seconde. */
    public static final Duration TICK = Duration.ofMillis(50);

    private static final long NANOS_PER_TICK = TICK.toNanos();
    private static final long NANOS_PER_MILLI = 1_000_000;

    private static final Pattern FORMAT = Pattern.compile(
            "(?:(\\d{1,3})d)?(?:(\\d{1,4})h)?(?:(\\d{1,5})m)?(?:(\\d{1,6})(?:\\.(\\d{1,3}))?s)?");

    private Durations() {
    }

    /**
     * Lit {@code [Nd][Nh][Nm][Ns]}, au moins une partie, dans cet ordre, sans espace interne : {@code 30m},
     * {@code 1h30m}, {@code 2d}, {@code 90s}, {@code 0s}. Les secondes, et elles seules, acceptent jusqu'a trois
     * decimales apres un point (la milliseconde) : {@code 1.5s}, {@code 2.4s}, {@code 0.25s}, {@code 1m2.5s}.
     * Majuscules et espaces autour acceptes. Une duree lue ici n'est pas forcement un nombre entier de ticks :
     * {@link #toTicks} le verifie.
     *
     * @throws IllegalArgumentException avec le texte fautif et des exemples : vide, negatif, unite inconnue ou dans
     *         le desordre, decimale ailleurs que sur les secondes, plus de trois decimales, {@code 1.s}, {@code .5s}
     */
    public static Duration parse(String text) {
        String trimmed = text.trim().toLowerCase(Locale.ROOT);
        Matcher matcher = FORMAT.matcher(trimmed);
        if (trimmed.isEmpty() || !matcher.matches()) {
            throw new IllegalArgumentException("duree invalide \"" + text
                    + "\" (exemples : 30m, 1h30m, 2d, 45s, 1.5s ; decimales sur les secondes seulement, 3 au plus)");
        }
        long seconds = part(matcher.group(1)) * 86_400
                + part(matcher.group(2)) * 3_600
                + part(matcher.group(3)) * 60
                + part(matcher.group(4));
        return Duration.ofSeconds(seconds).plusMillis(millis(matcher.group(5)));
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
     * {@code 0s}, {@code 1.5s}, {@code 1m0.25s}.
     *
     * @throws IllegalArgumentException si la duree est negative, a une fraction plus fine que la milliseconde ou
     *         depasse 999 jours
     */
    public static String compact(Duration duration) {
        if (!compactable(duration)) {
            throw new IllegalArgumentException(
                    "pas un nombre entier de millisecondes entre 0 et 999 jours : " + duration);
        }
        long total = duration.getSeconds();
        long millis = duration.getNano() / NANOS_PER_MILLI;
        if (total == 0 && millis == 0) {
            return "0s";
        }
        StringBuilder text = new StringBuilder();
        append(text, total / 86_400, 'd');
        append(text, total % 86_400 / 3_600, 'h');
        append(text, total % 3_600 / 60, 'm');
        long seconds = total % 60;
        if (millis > 0) {
            // 3 chiffres puis sans zeros de fin : 500 -> .5, 250 -> .25, 50 -> .05
            String fraction = String.format(Locale.ROOT, "%03d", millis).replaceFirst("0+$", "");
            text.append(seconds).append('.').append(fraction).append('s');
        } else {
            append(text, seconds, 's');
        }
        return text.toString();
    }

    /**
     * La duree en ticks serveur ({@link #TICK}, 50 ms) : {@code 1.5s} donne 30, {@code 2.4s} 48, {@code 1m} 1200.
     * Les configurations Minecraft comptent en ticks : une duree qui tomberait entre deux ticks est refusee plutot
     * qu'arrondie en silence, avec les deux ecritures valides les plus proches.
     *
     * @throws IllegalArgumentException si la duree est negative, n'est pas un multiple de 50 ms (le message donne
     *         par exemple {@code 1.25s (25 ticks) ou 1.3s (26 ticks)}) ou ne tient pas dans un {@code long}
     */
    public static long toTicks(Duration duration) {
        if (duration.isNegative()) {
            throw new IllegalArgumentException("duree negative, pas un nombre de ticks : " + describe(duration));
        }
        long seconds = duration.getSeconds();
        int nano = duration.getNano();
        // + 20 : la place du tick au-dessus propose dans le message
        if (seconds > (Long.MAX_VALUE - 20) / 20) {
            throw new IllegalArgumentException("duree trop longue pour un nombre de ticks : " + duration);
        }
        long below = seconds * 20 + nano / NANOS_PER_TICK;
        if (nano % NANOS_PER_TICK == 0) {
            return below;
        }
        throw new IllegalArgumentException("la duree " + describe(duration)
                + " n'est pas un nombre entier de ticks (50 ms) : ecrire " + tickHint(below) + " ou "
                + tickHint(below + 1));
    }

    /** {@code 1.25s (25 ticks)}, ou seulement {@code 25 ticks} si {@link #compact} ne sait pas l'ecrire. */
    private static String tickHint(long ticks) {
        Duration duration = Duration.ofSeconds(ticks / 20, ticks % 20 * NANOS_PER_TICK);
        String count = ticks + (ticks == 1 ? " tick" : " ticks");
        return compactable(duration) ? compact(duration) + " (" + count + ")" : count;
    }

    /** {@link #compact} quand il sait l'ecrire, sinon la forme ISO de {@link Duration#toString}. */
    private static String describe(Duration duration) {
        return compactable(duration) ? compact(duration) : duration.toString();
    }

    private static boolean compactable(Duration duration) {
        long total = duration.getSeconds();
        return total >= 0 && duration.getNano() % NANOS_PER_MILLI == 0 && total < 1000L * 86_400;
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

    /** Les 1 a 3 decimales des secondes en millisecondes : {@code 5} -> 500, {@code 25} -> 250, {@code 05} -> 50. */
    private static long millis(String decimals) {
        if (decimals == null) {
            return 0;
        }
        long value = Long.parseLong(decimals);
        for (int i = decimals.length(); i < 3; i++) {
            value *= 10;
        }
        return value;
    }
}
