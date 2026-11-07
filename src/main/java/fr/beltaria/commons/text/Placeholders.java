package fr.beltaria.commons.text;

import java.util.HashMap;
import java.util.Map;

/** Les placeholders {@code {nom}} des textes de configuration. Sans etat, utilisable depuis n'importe quel thread. */
public final class Placeholders {

    private Placeholders() {
    }

    /**
     * Remplace chaque {@code {nom}} du modele par la valeur qui suit {@code nom} dans {@code pairs}, en une seule
     * passe : une valeur inseree n'est jamais relue, donc un nom d'item contenant {@code {player}} reste tel quel.
     * Un {@code {nom}} sans valeur reste intact, pour que l'oubli se voie en jeu.
     *
     * @param pairs nom, valeur, nom, valeur... ; un nom est un {@link String}, une valeur passe par
     *              {@link String#valueOf(Object)}
     * @throws IllegalArgumentException si {@code pairs} est impair, si un nom n'est pas une chaine ou apparait deux fois
     */
    public static String fill(String template, Object... pairs) {
        Map<String, String> values = values(pairs);
        if (values.isEmpty() || template.indexOf('{') < 0) {
            return template;
        }
        StringBuilder out = new StringBuilder(template.length() + 32);
        int from = 0;
        while (from < template.length()) {
            int open = template.indexOf('{', from);
            if (open < 0) {
                break;
            }
            int close = template.indexOf('}', open + 1);
            if (close < 0) {
                break;
            }
            int nested = template.indexOf('{', open + 1);
            if (nested >= 0 && nested < close) {
                // "{{player}" : le premier '{' est du texte, le placeholder commence au suivant.
                out.append(template, from, nested);
                from = nested;
                continue;
            }
            String value = values.get(template.substring(open + 1, close));
            out.append(template, from, open);
            if (value != null) {
                out.append(value);
            } else {
                out.append(template, open, close + 1);
            }
            from = close + 1;
        }
        return out.append(template, from, template.length()).toString();
    }

    private static Map<String, String> values(Object... pairs) {
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("placeholders par paires nom/valeur, recu " + pairs.length + " elements");
        }
        Map<String, String> values = new HashMap<>(pairs.length);
        for (int i = 0; i < pairs.length; i += 2) {
            if (!(pairs[i] instanceof String name)) {
                throw new IllegalArgumentException("nom de placeholder non textuel en position " + i + " : " + pairs[i]);
            }
            if (values.put(name, String.valueOf(pairs[i + 1])) != null) {
                throw new IllegalArgumentException("placeholder {" + name + "} donne deux fois");
            }
        }
        return values;
    }
}
