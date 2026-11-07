package fr.beltaria.commons.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;

class PlaceholdersTest {

    @Test
    void replacesEveryOccurrenceOfEachName() {
        assertEquals("Bob paie 5 a Bob", Placeholders.fill("{player} paie {amount} a {player}", "player", "Bob", "amount", 5));
    }

    @Test
    void aValueIsNeverReadAgain() {
        // Un item renomme "{price}" a l'enclume ne doit pas afficher le prix a sa place.
        assertEquals("Vendu {price} pour 10", Placeholders.fill("Vendu {item} pour {price}", "item", "{price}", "price", 10));
    }

    @Test
    void anUnknownNameStaysVisible() {
        assertEquals("Salut Bob, {oubli}", Placeholders.fill("Salut {player}, {oubli}", "player", "Bob"));
    }

    @Test
    void bracesThatAreNotPlaceholdersStay() {
        assertEquals("{Bob} } {", Placeholders.fill("{{player}} } {", "player", "Bob"));
        assertEquals("a { b Bob", Placeholders.fill("a { b {player}", "player", "Bob"));
        assertEquals("{} Bob", Placeholders.fill("{} {player}", "player", "Bob"));
    }

    @Test
    void nullValuesAreWrittenAsNull() {
        assertEquals("x=null", Placeholders.fill("x={x}", "x", null));
    }

    @Test
    void withoutPairsTheTemplateIsReturnedAsIs() {
        String template = "{player}";
        assertSame(template, Placeholders.fill(template));
    }

    @Test
    void oddPairsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> Placeholders.fill("{a}", "a"));
        assertThrows(IllegalArgumentException.class, () -> Placeholders.fill("rien", "a", 1, "b"));
    }

    @Test
    void aNonTextualNameIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> Placeholders.fill("{1}", 1, "x"));
    }

    @Test
    void aNameGivenTwiceIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> Placeholders.fill("{a}", "a", 1, "a", 2));
    }

    @Property
    void aTemplateWithoutBracesIsUnchanged(@ForAll String template, @ForAll @AlphaChars @StringLength(min = 1, max = 8) String name,
                                           @ForAll String value) {
        String plain = template.replace("{", "").replace("}", "");
        assertEquals(plain, Placeholders.fill(plain, name, value));
    }

    @Property
    void anyValueIsInsertedVerbatim(@ForAll String before, @ForAll String after, @ForAll String value,
                                    @ForAll @AlphaChars @StringLength(min = 1, max = 8) String name) {
        String prefix = before.replace("{", "").replace("}", "");
        String suffix = after.replace("{", "").replace("}", "");
        assertEquals(prefix + value + suffix, Placeholders.fill(prefix + "{" + name + "}" + suffix, name, value));
    }
}
