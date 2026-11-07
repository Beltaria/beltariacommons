package fr.beltaria.commons.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
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

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "30", "m", "30 m", "1m1h", "-5m", "1.5h", "30min", "1h 30m", "1000d", "1w"})
    void refusesAnythingElse(String text) {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> Durations.parse(text));
        assertEquals(true, refused.getMessage().contains("\"" + text + "\""), refused.getMessage());
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
    }

    @Test
    void compactRefusesWhatParseCannotReadBack() {
        assertThrows(IllegalArgumentException.class, () -> Durations.compact(Duration.ofSeconds(-1)));
        assertThrows(IllegalArgumentException.class, () -> Durations.compact(Duration.ofMillis(1500)));
        assertThrows(IllegalArgumentException.class, () -> Durations.compact(Duration.ofDays(1000)));
    }

    @Property
    void compactReadsBackToTheSameDuration(@ForAll @LongRange(min = 0, max = 1000L * 86_400 - 1) long seconds) {
        Duration duration = Duration.ofSeconds(seconds);
        assertEquals(duration, Durations.parse(Durations.compact(duration)));
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
