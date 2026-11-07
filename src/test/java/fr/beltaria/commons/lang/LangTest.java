package fr.beltaria.commons.lang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LangTest {

    private static final String BUNDLED = """
            prefix: "&6[Test] "
            hello: "&aSalut {player}"
            bye: "Au revoir"
            muted: "x"
            help:
              - "&eLigne {n}"
              - "Fin"
            menu:
              title: "&8Menu"
            """;

    @TempDir
    Path folder;

    private static YamlConfiguration yaml(String text) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(text);
        } catch (org.bukkit.configuration.InvalidConfigurationException invalid) {
            throw new AssertionError(invalid);
        }
        return yaml;
    }

    private static Lang lang(String server) {
        return Lang.of(yaml(server), yaml(BUNDLED));
    }

    @Test
    void theServerFileWinsAndTheJarFillsMissingKeys() {
        Lang lang = lang("hello: \"&cYo {player}\"\n");
        assertEquals("§cYo Bob", lang.text("hello", "player", "Bob"));
        assertEquals("Au revoir", lang.text("bye"));
        assertEquals("§8Menu", lang.text("menu.title"));
    }

    @Test
    void valuesAreNeitherColouredNorReadAgain() {
        // Un item renomme "&kX{player}" a l'enclume reste tel quel dans le message.
        assertEquals("§aSalut &kX{player}", lang("").text("hello", "player", "&kX{player}"));
    }

    @Test
    void anUnknownKeyIsVisible() {
        assertEquals("[nope]", lang("").text("nope"));
        assertEquals(List.of("[nope]"), lang("").lines("nope"));
        assertFalse(lang("").has("nope"));
        assertTrue(lang("").has("menu.title"));
    }

    @Test
    void listsAreLinesAndASingleTextIsOneLine() {
        Lang lang = lang("");
        assertEquals(List.of("§eLigne 1", "Fin"), lang.lines("help", "n", 1));
        assertEquals(List.of("Au revoir"), lang.lines("bye"));
        assertEquals("§eLigne 2\nFin", lang.text("help", "n", 2));
    }

    @Test
    void sendPrefixesAndAnEmptyTextIsNotSent() {
        Lang lang = lang("muted: \"\"\n");
        CommandSender to = mock(CommandSender.class);
        lang.send(to, "hello", "player", "Bob");
        verify(to).sendMessage("§6[Test] §aSalut Bob");
        lang.send(to, "muted");
        verify(to, never()).sendMessage("§6[Test] ");
    }

    @Test
    void eachLineOfAListIsPrefixedAndEmptyLinesStayBlank() {
        Lang lang = lang("help:\n  - \"&eA {n}\"\n  - \"\"\n  - \"B\"\n");
        CommandSender to = mock(CommandSender.class);
        lang.send(to, "help", "n", 1);
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(to);
        order.verify(to).sendMessage("§6[Test] §eA 1");
        order.verify(to).sendMessage("");
        order.verify(to).sendMessage("§6[Test] B");
        order.verifyNoMoreInteractions();
    }

    @Test
    void aSectionIsNotAText() {
        assertEquals("[menu]", lang("").text("menu"));
        assertEquals(List.of("[menu]"), lang("").lines("menu"));
    }

    @Test
    void unknownKeysOfTheServerFileAreReported() {
        Lang lang = lang("helo: \"typo\"\nmenu:\n  titel: \"x\"\n  title: \"y\"\n");
        assertEquals(Set.of("helo", "menu.titel"), lang.unknownKeys());
    }

    @Test
    void oddPlaceholdersAreAProgrammingError() {
        assertThrows(IllegalArgumentException.class, () -> lang("").text("hello", "player"));
    }

    private Plugin plugin(String bundled) {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getName()).thenReturn("Test");
        when(plugin.getDataFolder()).thenReturn(this.folder.toFile());
        when(plugin.getResource(Lang.FILE)).thenAnswer(call -> bundled == null ? null
                : new ByteArrayInputStream(bundled.getBytes(StandardCharsets.UTF_8)));
        doAnswer(call -> {
            Files.writeString(this.folder.resolve(Lang.FILE), bundled);
            return null;
        }).when(plugin).saveResource(anyString(), org.mockito.ArgumentMatchers.anyBoolean());
        return plugin;
    }

    @Test
    void aMissingFileIsWrittenFromTheJar() throws IOException {
        Plugin plugin = plugin(BUNDLED);
        Lang lang = Lang.load(plugin);
        verify(plugin).saveResource(Lang.FILE, false);
        assertEquals("§aSalut Bob", lang.text("hello", "player", "Bob"));
        assertEquals(Set.of(), lang.unknownKeys());
    }

    @Test
    void anExistingFileIsReadInUtf8AndCompletedByTheJar() throws IOException {
        Files.writeString(this.folder.resolve(Lang.FILE), "hello: \"Été {player}\"\n", StandardCharsets.UTF_8);
        Plugin plugin = plugin(BUNDLED);
        Lang lang = Lang.load(plugin);
        verify(plugin, never()).saveResource(anyString(), org.mockito.ArgumentMatchers.anyBoolean());
        assertEquals("Été Bob", lang.text("hello", "player", "Bob"));
        assertEquals("Au revoir", lang.text("bye"));
    }

    @Test
    void aBrokenFileIsAnErrorNamingTheFile() throws IOException {
        Files.writeString(this.folder.resolve(Lang.FILE), "hello: \"pas fermee\n  - [", StandardCharsets.UTF_8);
        IOException refused = assertThrows(IOException.class, () -> Lang.load(plugin(BUNDLED)));
        assertTrue(refused.getMessage().contains(new File(this.folder.toFile(), Lang.FILE).getPath()), refused.getMessage());
    }

    @Test
    void aTextWrittenAsASectionIsRefused() throws IOException {
        Files.writeString(this.folder.resolve(Lang.FILE), "hello:\n  a: x\n", StandardCharsets.UTF_8);
        IOException refused = assertThrows(IOException.class, () -> Lang.load(plugin(BUNDLED)));
        assertTrue(refused.getMessage().contains("'hello'"), refused.getMessage());
    }

    @Test
    void aSectionWrittenAsATextIsRefused() throws IOException {
        Files.writeString(this.folder.resolve(Lang.FILE), "menu: \"&8Menu\"\n", StandardCharsets.UTF_8);
        IOException refused = assertThrows(IOException.class, () -> Lang.load(plugin(BUNDLED)));
        assertTrue(refused.getMessage().contains("'menu'"), refused.getMessage());
    }

    @Test
    void aListWhereTheJarHasATextIsAccepted() throws IOException {
        Files.writeString(this.folder.resolve(Lang.FILE), "bye:\n  - \"A\"\n  - \"B\"\n", StandardCharsets.UTF_8);
        assertEquals(List.of("A", "B"), Lang.load(plugin(BUNDLED)).lines("bye"));
    }

    @Test
    void aJarWithoutLangIsABuildError() throws IOException {
        Files.writeString(this.folder.resolve(Lang.FILE), "hello: x\n", StandardCharsets.UTF_8);
        assertThrows(IllegalStateException.class, () -> Lang.load(plugin(null)));
    }
}
