# BeltariaCommons

Outils **sans état** partagés par les plugins Beltaria, pour que tous lisent leurs textes et leurs durées de la même façon. Ce n'est pas un plugin : chaque plugin embarque la bibliothèque dans son jar, relocalisée dans son propre paquet.

Ce qui a un état ou un propriétaire ne va pas ici, mais dans le plugin qui en est responsable :

- l'argent dans BeltariaEconomy ;
- les items dans BeltariaContent ;
- les classements dans BeltariaClassements.

## Contenu

| Classe | Rôle |
|---|---|
| `fr.beltaria.commons.lang.Lang` | Textes de `lang.yml`. Les clés absentes du fichier sont complétées par le jar, les codes `&` deviennent des couleurs, les `{placeholders}` sont remplacés et `prefix` précède chaque ligne envoyée. Un message vide n'est pas envoyé. Un fichier illisible fait échouer le chargement. `unknownKeys()` liste les fautes de frappe. |
| `fr.beltaria.commons.text.Placeholders` | Remplace les `{nom}` en une seule passe : une valeur insérée n'est jamais relue. Un `{nom}` sans valeur reste visible. Des paires impaires ou un nom en double lèvent une exception. |
| `fr.beltaria.commons.time.Durations` | Lit les durées `30m`, `1h30m`, `2d`, `45s` et les affiche sous quatre formes : `compact` (relisible), `clock` (`29:59`), `words` (`1 h 05`) et `countdown` (`4m 12s`). |

Dans un message, la couleur est appliquée au texte du fichier **avant** de remplacer les placeholders. Une valeur, comme un nom d'item renommé à l'enclume ou un pseudo, n'est donc jamais colorisée par ses `&`. Une valeur qui doit être colorée est passée déjà colorée, avec des `§`.

## Utiliser la bibliothèque

```kotlin
plugins {
    id("com.gradleup.shadow") version "9.2.2"
}

repositories {
    maven("https://raw.githubusercontent.com/Beltaria/beltariacommons/maven/") {
        content { includeModule("fr.beltaria", "commons") }
    }
}

dependencies {
    implementation("fr.beltaria:commons:1.0.0")
}

tasks.shadowJar {
    // Obligatoire : deux plugins avec deux versions différentes ne doivent jamais partager les mêmes classes.
    relocate("fr.beltaria.commons", "fr.beltaria.<plugin>.libs.commons")
}
```

```java
Lang lang = Lang.load(plugin);              // IOException si lang.yml est cassé : le plugin refuse de démarrer
lang.unknownKeys().forEach(key -> plugin.getLogger().warning("lang.yml : clé inconnue " + key));
lang.send(player, "sold", "item", name, "price", price);
Duration cooldown = Durations.parsePositive(config.getString("cooldown", "30m"));
```

## Versions

Une version est publiée par le tag `vX.Y.Z`, dans la branche `maven` de ce dépôt (branche orpheline créée une fois pour toutes avec le dépôt, sans code). Une fois publiée, elle n'est jamais réécrite. Les plugins fixent une version exacte. Une version majeure signale qu'une signature a changé.

Pour compiler : `./gradlew build` (Java 25, tests JUnit 5 + jqwik + Mockito).
