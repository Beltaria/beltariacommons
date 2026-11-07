package fr.beltaria.commons.lang;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import fr.beltaria.commons.text.Placeholders;

/**
 * Les textes d'un plugin, lus dans {@code lang.yml}. Meme regles partout :
 * <ul>
 * <li>une cle absente du fichier du serveur prend la valeur livree dans le jar : une mise a jour qui ajoute un
 * message n'oblige pas a toucher au fichier ;</li>
 * <li>les codes {@code &} du texte sont colores, puis les placeholders {@code {nom}} remplaces en une passe
 * ({@link Placeholders#fill}) : une valeur inseree (nom d'item renomme a l'enclume, pseudo) n'est jamais coloree
 * ni relue ;</li>
 * <li>{@code prefix} precede chaque message envoye par {@link #send} ; un message vide dans le fichier n'est pas
 * envoye, c'est ainsi qu'un admin coupe un message ;</li>
 * <li>un fichier illisible, ou dont une cle contredit la forme du jar, est une erreur ({@link #load} leve), jamais
 * un repli silencieux sur les textes du jar.</li>
 * </ul>
 * Immuable apres chargement : lisible depuis n'importe quel thread. Un rechargement construit une nouvelle instance.
 */
public final class Lang {

    /** Nom du fichier, dans le dossier du plugin et a la racine du jar. */
    public static final String FILE = "lang.yml";

    private static final String PREFIX = "prefix";

    private final Configuration texts;

    private Lang(Configuration texts) {
        this.texts = texts;
    }

    /**
     * Lit {@code lang.yml} du dossier du plugin, ecrit d'abord celui du jar s'il manque. Les textes du jar
     * completent les cles absentes.
     *
     * @throws IOException si le fichier ne se lit pas, n'est pas du YAML valide, ou si une cle contredit la forme du
     *         jar (section a la place d'un texte, ou l'inverse) ; le message nomme le fichier et les cles
     * @throws IllegalStateException si le jar ne contient pas de {@code lang.yml} (erreur de build)
     */
    public static Lang load(Plugin plugin) throws IOException {
        File file = new File(plugin.getDataFolder(), FILE);
        if (!file.isFile()) {
            plugin.saveResource(FILE, false);
        }
        YamlConfiguration texts = new YamlConfiguration();
        try {
            texts.load(file);
        } catch (InvalidConfigurationException invalid) {
            throw new IOException(file.getPath() + " n'est pas du YAML valide : " + invalid.getMessage(), invalid);
        }
        YamlConfiguration defaults = bundled(plugin);
        List<String> problems = shapeProblems(texts, defaults);
        if (!problems.isEmpty()) {
            throw new IOException(file.getPath() + " : " + String.join(" ; ", problems));
        }
        texts.setDefaults(defaults);
        return new Lang(texts);
    }

    /**
     * Les cles du fichier du serveur dont la forme contredit le jar : une section la ou le jar a un texte (un texte
     * indente par erreur sous sa cle), ou un texte la ou le jar a une section. Sans ce controle, le joueur verrait
     * {@code [cle]} ou le texte du jar, et la console un simple avertissement.
     */
    static List<String> shapeProblems(ConfigurationSection server, ConfigurationSection defaults) {
        List<String> problems = new ArrayList<>();
        for (String key : defaults.getKeys(true)) {
            // Appele avant setDefaults : contains ne voit que le fichier du serveur.
            if (!server.contains(key)) {
                continue;
            }
            boolean jarSection = defaults.isConfigurationSection(key);
            boolean serverSection = server.isConfigurationSection(key);
            if (jarSection && !serverSection) {
                problems.add("'" + key + "' doit etre une section (cles indentees en dessous), pas un texte");
            } else if (!jarSection && serverSection) {
                problems.add("'" + key + "' doit etre un texte ou une liste de lignes, pas une section");
            }
        }
        return problems;
    }

    /** Textes deja lus, {@code defaults} completant les cles absentes de {@code texts} (tests, sources non fichier). */
    public static Lang of(ConfigurationSection texts, ConfigurationSection defaults) {
        YamlConfiguration merged = new YamlConfiguration();
        copy(texts, merged);
        YamlConfiguration fallback = new YamlConfiguration();
        copy(defaults, fallback);
        merged.setDefaults(fallback);
        return new Lang(merged);
    }

    private static YamlConfiguration bundled(Plugin plugin) throws IOException {
        try (InputStream in = plugin.getResource(FILE)) {
            if (in == null) {
                throw new IllegalStateException("le jar de " + plugin.getName() + " ne contient pas " + FILE);
            }
            YamlConfiguration defaults = new YamlConfiguration();
            defaults.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return defaults;
        } catch (InvalidConfigurationException invalid) {
            throw new IllegalStateException(FILE + " du jar de " + plugin.getName() + " invalide : " + invalid.getMessage(),
                    invalid);
        }
    }

    private static void copy(ConfigurationSection from, YamlConfiguration to) {
        for (String key : from.getKeys(true)) {
            if (!from.isConfigurationSection(key)) {
                to.set(key, from.get(key));
            }
        }
    }

    /**
     * Le texte de {@code key}, colore, placeholders remplaces. Une liste est jointe par des retours a la ligne. Une
     * cle inconnue du fichier et du jar rend {@code [key]}, visible en jeu.
     *
     * @param pairs nom, valeur, nom, valeur... (voir {@link Placeholders#fill})
     */
    public String text(String key, Object... pairs) {
        if (this.texts.isList(key)) {
            return String.join("\n", lines(key, pairs));
        }
        String raw = scalar(key);
        return raw == null ? "[" + key + "]" : render(raw, pairs);
    }

    /** Les lignes de {@code key} (une liste, ou un texte seul vu comme une ligne), colorees et remplies. */
    public List<String> lines(String key, Object... pairs) {
        if (!this.texts.isList(key)) {
            String raw = scalar(key);
            return List.of(raw == null ? "[" + key + "]" : render(raw, pairs));
        }
        List<String> out = new ArrayList<>();
        for (Object line : this.texts.getList(key)) {
            out.add(render(String.valueOf(line), pairs));
        }
        return List.copyOf(out);
    }

    /** {@code prefix} colore, vide s'il n'est defini nulle part. */
    public String prefix() {
        // Jamais getString(PREFIX, "") : Bukkit rend alors "" sans consulter les textes du jar.
        String prefix = this.texts.getString(PREFIX);
        return prefix == null ? "" : colour(prefix);
    }

    /**
     * Envoie chaque ligne de {@link #lines} precedee de {@link #prefix()} ; rien si le texte est vide dans la
     * configuration. Une ligne vide d'une liste est envoyee sans prefixe (ligne d'espacement).
     */
    public void send(CommandSender to, String key, Object... pairs) {
        List<String> lines = lines(key, pairs);
        if (lines.size() == 1 && lines.get(0).isEmpty()) {
            return;
        }
        String prefix = prefix();
        for (String line : lines) {
            to.sendMessage(line.isEmpty() ? line : prefix + line);
        }
    }

    /** Si {@code key} existe dans le fichier ou dans le jar. */
    public boolean has(String key) {
        return this.texts.contains(key);
    }

    /**
     * Les cles du fichier du serveur que le jar ne connait pas : une faute de frappe, ou un texte qu'une version a
     * retire. Le plugin les signale au chargement ; elles ne sont jamais lues.
     */
    public Set<String> unknownKeys() {
        Configuration defaults = this.texts.getDefaults();
        Set<String> unknown = new TreeSet<>();
        if (defaults == null) {
            return unknown;
        }
        for (String key : this.texts.getKeys(true)) {
            if (!this.texts.isConfigurationSection(key) && !defaults.contains(key)) {
                unknown.add(key);
            }
        }
        return unknown;
    }

    /** Le texte de {@code key}, null si absent ou si c'est une section (getString rendrait son toString). */
    private String scalar(String key) {
        return this.texts.isConfigurationSection(key) ? null : this.texts.getString(key);
    }

    private static String render(String raw, Object... pairs) {
        return Placeholders.fill(colour(raw), pairs);
    }

    /** Les codes {@code &} en couleurs Minecraft. */
    public static String colour(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
