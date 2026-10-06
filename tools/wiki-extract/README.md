# Slayer location data builder

Builds `src/main/resources/slayer_areas.json`: every place a Slayer task can be done, with a world map
marker point, kill spots for Shortest Path, cannon and combat facts, and the quests and levels a player
needs to get in. Every point and fact in the output carries its source.

## Inputs

| File | What it holds |
| --- | --- |
| `../cache-dump/out/slayer_tables.json` | Game cache dump owned by the cache-dump tool. Gives each SlayerArea row's area id and name, and Konar's allowed areas per task id. Read only. Not in the repository. Run the cache-dump tool first (see its README). |
| `runelite_map_data.tsv` | RuneLite 1.13.1 world map data (dungeon icons, transports, fairy rings, teleports). Made by `dump_runelite_map_data.sh`. |
| `cache/` | OSRS Wiki wikitext, fetched by `fetch_pages.py`. `cache/manifest.json` maps requested titles to fetched pages and dates. Not in the repository. |
| `curated.json` | Hand-picked inputs: tasks to extract (task id, wiki task guide, monster pages), location keys and labels, wiki name aliases, entrances, facts and notes. Each entrance and fact names its source. |
| `access.json` | What each place needs to get in, read from the wiki, with the quoted sentence or infobox field each entry comes from. See Access below. |
| `work/walkable.tsv`, `work/reachable.tsv` | Results of `check_walkable.sh` against the installed Shortest Path jar. Not in the repository. |

## Run

From Git Bash in this folder:

```
python fetch_pages.py          # downloads missing wiki pages, 1 request per second
python build_slayer_areas.py   # writes work/candidates.tsv
bash check_walkable.sh         # checks the candidates against the Shortest Path jar (read only)
python build_slayer_areas.py   # writes the JSON and report.txt
```

The Python scripts need Python 3 and nothing else. The two shell scripts work in Git Bash on Windows and
in any other bash. They hold no paths of their own. They read these from the environment:

| Variable | Default | What it is |
| --- | --- | --- |
| `JAVA_HOME` | unset, so `java` and `javac` come from `PATH` | A JDK 11 or newer. |
| `GRADLE_USER_HOME` | `~/.gradle` | Gradle's home. The RuneLite client and API jars are read from its cache. Any Gradle build that used that RuneLite version, such as the plugin's own, puts them there. |
| `RL_VERSION` | `1.13.1` | The RuneLite version whose jars are read from that cache. |
| `SHORTEST_PATH_JAR` | the first `~/.runelite/plugins/shortest-path_*.jar` | The Shortest Path jar `check_walkable.sh` reads. RuneLite keeps Plugin Hub jars in that folder. |

For example `JAVA_HOME=/path/to/jdk-11 bash check_walkable.sh`.

Run the check and the build a second time when entrances changed, because entrance neighbourhoods are
added to the candidates on the first build. Then run the plugin tests. `SlayerLocationsTest` fails on
missing sources, bad values, Konar coverage regressions and disagreements with `TaskDestination`.

`report.txt` lists Konar coverage, place coverage per master (pool tasks with two or more places, one
place, none), Krystilia's Wilderness coverage, spawn lists that were dropped and why, unreachable entrances,
cannon conflicts left for review, and how many places can lock, carry a note only, need nothing or are not
settled, with the keys of the last group.

`fetch_pages.py` reads its titles from `curated.json` and from the pages `access.json` cites, and skips a
page already cached under its own title. It sends the User-Agent
`SlayerCompanion-data-builder/1.0 (personal RuneLite plugin)` and nothing else that identifies anyone.

## How kill spots are chosen

Spawn tiles come only from `LocLine` templates on monster pages.

1. A whole spawn list is dropped when fewer than 60% of its tiles are walkable in Shortest Path's
   collision map. Such lists are in world map display coordinates or on the wrong plane (the God Wars
   Dungeon, Chasm of Fire, Karuulm and Kalphite lists on the wiki are examples), and their few walkable
   tiles may belong to some other place.
2. Tiles Shortest Path cannot reach are dropped. `ReachabilityCheck.java` floods Shortest Path's map
   with its own movement rules and transport tables, ignoring all requirements. A route to a tile it
   cannot reach would end somewhere unrelated, so those places route to their entrance instead.
3. The remaining tiles are grouped (12 tile gap, same plane) and each group is represented by its
   medoid, which is one of the listed tiles. At most 6 spots are kept per task and place.

An entrance with no reachable tile within 8 tiles gets `"entranceRoutable": false`. It still shows on
the world map but is never sent to Shortest Path. A place without an entrance is marked at the task's
first kill spot on the surface map (`Destination` in the plugin), so most places found only through spawn
lists still get a marker.

## Extending to tasks Konar does not assign

1. Find the task id in the cache dump: `masters[].tasks[].taskRow` is a SlayerTask row, whose `COL_ID`
   (in `tables.SlayerTask.rows`) is the task id.
2. Add an entry to `tasks` in `curated.json` with the wiki task guide page (when one exists, and not a
   redirect to a monster page) and the monster pages whose `LocLine` templates list the spawns.
3. Run the steps above. New places get a key made from the wiki location name. Add an alias when a wiki
   name should map to an existing key, and an entrance (RuneLite point or a captioned wiki map) when
   the automatic RuneLite name match finds none.

Task options in `curated.json`:

| Option | Effect |
| --- | --- |
| `placesFromGuide` | List only the places the task guide names: its location comparison rows, or without one the Locations column of its monster table. Spawn lists then only add kill spots to those places (matched by name, or by wiki link when one guide place has it). The place the dossier names is kept even when the guide leaves it out. Used for tasks whose monsters spawn all over the map, such as rats and birds. |
| `places` | Places a wiki page names only in its text, each with `text`, `link`, `note` and `noteSource`. Example: the Canifis werewolves. |
| `excludeRows` | Regular expressions for guide rows and spawn list locations to leave out, such as boss lairs. |
| `dossierName` | The dossier entry to read when its name differs from the task name. |

## Krystilia's tasks

Her tasks only count in the Wilderness, so they get their own lists in `wildernessTaskLocations`, built
from the Location column of the Tasks table on her wiki page, which names only places within the
Wilderness. The `krystilia` section of `curated.json` maps her table's monsters to task ids, adds spawn
pages for her own tasks, and maps her place names to location keys.

Every point in her lists is checked again:

- A kill spot must lie inside RuneLite 1.13.1's own Wilderness areas (`NpcAggroAreaPlugin`
  WILDERNESS_ABOVE_GROUND and WILDERNESS_UNDERGROUND), or in a place listed in `wholePlaces` whose wiki
  page says all of it lies in the Wilderness. Only the Wilderness Slayer Cave needs that, because RuneLite's
  underground area ends west of it.
- The marker point is the place's entrance only when that lies inside RuneLite's Wilderness areas.
- A place whose own points reach outside the Wilderness, such as Edgeville Dungeon, keeps only its
  Wilderness spots and is labelled "(Wilderness part)".

A spawn list matches one of her places by name, or by any wiki link other than the Wilderness itself, as
in "Wilderness - south-east of the Demonic Ruins". A place her page names but no spawn list confirms is
listed by name only, with no marker and no route. `SlayerLocationsTest` checks every point against
RuneLite's areas.

Spawn pins with display fields, such as `x:3234,y:3621,levels:28,icon:bluePin`, are read too.

## Recommended place

Every task list gets a `recommended` place, listed first. It is the place the dossier's best location
names, else the first listed place with a verified point, else the first listed place. The dossier's place
is found through a curated `dossierPlaces` entry (for wording the automatic match cannot see), then the
`dossierPrefixes` that mirror `TaskDestination.forTask`, then the listed place whose name the text
mentions first, skipping advice meant for Krystilia. The plugin falls back to a similar rule when the
field is missing.

## Access

`access.json` says what a player needs to get into a place and reach the task monsters there. The
plugin compares it with the account and stops recommending a place the player cannot enter.

- `locations` holds, per location key, `questsFinished` (quests that must be finished),
  `questsStarted` (quests that must at least be started, for places the wiki opens during a quest or
  after partial completion), `skills` (real levels), `note` and `sources`. `tasks` holds the same per
  task id when only that task's monsters lie behind a further gate, such as the dwarves inside
  Keldagrim, whose trolls live in the entrance tunnel.
- `open` lists the places the wiki shows to need nothing, each with the sentence that shows how the
  place is reached. A place in neither list is not settled, and nothing locks it.

Quest names are `net.runelite.api.Quest` constants and skill names `net.runelite.api.Skill` constants.
`SlayerLocationsTest` fails on a name that is not one.

Only hard gates the wiki states are kept. Optional shortcuts, entry fees and items to wear or carry are
left out. A gate with another way around it, a skill level the wiki does not say cannot be boosted, and
achievement diaries or Combat Achievements can only be a `note`, which the plugin shows and never locks
on. A Sailing level to land on an island locks, because the wiki's Mooring point page says
disembarking is not boostable. Where the wiki says partial completion or during the quest, the place
needs the quest started, the lowest progress that can be read.

Each source is `{"page", "quote"}` or `{"page", "field", "value"}`. A quote is plain text: links show
their text, formatting and references are gone, `{{SCP|Agility|60}}` reads as "60 Agility" and
`{{Boostable|no}}` as "(not boostable)" (`wikiparse.plain_text`). A field names a field of the page's
first infobox, and its value must be part of it. The build checks every source against the cached page
and leaves out an entry whose source it cannot find, with an `ACCESS DROPPED` line in `report.txt`, so a
page that changed never locks a place on old words.

Konar's assigned area is never checked. Her page says the roll for the area "will check any other
additional requirements if a location is locked behind a quest", so she assigns only areas the player
can enter.

## Output shape

```
locations[]       key, label, areaId (game Slayer area, only for areas Konar can assign), row,
                  entrance [x, y, plane], entranceLabel, entranceSource, entranceRoutable,
                  cannon yes|no, cannonNote, combat multi|single|mixed, note, each with a source,
                  access: questsFinished, questsStarted, skills, note, source,
                  tasks[]: taskId, task, spots [[x, y, plane]], spotsSource, task-level facts, access
taskLocations[]   taskId, task, locations (keys in display order, recommended first), recommended, source
wildernessTaskLocations[]
                  taskId, task, recommended, source, places[]: key, label (her page's name), entrance and
                  spots checked to lie in the Wilderness, each with a source
```
