package fr.beltaria.commons.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;

class WeeklyTest {

    // ---- jours

    @Test
    void readsFrenchAndEnglishDaysWhateverTheCaseAndAccents() {
        assertEquals(DayOfWeek.SATURDAY, Weekly.parseDay("samedi"));
        assertEquals(DayOfWeek.SATURDAY, Weekly.parseDay(" Samedi "));
        assertEquals(DayOfWeek.SATURDAY, Weekly.parseDay("SATURDAY"));
        assertEquals(DayOfWeek.WEDNESDAY, Weekly.parseDay("mércredi"));
        for (DayOfWeek day : DayOfWeek.values()) {
            assertEquals(day, Weekly.parseDay(Weekly.day(day)), "le nom affiche se relit");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "sam", "lundis", "MON", "1"})
    void refusesAnUnknownDayAndNamesIt(String text) {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> Weekly.parseDay(text));
        assertTrue(refused.getMessage().contains("\"" + text + "\""), refused.getMessage());
    }

    @Test
    void absentStarOrTousMeanEveryDay() {
        assertEquals(Set.of(), Weekly.parseDays(null));
        assertEquals(Set.of(), Weekly.parseDays("*"));
        assertEquals(Set.of(), Weekly.parseDays(" Tous "));
        assertEquals(Set.of(), Weekly.parseDays(List.of("*")));
        assertEquals(Set.of(), Weekly.parseDays(List.of("tous")));
        // melange : "*" n'est pas un jour
        assertThrows(IllegalArgumentException.class, () -> Weekly.parseDays(List.of("*", "samedi")));
    }

    @Test
    void theDocumentedEveryDayFormsParseThroughTheRealYaml() throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("a: \"*\"\nb: [\"*\"]\nc: [tous]\nd: tous\n");
        for (String key : List.of("a", "b", "c", "d")) {
            assertEquals(Set.of(), Weekly.parseDays(yaml.get(key)), key);
        }
        // [*] sans guillemets n'est pas du YAML : * ouvre un alias
        assertThrows(InvalidConfigurationException.class, () -> new YamlConfiguration().loadFromString("e: [*]\n"));
    }

    @Test
    void theHintForANumberIsAValueTheSameMethodAccepts() {
        IllegalArgumentException start = assertThrows(IllegalArgumentException.class, () -> Weekly.parseTime(1440));
        assertTrue(!start.getMessage().contains("\"24:00\""), start.getMessage());
        IllegalArgumentException end = assertThrows(IllegalArgumentException.class, () -> Weekly.parseEndTime(1440));
        assertTrue(end.getMessage().contains("\"24:00\""), end.getMessage());
    }

    @Test
    void readsAYamlListOrACommaSeparatedText() {
        Set<DayOfWeek> weekend = EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
        assertEquals(weekend, Weekly.parseDays(List.of("samedi", "dimanche")));
        assertEquals(weekend, Weekly.parseDays("samedi,dimanche"));
        assertEquals(weekend, Weekly.parseDays("saturday,SUNDAY,samedi"));
    }

    @Test
    void refusesAnEmptyListBecauseNoDayAndEveryDayWouldLookTheSame() {
        assertThrows(IllegalArgumentException.class, () -> Weekly.parseDays(List.of()));
        assertThrows(IllegalArgumentException.class, () -> Weekly.parseDays(""));
        assertThrows(IllegalArgumentException.class, () -> Weekly.parseDays("samedi,"));
        assertThrows(IllegalArgumentException.class, () -> Weekly.parseDays(Arrays.asList("samedi", null)));
        assertThrows(IllegalArgumentException.class, () -> Weekly.parseDays(List.of("samedi", "funday")));
    }

    // ---- heures

    @Test
    void readsTimes() {
        assertEquals(LocalTime.of(21, 0), Weekly.parseTime("21:00"));
        assertEquals(LocalTime.of(9, 5), Weekly.parseTime("9:05"));
        assertEquals(LocalTime.of(0, 0), Weekly.parseTime("00:00"));
        assertEquals(LocalTime.of(23, 59), Weekly.parseTime(" 23:59 "));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "21", "21h", "21:0", "21:000", "24:00", "23:60", "-1:00", "21:00:00", "9.30"})
    void refusesAnythingElseAsAStartTime(String text) {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> Weekly.parseTime(text));
        assertTrue(refused.getMessage().contains("\"" + text + "\""), refused.getMessage());
    }

    @Test
    void anEndTimeMayBeTwentyFourOClock() {
        assertEquals(LocalTime.MIDNIGHT, Weekly.parseEndTime("24:00"));
        assertEquals(LocalTime.of(9, 0), Weekly.parseEndTime("09:00"));
        assertThrows(IllegalArgumentException.class, () -> Weekly.parseEndTime(1440));
        assertThrows(IllegalArgumentException.class, () -> Weekly.parseEndTime("24:01"));
        assertThrows(IllegalArgumentException.class, () -> Weekly.parseTime(1440));
    }

    @Test
    void anUnquotedTimeIsRefusedWithTheQuotedFormToWrite() throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("a: 21:00\nb: 9:05\nc: \"21:00\"\nd: 09:00\ne: 24:00\nf: 0:30\n");
        // Le vrai parseur de la prod : 21:00 et 9:05 sans guillemets deviennent des nombres (1260, 545),
        // indiscernables de 1260 ou 545 ecrits tels quels ; 09:00 et 0:30 restent du texte
        assertTrue(yaml.get("a") instanceof Number, String.valueOf(yaml.get("a")));
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> Weekly.parseTime(yaml.get("a")));
        assertTrue(refused.getMessage().contains("\"21:00\""), refused.getMessage());
        refused = assertThrows(IllegalArgumentException.class, () -> Weekly.parseTime(yaml.get("b")));
        assertTrue(refused.getMessage().contains("\"09:05\""), refused.getMessage());
        refused = assertThrows(IllegalArgumentException.class, () -> Weekly.parseEndTime(yaml.get("e")));
        assertTrue(refused.getMessage().contains("\"24:00\""), refused.getMessage());
        assertEquals(LocalTime.of(21, 0), Weekly.parseTime(yaml.get("c")));
        assertEquals(LocalTime.of(9, 0), Weekly.parseTime(yaml.get("d")));
        assertEquals(LocalTime.of(0, 30), Weekly.parseTime(yaml.get("f")));
    }

    @ParameterizedTest
    @ValueSource(ints = {-60, 0, 9, 59, 720, 1200, 1260, 1440, 100_000})
    void refusesEveryNumber(int number) {
        // time: 9 n'est pas 00:09, time: 1200 n'est ni midi ni 20:00
        assertThrows(IllegalArgumentException.class, () -> Weekly.parseTime(number));
    }

    @Test
    void refusesAFractionalNumberAndNull() {
        assertThrows(IllegalArgumentException.class, () -> Weekly.parseTime(1260.5));
        assertThrows(IllegalArgumentException.class, () -> Weekly.parseTime(null));
    }

    @Property
    void everyTimeIsWrittenThenReadBack(@ForAll @IntRange(min = 0, max = 23) int hour,
            @ForAll @IntRange(min = 0, max = 59) int minute) {
        LocalTime time = LocalTime.of(hour, minute);
        assertEquals(time, Weekly.parseTime(Weekly.time(time)));
        assertEquals(time, Weekly.parseEndTime(Weekly.time(time)));
    }

    // ---- prochaine occurrence

    private static Instant paris(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, Weekly.ZONE).toInstant();
    }

    @Test
    void nextIsStrictlyAfter() {
        Set<DayOfWeek> saturday = Set.of(DayOfWeek.SATURDAY);
        // 2026-11-07 est un samedi
        assertEquals(paris(2026, 11, 7, 21, 0), Weekly.next(saturday, LocalTime.of(21, 0), paris(2026, 11, 7, 20, 59)));
        assertEquals(paris(2026, 11, 14, 21, 0), Weekly.next(saturday, LocalTime.of(21, 0), paris(2026, 11, 7, 21, 0)));
        assertEquals(paris(2026, 11, 8, 9, 0), Weekly.next(Set.of(), LocalTime.of(9, 0), paris(2026, 11, 7, 9, 0)));
    }

    @Test
    void aTimeSkippedInMarchFallsAnHourLater() {
        // 2026-03-29 : 02:00 -> 03:00
        Instant next = Weekly.next(Set.of(DayOfWeek.SUNDAY), LocalTime.of(2, 30), paris(2026, 3, 28, 12, 0));
        assertEquals(paris(2026, 3, 29, 3, 30), next);
    }

    @Test
    void aTimeRepeatedInOctoberFallsOnlyOnce() {
        // 2026-10-25 : 03:00 -> 02:00, 02:30 existe deux fois
        Instant first = Weekly.next(Set.of(), LocalTime.of(2, 30), paris(2026, 10, 25, 0, 0));
        assertEquals(Instant.parse("2026-10-25T00:30:00Z"), first, "la premiere, en heure d'ete");
        Instant second = Weekly.next(Set.of(), LocalTime.of(2, 30), first);
        assertEquals(paris(2026, 10, 26, 2, 30), second, "pas de seconde fois une heure plus tard");
    }

    @Provide
    Arbitrary<Set<DayOfWeek>> days() {
        return Arbitraries.of(DayOfWeek.class).set().ofMaxSize(7);
    }

    @Property(tries = 2000)
    void nextFallsOnAListedDayAtTheTimeAndMissesNothing(@ForAll("days") Set<DayOfWeek> days,
            @ForAll @IntRange(min = 0, max = 23) int hour, @ForAll @IntRange(min = 0, max = 59) int minute,
            @ForAll @LongRange(min = 1_577_836_800L, max = 2_082_758_400L) long epochSecond) {
        LocalTime time = LocalTime.of(hour, minute);
        Instant after = Instant.ofEpochSecond(epochSecond);
        Instant next = Weekly.next(days, time, after);

        assertTrue(next.isAfter(after));
        assertTrue(Duration.between(after, next).compareTo(Duration.ofDays(7).plusHours(1)) <= 0);
        ZonedDateTime local = next.atZone(Weekly.ZONE);
        assertTrue(days.isEmpty() || days.contains(local.getDayOfWeek()), local.toString());
        assertEquals(Weekly.at(local.toLocalDate(), time), next, "l'occurrence de ce jour-la");
        // Rien entre les deux : repartir de n'importe quel instant avant next redonne next
        Instant middle = after.plus(Duration.between(after, next).dividedBy(2));
        assertEquals(next, Weekly.next(days, time, middle));
        assertEquals(next, Weekly.next(days, time, next.minusNanos(1)));
    }

    @Property(tries = 300)
    void aWeekOfOccurrencesHasOnePerListedDay(@ForAll("days") Set<DayOfWeek> days,
            @ForAll @IntRange(min = 0, max = 23) int hour,
            @ForAll @LongRange(min = 1_577_836_800L, max = 2_082_758_400L) long epochSecond) {
        LocalTime time = LocalTime.of(hour, 0);
        Instant start = Instant.ofEpochSecond(epochSecond);
        Instant end = start.atZone(Weekly.ZONE).plusWeeks(1).toInstant();
        List<LocalDate> dates = new ArrayList<>();
        for (Instant at = Weekly.next(days, time, start); !at.isAfter(end); at = Weekly.next(days, time, at)) {
            dates.add(at.atZone(Weekly.ZONE).toLocalDate());
        }
        assertEquals(dates.stream().distinct().count(), dates.size(), "jamais deux fois le meme jour : " + dates);
        assertEquals(days.isEmpty() ? 7 : days.size(), dates.size(), dates.toString());
    }
}
