package fr.beltaria.commons.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Assume;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.LongRange;

class DurationsTest {

    @Test
    void readsEveryUnitAndTheirCombinations() {
        assertEquals(Duration.ofMinutes(30), Durations.parse("30m"));
        assertEquals(Duration.ofMinutes(90), Durations.parse("1h30m"));
        assertEquals(Duration.ofDays(2), Durations.parse("2d"));
        assertEquals(Duration.ofSeconds(45), Durations.parse("45s"));
        assertEquals(Duration.ofSeconds(86_400 + 3_600 + 60 + 1), Durations.parse("1d1h1m1s"));
        assertEquals(Duration.ZERO, Durations.parse("0s"));
        assertEquals(Duration.ofHours(2), Durations.parse(" 2H "));
    }

    @Test
    void readsDecimalSecondsToTheMillisecond() {
        assertEquals(Duration.ofMillis(1500), Durations.parse("1.5s"));
        assertEquals(Duration.ofMillis(2400), Durations.parse("2.4s"));
        assertEquals(Duration.ofMillis(250), Durations.parse("0.25s"));
        assertEquals(Duration.ofMillis(62_500), Durations.parse("1m2.5s"));
        assertEquals(Duration.ofMillis(50), Durations.parse("0.05s"));
        assertEquals(Duration.ofMillis(1), Durations.parse("0.001s"));
        assertEquals(Duration.ofMillis(1050), Durations.parse("1.050s"));
        assertEquals(Duration.ofSeconds(1), Durations.parse("1.0s"));
        assertEquals(Duration.ZERO, Durations.parse("0.000s"));
        assertEquals(Duration.ofHours(25).plusMillis(1500), Durations.parse(" 1D1H1.5S "));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "30", "m", "30 m", "1m1h", "-5m", "1.5h", "30min", "1h 30m", "1000d", "1w",
            "1.5m", "1.5d", "1.2345s", "1.s", ".5s", "-1.5s", "-0.5s", "1,5s", "1.5 s", "1. 5s", "1.5", "1.5.5s",
            "1.5s2m", "1.5s1.5s", "0x1s", "1e3s", "+1.5s"})
    void refusesAnythingElse(String text) {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> Durations.parse(text));
        assertTrue(refused.getMessage().contains("\"" + text + "\""), refused.getMessage());
        assertTrue(refused.getMessage().contains("1.5s"), refused.getMessage());
    }

    @Test
    void positiveRefusesZero() {
        assertThrows(IllegalArgumentException.class, () -> Durations.parsePositive("0m"));
        assertEquals(Duration.ofSeconds(1), Durations.parsePositive("1s"));
    }

    @Test
    void compactIsTheShortestForm() {
        assertEquals("0s", Durations.compact(Duration.ZERO));
        assertEquals("30m", Durations.compact(Duration.ofMinutes(30)));
        assertEquals("1h30m", Durations.compact(Duration.ofMinutes(90)));
        assertEquals("2d3h", Durations.compact(Duration.ofHours(51)));
        assertEquals("1d1s", Durations.compact(Duration.ofSeconds(86_401)));
        assertEquals("1.5s", Durations.compact(Duration.ofMillis(1500)));
        assertEquals("2.4s", Durations.compact(Duration.ofMillis(2400)));
        assertEquals("0.25s", Durations.compact(Duration.ofMillis(250)));
        assertEquals("0.05s", Durations.compact(Duration.ofMillis(50)));
        assertEquals("0.001s", Durations.compact(Duration.ofMillis(1)));
        assertEquals("1m0.5s", Durations.compact(Duration.ofMillis(60_500)));
        assertEquals("1h2.125s", Durations.compact(Duration.ofMillis(3_602_125)));
    }

    @Test
    void compactRefusesWhatParseCannotReadBack() {
        assertThrows(IllegalArgumentException.class, () -> Durations.compact(Duration.ofSeconds(-1)));
        assertThrows(IllegalArgumentException.class, () -> Durations.compact(Duration.ofMillis(-500)));
        assertThrows(IllegalArgumentException.class, () -> Durations.compact(Duration.ofNanos(1_500_000_001)));
        assertThrows(IllegalArgumentException.class, () -> Durations.compact(Duration.ofNanos(1)));
        assertThrows(IllegalArgumentException.class, () -> Durations.compact(Duration.ofDays(1000)));
    }

    @Property
    void compactReadsBackToTheSameDuration(@ForAll @LongRange(min = 0, max = 1000L * 86_400 - 1) long seconds) {
        Duration duration = Duration.ofSeconds(seconds);
        assertEquals(duration, Durations.parse(Durations.compact(duration)));
    }

    @Property
    void anyMillisecondDurationReadsBack(@ForAll @LongRange(min = 0, max = 1000L * 86_400_000 - 1) long millis) {
        Duration duration = Duration.ofMillis(millis);
        assertEquals(duration, Durations.parse(Durations.compact(duration)));
    }

    @Property
    void anyWholeTickDurationRoundTripsThroughItsText(
            @ForAll @LongRange(min = 0, max = 1000L * 86_400 * 20 - 1) long ticks) {
        Duration duration = Durations.TICK.multipliedBy(ticks);
        String text = Durations.compact(duration);
        assertEquals(duration, Durations.parse(text), text);
        assertEquals(ticks, Durations.toTicks(Durations.parse(text)), text);
    }

    /** Les formes entieres d'avant 1.3.0 donnent toujours la meme duree, et toujours un nombre entier de ticks. */
    @Property
    void wholeFormsAreUnchanged(@ForAll("wholeForms") WholeForm form) {
        Duration expected = Duration.ofSeconds(form.days * 86_400L + form.hours * 3_600L + form.minutes * 60L
                + form.seconds);
        assertEquals(expected, Durations.parse(form.text));
        assertEquals(expected.getSeconds() * 20, Durations.toTicks(Durations.parse(form.text)));
    }

    @Property
    void malformedTextIsRefused(@ForAll("malformed") String text) {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> Durations.parse(text));
        assertTrue(refused.getMessage().contains("\"" + text + "\""), refused.getMessage());
    }

    @Test
    void ticksOfTheConfigExamples() {
        assertEquals(30, Durations.toTicks(Durations.parse("1.5s")));
        assertEquals(48, Durations.toTicks(Durations.parse("2.4s")));
        assertEquals(5, Durations.toTicks(Durations.parse("0.25s")));
        assertEquals(1, Durations.toTicks(Durations.parse("0.05s")));
        assertEquals(1200, Durations.toTicks(Durations.parse("1m")));
        assertEquals(1250, Durations.toTicks(Durations.parse("1m2.5s")));
        assertEquals(0, Durations.toTicks(Duration.ZERO));
        assertEquals(1728000, Durations.toTicks(Durations.parse("1d")));
    }

    @Test
    void ticksRefuseBetweenTwoTicksWithTheNearestSpellings() {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> Durations.toTicks(Durations.parse("1.27s")));
        assertTrue(refused.getMessage().contains("1.27s"), refused.getMessage());
        assertTrue(refused.getMessage().contains("1.25s (25 ticks) ou 1.3s (26 ticks)"), refused.getMessage());

        refused = assertThrows(IllegalArgumentException.class, () -> Durations.toTicks(Durations.parse("0.01s")));
        assertTrue(refused.getMessage().contains("0s (0 ticks) ou 0.05s (1 tick)"), refused.getMessage());

        refused = assertThrows(IllegalArgumentException.class, () -> Durations.toTicks(Duration.ofNanos(1)));
        assertTrue(refused.getMessage().contains("0s (0 ticks) ou 0.05s (1 tick)"), refused.getMessage());
    }

    @Test
    void ticksRefuseNegativeAndOverflow() {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> Durations.toTicks(Duration.ofMillis(-50)));
        assertTrue(refused.getMessage().contains("negative"), refused.getMessage());
        assertThrows(IllegalArgumentException.class, () -> Durations.toTicks(Duration.ofSeconds(Long.MAX_VALUE)));
        assertThrows(IllegalArgumentException.class,
                () -> Durations.toTicks(Duration.ofSeconds(Long.MAX_VALUE / 20)));
    }

    @Test
    void ticksBeyondWhatCompactWritesStillGiveTheCount() {
        Duration huge = Duration.ofDays(5000).plusMillis(10);
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> Durations.toTicks(huge));
        assertTrue(refused.getMessage().contains("ecrire 8640000000 ticks ou 8640000001 ticks"),
                refused.getMessage());
        assertEquals(5000L * 86_400 * 20, Durations.toTicks(Duration.ofDays(5000)));
    }

    /** Toute duree au milliseme qui n'est pas un multiple de 50 ms est refusee, avec les deux ticks qui l'encadrent. */
    @Property
    void ticksRefuseEveryOffTickMillisecond(@ForAll @LongRange(min = 0, max = 1000L * 86_400_000 - 2) long millis) {
        Assume.that(millis % 50 != 0);
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> Durations.toTicks(Duration.ofMillis(millis)));
        long below = millis / 50;
        String message = refused.getMessage();
        assertTrue(message.contains(below + (below == 1 ? " tick)" : " ticks)")), message);
        assertTrue(message.contains((below + 1) + (below + 1 == 1 ? " tick" : " ticks")), message);
        if (below + 1 < 1000L * 86_400 * 20) {
            String above = Durations.compact(Durations.TICK.multipliedBy(below + 1));
            assertTrue(message.contains(" ou " + above + " ("), message);
            assertEquals(below + 1, Durations.toTicks(Durations.parse(above)));
        }
    }

    @Provide
    Arbitrary<WholeForm> wholeForms() {
        Arbitrary<Integer> days = Arbitraries.integers().between(-1, 999);
        Arbitrary<Integer> hours = Arbitraries.integers().between(-1, 9_999);
        Arbitrary<Integer> minutes = Arbitraries.integers().between(-1, 99_999);
        Arbitrary<Integer> seconds = Arbitraries.integers().between(-1, 999_999);
        // -1 : partie absente ; au moins une partie presente
        return Combinators.combine(days, hours, minutes, seconds).as(WholeForm::of)
                .filter(form -> !form.text.isEmpty());
    }

    @Provide
    Arbitrary<String> malformed() {
        Arbitrary<String> number = Arbitraries.integers().between(0, 999).map(String::valueOf);
        Arbitrary<String> decimals = Arbitraries.strings().numeric().ofMinLength(1).ofMaxLength(3);
        Arbitrary<String> nonSecondUnit = Arbitraries.of("d", "h", "m");
        return Arbitraries.oneOf(
                // decimale sur une autre unite que les secondes
                Combinators.combine(number, decimals, nonSecondUnit).as((n, d, u) -> n + "." + d + u),
                // plus de trois decimales
                Combinators.combine(number, Arbitraries.strings().numeric().ofMinLength(4).ofMaxLength(9))
                        .as((n, d) -> n + "." + d + "s"),
                // negatif
                Combinators.combine(number, decimals).as((n, d) -> "-" + n + "." + d + "s"),
                // point sans chiffre d'un cote ou de l'autre, ou virgule
                number.map(n -> n + ".s"),
                decimals.map(d -> "." + d + "s"),
                Combinators.combine(number, decimals).as((n, d) -> n + "," + d + "s"),
                // secondes decimales avant une autre unite
                Combinators.combine(number, decimals, nonSecondUnit).as((n, d, u) -> n + "." + d + "s1" + u));
    }

    record WholeForm(String text, int days, int hours, int minutes, int seconds) {
        static WholeForm of(int days, int hours, int minutes, int seconds) {
            StringBuilder text = new StringBuilder();
            if (days >= 0) {
                text.append(days).append('d');
            }
            if (hours >= 0) {
                text.append(hours).append('h');
            }
            if (minutes >= 0) {
                text.append(minutes).append('m');
            }
            if (seconds >= 0) {
                text.append(seconds).append('s');
            }
            return new WholeForm(text.toString(), Math.max(days, 0), Math.max(hours, 0), Math.max(minutes, 0),
                    Math.max(seconds, 0));
        }
    }

    @Test
    void clockRoundsUpAndNeverGoesBelowZero() {
        assertEquals("29:59", Durations.clock(Duration.ofSeconds(1799)));
        assertEquals("00:01", Durations.clock(Duration.ofMillis(1)));
        assertEquals("1:02:03", Durations.clock(Duration.ofSeconds(3723)));
        assertEquals("00:00", Durations.clock(Duration.ofSeconds(-5)));
    }

    @Test
    void wordsForAnnouncements() {
        assertEquals("15 min", Durations.words(Duration.ofMinutes(15)));
        assertEquals("1 h 05", Durations.words(Duration.ofMinutes(65)));
        assertEquals("1 h", Durations.words(Duration.ofHours(1)));
        assertEquals("1 min 30 s", Durations.words(Duration.ofSeconds(90)));
        assertEquals("30 s", Durations.words(Duration.ofSeconds(30)));
        assertEquals("2 j 3 h", Durations.words(Duration.ofHours(51)));
        assertEquals("1 j", Durations.words(Duration.ofDays(1)));
        assertEquals("0 s", Durations.words(Duration.ZERO));
    }

    @Test
    void countdownIsShort() {
        assertEquals("4m 12s", Durations.countdown(Duration.ofSeconds(252)));
        assertEquals("45s", Durations.countdown(Duration.ofSeconds(45)));
        assertEquals("1h 05m", Durations.countdown(Duration.ofMinutes(65)));
        assertEquals("1s", Durations.countdown(Duration.ofMillis(10)));
    }
}
