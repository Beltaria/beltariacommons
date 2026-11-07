# Conventions des plugins Beltaria

Ces règles valent pour tous les plugins Beltaria, y compris KOTH. Un admin qui connaît un plugin doit retrouver ses repères dans tous les autres. Toute nouvelle fonction les suit dès le départ ; un écart se justifie dans le README du plugin.

`<plugin>` désigne le nom du plugin sans « Beltaria », en minuscules : `auction`, `portails`, `runes`, `core`, `content`, `koth`.

## 1. Permissions

- Forme : `beltaria.<plugin>.<action>`, par exemple `beltaria.auction.use`, `beltaria.portails.admin` ou `beltaria.koth.top`.
- Chaque permission est déclarée dans `plugin.yml`, avec une `description` et un `default`. La permission `beltaria.<plugin>.*` est déclarée et regroupe toutes les autres.
- `beltaria.<plugin>.admin` couvre les commandes d'administration. Ce qu'un joueur fait normalement est `default: true`, sauf choix contraire documenté.
- Une permission à valeur (limite, palier) prend la forme `beltaria.<plugin>.<chose>.<valeur>`, par exemple `beltaria.auction.limit.15`.

## 2. Placeholders PlaceholderAPI

- Identifiant : `beltaria<plugin>`, ce qui donne `%beltariaoutpost_<nom>%` ou `%beltariacontent_glyph_damage%`.
- Les noms sont en `snake_case` anglais. Les placeholders indexés suivent la forme `top_<n>_<champ>`.
- Un placeholder inconnu rend une chaîne vide, jamais `null`.

## 3. Commandes

- Le nom principal de chaque commande est déclaré dans `plugin.yml`.
- Une commande de joueur au nom anglais reçoit un alias français : `/portal` → `/portail`, `/trade` → `/echange`, `/sell` → `/vendre`.
- Un alias ne doit jamais prendre le nom d'une commande d'un autre plugin de la prod (Essentials, Factions, Jobs…). Vérifie avant d'en ajouter un.
- Les sous-commandes s'écrivent en minuscules et la complétion par tabulation ne propose que ce que le joueur a le droit de faire.

## 4. Textes

- Tout texte destiné à un joueur ou à un admin en jeu vient de `lang.yml` et est lu par `fr.beltaria.commons.lang.Lang`. Aucun texte en dur dans le code, aucun texte dans `config.yml`.
- Les placeholders s'écrivent `{nom}` en anglais (`{player}`, `{amount}`, `{faction}`). Plus de `%nom%` ni de `<nom>` dans les textes.
- Les couleurs utilisent `&`. La clé `prefix` précède chaque message envoyé, et un message laissé vide (`""`) n'est pas envoyé : c'est ainsi qu'un admin coupe un message.
- Les textes **tutoient** le joueur : « Tu n'as pas assez d'argent », jamais « Vous ».
- Les journaux de la console sont écrits pour l'admin. Ils nomment le fichier, la clé et la valeur en cause.

## 5. config.yml

- Les clés sont en anglais, en `kebab-case`. Une valeur affichée au joueur (nom d'objet, de lieu) reste en français, mais sa clé est anglaise.
- Les durées sont du texte au format `Durations` : `30s`, `5m`, `1h30m`, `2d`. La clé ne porte pas d'unité : `cooldown: 30s`, et non `cooldown-seconds: 30`. Exception : un réglage d'animation inférieur à la seconde peut rester en ticks, avec le suffixe `-ticks` (`hold-ticks: 40`).
- Les heures et les jours sont lus en `Europe/Paris`, par `fr.beltaria.commons.time.Weekly`. Aucune clé de fuseau horaire.
- Les montants sont des nombres, en monnaie BeltariaEconomy, sans symbole.
- Une clé absente prend la valeur par défaut du jar, que le fichier de départ documente en commentaire.

## 6. Configuration cassée : politique stricte

- **Au démarrage**, un `config.yml` ou un `lang.yml` illisible, une valeur invalide (durée mal écrite, matériau inconnu, nombre hors bornes) ou une clé obligatoire absente désactive le plugin. L'erreur nomme le fichier, la clé, la valeur et ce qui est attendu. Jamais de repli silencieux sur une valeur par défaut.
- **Au rechargement** (`/<plugin> reload`), si la nouvelle configuration est invalide, l'ancienne reste active et l'erreur est envoyée à l'auteur de la commande.
- **Dans les fichiers de contenu** (une caisse, un outpost, un item), une entrée invalide est refusée seule et nommée. Les autres entrées se chargent.
- Une clé inconnue (faute de frappe, clé retirée) donne un avertissement au chargement, sans blocage.

## 7. Événements (KOTH, Totem, Outpost, Duels)

- **Planning** : `schedule.entries`, une liste d'entrées hebdomadaires lues par `Weekly`.
  - `days` : une liste (`[samedi, dimanche]`, en français ou en anglais). Clé absente, `"*"`, `["*"]` ou `[tous]` : tous les jours (`*` toujours entre guillemets : seul, YAML le lit comme un alias). Une liste vide est refusée.
  - `time` (un lancement) ou `from`/`to` (un créneau) : `"HH:mm"` entre guillemets ; `to` accepte `"24:00"`.
  - Pas de planning (Duels, lancé par les joueurs) : pas de clé `schedule`.
  - Exception documentée : KOTH garde son `schedule.json`.
- **Annonces avant le début** : `announce-before`, une liste de durées strictement positives (`[15m, 5m, 1m]`) ; `[]` n'annonce rien.
- **Commandes de récompense** : commandes console, sous `rewards`.
  - `winner-commands` : une fois, pour le gagnant.
  - `member-commands` : une fois par membre en ligne de la faction gagnante.
  - Placeholders `{player}` et `{faction}`, plus ceux propres à l'événement.
- **Début** : chaque événement appelle `EventStartedEvent(type, nom)` de BeltariaClassements, sur le fil principal, au moment où il devient jouable.
  - `koth` : au lancement.
  - `totem` : au lancement.
  - `outpost` : à la fin d'un créneau désactivé.
  - Duels appelle `DuelStartedEvent`.

## 8. Build

- Le code est compilé en Java 25 (`options.release = 25`).
- La version vient du tag `vX.Y.Z` (`-PpluginVersion` en CI).
- Les tests utilisent JUnit 5, jqwik et Mockito.
- BeltariaCommons est embarqué avec shadow et relocalisé dans `fr.beltaria.<plugin>.libs.commons`.
- Les Actions GitHub sont épinglées sur des SHA. Les tests tournent à chaque push.
