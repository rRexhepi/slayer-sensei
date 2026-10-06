# Slayer cache dump

Reads the Slayer database tables and the slayer varbit layout from the local game cache and writes `out/slayer_tables.json`. The plugin reads the same tables at runtime. This dump is how its assumptions about the tables were checked.

This is its own Gradle build. It is not part of the plugin build and the plugin never loads the cache library.

## Re-run after a game update

1. Log in once with RuneLite so the client downloads the new cache.
2. Point `JAVA_HOME` at a Java 11 JDK, then run from this directory:

   ```
   ../../gradlew run
   ```

   Add `--offline` once the dependencies are cached. To match a newer client, pass `-PruneLiteVersion=1.13.2` (or later). That version is used for both the cache library and the gameval names.
3. Compare the new file with the old one (`git diff --no-index` works on two plain files). New rows in `tables.SlayerUnlock` mean a new reward. `SlayerRewardCoverageTest` in the plugin flags those too.
4. Refresh the plugin's test fixture from the same cache, then run the plugin tests:

   ```
   ../../gradlew run --offline -Pfixture=../../src/test/resources/slayer_cache_fixture.json
   ```

   The fixture is a trimmed raw copy of the SlayerUnlock, SlayerTask, SlayerMasterTask and SlayerTaskSublist tables plus the slayer varbits and `COMBAT_LEVEL_REQS_DISABLED`. SlayerTask keeps the combat level, both stat requirement columns and the columns that name the rewards changing a task (`COL_EXTENSION_MIN_MAX`, `COL_EXTENSION_ADDITIVE`, `COL_UNLOCK_WEIGHTING`, `COL_BLOCK_UNLOCK`), SlayerMasterTask the weights, amounts and unlocks of every master's pool, Spria's and Mortimer's included. It also keeps the reward shop's Buy tab enums (`enums`: 840 slot to item, 841 amount, 842 cost). `SlayerGameDataTest`, `SlayerRewardCoverageTest`, `SlayerShopItemTest` and the pipeline tests in `SlayerPointsPlannerTest` read it. They fail when a cost, name, bit, weight, level, extension or Buy tab item moves, which is the cue to update `SlayerReward` or `SlayerShopItem`.

   The goal advice reads it too: `SlayerPoolTest`, `SlayerMasterRankerTest` and `SlayerBlockAdvisorTest`. `SlayerDatasetTest` fails when a master's pool gains a task the dossier has not rated yet. Rate it in `slayer_tasks.json` or `slayer_tasks_supplement.json` (see `TaskRating` for the scale), then check that `SlayerMasterRankerTest` still puts Kuradal first for XP.

   SlayerTaskSublist is the boss list: a boss task's `SLAYER_TARGET_BOSSID` leads through it to the boss's SlayerTask row. `SlayerTaskReaderTest` reads it with SlayerTask to derive every task and boss name the plugin can see, and fails when one is not a name the dossier and gear tables can be keyed by.

   It also holds the multi-NPCs that put a master or a stand-in at each spot (`masterNpcs`, `masterNames`) and the end states of the quests that move them (`quests`). `SlayerMasterAccessTest` checks `SlayerMasterAccess` against them, so it fails when a quest varbit or stage that moves Aya, Achtryn, Kuradal, Steve, Spria or Mortimer changes.

By default the tool reads `.runelite/jagexcache/oldschool/LIVE` in your home folder. Use `-Pcache=DIR` for another cache and `-Pout=FILE` for another output path. The cache files are copied to a temp directory first, so the live cache is never opened for writing and a running client does not get in the way.

Both outputs name the cache they came from without an absolute path or a user name: `~/` and the path under the home folder, or only the folder's name for a cache elsewhere.

## What the file contains

- `about`: when and from which cache the file was made, and how column values are shaped.
- `slayerAreas`: every SlayerArea row with its area id and in-game names.
- `konarTaskAreas`: Konar's task pool. Each task has its weight, amount range, the unlock it needs and its allowed areas, resolved to SlayerArea names.
- `masters`: SlayerMasterTask rows grouped by `COL_MASTER_ID`, with the master name taken from the row names.
- `masterNpcs`: every NPC named like a slayer master or stand-in, with its ops (`assigns` marks the versions that give tasks), and every multi-NPC that turns into one, with the varbit that switches it and the NPC shown for each value. This is where the plugin's rules for Aya, Achtryn, Kuradal, Steve, Spria and Mortimer come from.
- `masterQuests`: the quests that move a master or decide whether one gives tasks, with their end state from the Quest table.
- `shopBuyTab`: the reward shop's Buy tab, which client script 322 draws from enum 840 (the item in each slot), 842 (its cost in points), 841 (how many one purchase gives) and 843 (the description). Each item is listed with its name.
- `unlockBitCheck`: each reward's `COL_BIT` read as a bit index into the three unlock varps, with the varbit at that bit. It is also read as a varbit id, which shows why that reading is wrong.
- `varbits`: every slayer varbit with its base varp and bit range, and every varbit packed into the slayer varps.
- `tables`: the full SlayerUnlock, SlayerTask, SlayerMasterTask, SlayerArea, SlayerTaskSublist and SlayerModifiers tables. Each table lists its columns with ids, gameval names and types, then every row with its row id, gameval name and all column values.
- `warnings`: anything that did not line up, such as a gameval column missing from the cache. It should be empty.

A column value is a list with one entry per stored tuple. A reference to another row is written as `{row, table, name, label}`.
