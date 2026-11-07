package fr.beltaria.commons.time;

import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Les plannings hebdomadaires des plugins (lancements de KOTH ou de totem, creneaux d'outpost) : jours, heures
 * et prochaine occurrence, toujours en {@link #ZONE Europe/Paris}. Sans etat, utilisable depuis n'importe quel
 * fil.
 *
 * <p>Changement d'heure : une heure qui n'existe pas (le dernier dimanche de mars, 02:30) tombe une heure plus
 * tard (03:30) ; une heure qui existe deux fois (le dernier dimanche d'octobre, 02:30) ne tombe qu'une fois, a
 * la premiere (heure d'ete). C'est la regle de {@link ZonedDateTime#of}.
 */
public final class Weekly {

    /** Le fuseau de toutes les heures et de tous les jours des configurations Beltaria. */
    public static final ZoneId ZONE = ZoneId.of("Europe/Paris");

    private static final Map<String, DayOfWeek> FRENCH_DAYS = Map.of(
            "LUNDI", DayOfWeek.MONDAY, "MARDI", DayOfWeek.TUESDAY, "MERCREDI", DayOfWeek.WEDNESDAY,
            "JEUDI", DayOfWeek.THURSDAY, "VENDREDI", DayOfWeek.FRIDAY, "SAMEDI", DayOfWeek.SATURDAY,
            "DIMANCHE", DayOfWeek.SUNDAY);

    /** Ce qui veut dire « tous les jours » dans une config ou une commande. */
    private static final Set<String> EVERY_DAY = Set.of("*", "TOUS");

    private static final Pattern TIME = Pattern.compile("(\\d{1,2}):(\\d{2})");

    private static final int MINUTES_PER_DAY = 24 * 60;

    /** Une occurrence se cherche au plus sur ce nombre de jours : une semaine, plus les bords. */
    private static final int SEARCH_DAYS = 9;

    private Weekly() {
    }

    /**
     * Un jour en francais ou en anglais, casse et accents ignores : {@code samedi}, {@code Samedi},
     * {@code SATURDAY}.
     *
     * @throws IllegalArgumentException avec le texte fautif et ce qui est attendu
     */
    public static DayOfWeek parseDay(String text) {
        Objects.requireNonNull(text, "text");
        String key = normalize(text);
        DayOfWeek french = FRENCH_DAYS.get(key);
        if (french != null) {
            return french;
        }
        try {
            return DayOfWeek.valueOf(key);
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException("jour inconnu \"" + text + "\" (lundi..dimanche ou MONDAY..SUNDAY)",
                    unknown);
        }
    }

    /**
     * Les jours d'une config ou d'une commande. Vide veut dire tous les jours.
     *
     * <ul>
     * <li>{@code null} (cle absente), {@code "*"} ou {@code "tous"} : tous les jours ;</li>
     * <li>une liste YAML ({@code [samedi, dimanche]}) ou un texte separe par des virgules
     * ({@code samedi,dimanche}) : ces jours.</li>
     * </ul>
     *
     * @throws IllegalArgumentException pour un jour inconnu, ou une liste vide (ambigue : aucun jour, ou tous ?)
     */
    public static Set<DayOfWeek> parseDays(Object value) {
        if (value == null) {
            return Set.of();
        }
        Collection<?> items;
        if (value instanceof Collection<?> list) {
            items = list;
        } else {
            String text = String.valueOf(value).trim();
            if (EVERY_DAY.contains(normalize(text))) {
                return Set.of();
            }
            items = text.isEmpty() ? List.of() : List.of(text.split(",", -1));
        }
        if (items.isEmpty()) {
            throw new IllegalArgumentException(
                    "liste de jours vide (retire la cle, ou mets \"*\", pour tous les jours)");
        }
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (Object item : items) {
            if (item == null) {
                throw new IllegalArgumentException("jour vide dans la liste (lundi..dimanche ou MONDAY..SUNDAY)");
            }
            days.add(parseDay(String.valueOf(item)));
        }
        return Set.copyOf(days);
    }

    /**
     * Une heure de debut : {@code "21:00"}, {@code "9:05"}, de {@code 00:00} a {@code 23:59}, minutes entieres.
     * Un nombre est refuse : YAML 1.1 lit {@code 21:00} sans guillemets comme 1260, indiscernable d'un 1260 ecrit
     * tel quel ; le message donne la forme a ecrire ({@code "21:00"}).
     *
     * @throws IllegalArgumentException avec la valeur fautive et ce qui est attendu
     */
    public static LocalTime parseTime(Object value) {
        int minutes = minutes(value, false);
        return LocalTime.of(minutes / 60, minutes % 60);
    }

    /**
     * Une heure de fin : comme {@link #parseTime}, plus {@code "24:00"} pour minuit en fin de journee (rendu
     * {@link LocalTime#MIDNIGHT} ; a l'appelant de le placer le lendemain).
     *
     * @throws IllegalArgumentException avec la valeur fautive et ce qui est attendu
     */
    public static LocalTime parseEndTime(Object value) {
        int minutes = minutes(value, true);
        return minutes == MINUTES_PER_DAY ? LocalTime.MIDNIGHT : LocalTime.of(minutes / 60, minutes % 60);
    }

    /** Le jour en francais, en minuscules : {@code samedi}. */
    public static String day(DayOfWeek day) {
        return day.getDisplayName(TextStyle.FULL, Locale.FRENCH);
    }

    /** L'heure sur deux chiffres : {@code 09:05}, {@code 21:00}. */
    public static String time(LocalTime time) {
        return String.format(Locale.ROOT, "%02d:%02d", time.getHour(), time.getMinute());
    }

    /** Le moment ou tombe {@code time} le jour {@code date}, a Paris (voir la classe pour le changement d'heure). */
    public static Instant at(LocalDate date, LocalTime time) {
        return ZonedDateTime.of(date, time, ZONE).toInstant();
    }

    /**
     * La premiere occurrence strictement apres {@code after} de {@code time} un des {@code days} (vide : tous
     * les jours).
     */
    public static Instant next(Set<DayOfWeek> days, LocalTime time, Instant after) {
        Objects.requireNonNull(days, "days");
        Objects.requireNonNull(time, "time");
        LocalDate date = after.atZone(ZONE).toLocalDate().minusDays(1);
        for (int i = 0; i < SEARCH_DAYS; i++, date = date.plusDays(1)) {
            if (!days.isEmpty() && !days.contains(date.getDayOfWeek())) {
                continue;
            }
            Instant occurrence = at(date, time);
            if (occurrence.isAfter(after)) { // strictement : l'occurrence en cours est deja passee
                return occurrence;
            }
        }
        throw new IllegalStateException("aucune occurrence en une semaine : " + days + " " + time);
    }

    private static int minutes(Object value, boolean end) {
        String expected = end ? "HH:mm, de 00:00 a 24:00" : "HH:mm, de 00:00 a 23:59";
        if (value instanceof Number number) {
            // 21:00 sans guillemets est l'entier sexagesimal 1260 de YAML 1.1, mais 1260 ecrit tel quel aussi, et
            // time: 1200 (midi ?) donnerait 20:00. Indiscernables : refus, avec la forme a ecrire quand le nombre
            // peut venir d'une heure.
            long raw = number.longValue();
            String hint = raw == number.doubleValue() && raw >= 60 && raw <= MINUTES_PER_DAY
                    ? "\"" + String.format(Locale.ROOT, "%02d:%02d", raw / 60, raw % 60) + "\""
                    : "\"21:00\"";
            throw new IllegalArgumentException("heure " + value + " sans guillemets (" + expected
                    + ") : ecris-la entre guillemets, par exemple " + hint);
        }
        String text = value == null ? "" : String.valueOf(value).trim();
        Matcher matcher = TIME.matcher(text);
        if (matcher.matches()) {
            int hours = Integer.parseInt(matcher.group(1));
            int minutes = Integer.parseInt(matcher.group(2));
            if (minutes < 60 && (hours < 24 || end && hours == 24 && minutes == 0)) {
                return hours * 60 + minutes;
            }
        }
        throw new IllegalArgumentException("heure invalide \"" + (value == null ? "" : value) + "\" (" + expected + ")");
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT);
    }
}
