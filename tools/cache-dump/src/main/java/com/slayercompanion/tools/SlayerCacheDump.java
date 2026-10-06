package com.slayercompanion.tools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.cache.ConfigType;
import net.runelite.cache.DBRowManager;
import net.runelite.cache.DBTableIndexManager;
import net.runelite.cache.DBTableManager;
import net.runelite.cache.EntityOpsDefinition;
import net.runelite.cache.IndexType;
import net.runelite.cache.ItemManager;
import net.runelite.cache.NpcManager;
import net.runelite.cache.definitions.DBRowDefinition;
import net.runelite.cache.definitions.DBTableDefinition;
import net.runelite.cache.definitions.DBTableIndex;
import net.runelite.cache.definitions.EnumDefinition;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.definitions.NpcDefinition;
import net.runelite.cache.definitions.VarbitDefinition;
import net.runelite.cache.definitions.loaders.EnumLoader;
import net.runelite.cache.definitions.loaders.VarbitLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.ArchiveFiles;
import net.runelite.cache.fs.FSFile;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;
import net.runelite.cache.util.ScriptVarType;

/**
 * Dumps the Slayer database tables and the slayer varbit layout from the local game cache to JSON.
 * The cache is copied to a temporary directory first, so the live cache is only ever read.
 *
 * Usage: SlayerCacheDump [--cache DIR] [--out FILE] [--fixture FILE]
 */
public final class SlayerCacheDump
{
	/** Tables to dump, in output order. SlayerModifiers is referenced by SlayerMasterTask.COL_MODIFIER. */
	private static final String[] TABLES = {
		"SlayerUnlock", "SlayerTask", "SlayerMasterTask", "SlayerArea", "SlayerTaskSublist", "SlayerModifiers"};

	/** The three reward unlock varps, in the order a bit index into them would run. */
	private static final int[] UNLOCK_VARPS = {
		VarPlayerID.SLAYER_REWARDS_UNLOCKS, VarPlayerID.SLAYER_REWARDS_UNLOCKS1, VarPlayerID.SLAYER_REWARDS_UNLOCKS2};

	/**
	 * NPC names of the slayer masters and their stand-ins. Every multi-NPC that can turn into one of them is
	 * dumped under masterNpcs, with the varbit or varp that switches it.
	 */
	private static final Set<String> MASTER_NAMES = new TreeSet<>(Arrays.asList("Turael", "Aya", "Spria", "Mazchna",
		"Achtryn", "Vannaka", "Chaeldar", "Nieve", "Steve", "Duradel", "Kuradal", "Konar quo Maten", "Krystilia", "Mortimer"));

	/** The op that marks an NPC version that gives tasks, as opposed to a quest or cutscene copy. */
	private static final String ASSIGN_OP = "Assignment";

	/** Quests whose progress moves a master or decides whether one gives tasks. */
	private static final int[] MASTER_QUESTS = {
		DBTableID.Quest.Row.QUEST_WHILEGUTHIXSLEEPS, DBTableID.Quest.Row.QUEST_MONKEYMADNESS2,
		DBTableID.Quest.Row.QUEST_PORCINEOFINTEREST, DBTableID.Quest.Row.QUEST_FALLENFROMGRACE,
		DBTableID.Quest.Row.QUEST_PRIESTINPERIL, DBTableID.Quest.Row.QUEST_LOSTCITY, DBTableID.Quest.Row.QUEST_SHILOVILLAGE};

	/**
	 * The reward shop's Buy tab. Client script 322 draws it: enum 840 gives the item in each slot, 842 its cost in
	 * points, 841 how many one purchase gives and 843 the description. RuneLite has no gameval names for them.
	 */
	private static final int[] SHOP_ENUMS = {840, 841, 842, 843};

	private final Map<Integer, String> tableNames = new HashMap<>();
	private final Map<Integer, Map<Integer, String>> columnNames = new HashMap<>();
	private final Map<Integer, String> rowNames = new HashMap<>();
	private final Map<Integer, String> varbitNames = new HashMap<>();
	private final Map<Integer, String> varpNames = new HashMap<>();

	private DBTableManager tables;
	private DBRowManager rows;
	private DBTableIndexManager tableIndexes;
	private NpcManager npcs;
	private ItemManager items;
	private final Map<Integer, EnumDefinition> enums = new TreeMap<>();
	private final Map<Integer, VarbitDefinition> varbits = new TreeMap<>();
	private final List<String> warnings = new ArrayList<>();

	public static void main(String[] args) throws Exception
	{
		File cache = new File(System.getProperty("user.home"), ".runelite/jagexcache/oldschool/LIVE");
		File out = new File("out/slayer_tables.json");
		File fixture = null;
		for (int i = 0; i < args.length; i++)
		{
			if ("--cache".equals(args[i]) && i + 1 < args.length) cache = new File(args[++i]);
			else if ("--out".equals(args[i]) && i + 1 < args.length) out = new File(args[++i]);
			else if ("--fixture".equals(args[i]) && i + 1 < args.length) fixture = new File(args[++i]);
			else throw new IllegalArgumentException("Unknown argument " + args[i] + ". Usage: [--cache DIR] [--out FILE] [--fixture FILE]");
		}
		new SlayerCacheDump().run(cache, out, fixture);
	}

	private void run(File cacheDir, File out, File fixtureOut) throws Exception
	{
		final File[] cacheFiles = cacheDir.listFiles((dir, name) -> name.startsWith("main_file_cache."));
		if (cacheFiles == null || cacheFiles.length == 0)
		{
			throw new IOException("No main_file_cache.* files in " + cacheDir.getAbsolutePath());
		}
		loadGamevalNames();

		// The disk storage opens its files read-write. Work on a copy so the live cache is never touched,
		// and so a running game client holding the files open does not matter.
		final Path copy = Files.createTempDirectory("slayer-cache-dump-");
		final JsonObject cacheInfo = new JsonObject();
		cacheInfo.addProperty("source", sourceLabel(cacheDir));
		for (File f : cacheFiles)
		{
			Files.copy(f.toPath(), copy.resolve(f.getName()), StandardCopyOption.COPY_ATTRIBUTES);
			if (f.getName().endsWith(".dat2"))
			{
				cacheInfo.addProperty("dat2Bytes", f.length());
				cacheInfo.addProperty("dat2LastModified", Instant.ofEpochMilli(f.lastModified()).toString());
			}
		}

		final JsonObject root = new JsonObject();
		try (Store store = new Store(copy.toFile()))
		{
			store.load();
			final Index configs = store.getIndex(IndexType.CONFIGS);
			cacheInfo.addProperty("configIndexRevision", configs.getRevision());
			cacheInfo.addProperty("dbrowArchiveRevision", configs.getArchive(ConfigType.DBROW.getId()).getRevision());
			cacheInfo.addProperty("dbtableArchiveRevision", configs.getArchive(ConfigType.DBTABLE.getId()).getRevision());

			tables = new DBTableManager(store);
			tables.load();
			rows = new DBRowManager(store);
			rows.load();
			tableIndexes = new DBTableIndexManager(store);
			tableIndexes.load();
			loadVarbits(store, configs);
			loadEnums(store, configs);
			npcs = new NpcManager(store);
			npcs.load();
			items = new ItemManager(store);
			items.load();

			root.add("about", about(cacheInfo));
			root.add("slayerAreas", slayerAreas());
			root.add("konarTaskAreas", masterTaskAreas("KONAR_"));
			root.add("masters", masters());
			root.add("masterNpcs", masterNpcs());
			root.add("masterQuests", masterQuests());
			root.add("shopBuyTab", shopBuyTab());
			root.add("unlockBitCheck", unlockBitCheck());
			root.add("varbits", varbitSection());
			final JsonObject tablesOut = new JsonObject();
			for (String table : TABLES)
			{
				tablesOut.add(table, dumpTable(tableId(table)));
			}
			root.add("tables", tablesOut);
			root.add("warnings", strings(warnings));
			if (fixtureOut != null)
			{
				write(fixtureOut, fixture(cacheInfo));
			}
		}
		finally
		{
			deleteRecursively(copy.toFile());
		}

		final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();
		write(out, gson.toJson(root) + "\n");
		for (String warning : warnings)
		{
			System.out.println("WARNING: " + warning);
		}
	}

	/**
	 * Where the cache came from, without an absolute path or a user name, since the outputs are published: the
	 * cache's path under the home folder, or only its folder name when it lies elsewhere.
	 */
	static String sourceLabel(File cacheDir)
	{
		final Path home = new File(System.getProperty("user.home")).toPath().toAbsolutePath().normalize();
		final Path dir = cacheDir.toPath().toAbsolutePath().normalize();
		if (dir.startsWith(home)) return "~/" + home.relativize(dir).toString().replace('\\', '/');
		return dir.getFileName() == null ? "cache" : dir.getFileName().toString();
	}

	private static void write(File file, String text) throws IOException
	{
		final File parent = file.getAbsoluteFile().getParentFile();
		if (parent != null && !parent.isDirectory() && !parent.mkdirs())
		{
			throw new IOException("Cannot create " + parent);
		}
		try (Writer w = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8))
		{
			w.write(text);
		}
		System.out.println("Wrote " + file.getAbsolutePath());
	}

	// ---- test fixture for the plugin ----

	/** Columns kept in the plugin's test fixture: what SlayerGameData and its tests read. */
	private static final String[][] FIXTURE_COLUMNS = {
		{"SlayerUnlock", "COL_BIT", "COL_COST", "COL_NAME", "COL_REFUNDABLE", "COL_LIST_POSITION", "COL_RELATED_TASK"},
		{"SlayerTask", "COL_ID", "COL_MIN_COMLEVEL", "COL_MIN_STAT_REQUIREMENT_ALL", "COL_MIN_STAT_REQUIREMENT_ANY",
			"COL_NAME_UPPERCASE", "COL_EXTENSION_MIN_MAX", "COL_EXTENSION_ADDITIVE", "COL_UNLOCK_WEIGHTING", "COL_BLOCK_UNLOCK"},
		{"SlayerMasterTask", "COL_MASTER_ID", "COL_TASK", "COL_WEIGHT", "COL_MIN_AMOUNT", "COL_MAX_AMOUNT", "COL_TASK_UNLOCK"},
		// A boss task's SLAYER_TARGET_BOSSID leads through this table to its SlayerTask row
		{"SlayerTaskSublist", "COL_SUBTABLE_ID", "COL_TASK_SUBTABLE_ID", "COL_TASK"},
	};

	/**
	 * Raw rows in the shape the client holds them: per column, the flat value array of every tuple
	 * entry, and only the values a row stores itself (a reader falls back to the column default).
	 * One row per line so a game update shows up as a readable diff.
	 */
	private String fixture(JsonObject cacheInfo)
	{
		final Gson gson = new GsonBuilder().disableHtmlEscaping().serializeNulls().create();
		final StringBuilder sb = new StringBuilder("{\n");
		sb.append("\"about\": ").append(gson.toJson("Trimmed raw copy of the Slayer DB tables and slayer varbits for"
			+ " the plugin tests. SlayerTaskSublist is the boss list a boss task's SLAYER_TARGET_BOSSID is looked up in."
			+ " Values are flat tuple arrays per column, as the client stores them. masterNpcs are the"
			+ " multi-NPCs that turn into a slayer master, with the raw transform list (the last entry is the default),"
			+ " masterNames the NPCs among them that give tasks, and quests the end states of the quests that move masters."
			+ " enums are the reward shop's Buy tab: 840 slot to item, 841 item to amount, 842 item to cost in points."
			+ " Made by tools/cache-dump with --fixture. Do not edit by hand.")).append(",\n");
		sb.append("\"cache\": ").append(gson.toJson(cacheInfo)).append(",\n");
		sb.append("\"tables\": [\n");
		for (int t = 0; t < FIXTURE_COLUMNS.length; t++)
		{
			final String tableName = FIXTURE_COLUMNS[t][0];
			final int table = tableId(tableName);
			final ScriptVarType[][] types = tables.get(table).getTypes();
			final Object[] defaults = tables.get(table).getDefaultColumnValues();
			final List<Integer> keep = new ArrayList<>();
			final JsonArray columns = new JsonArray();
			for (int i = 1; i < FIXTURE_COLUMNS[t].length; i++)
			{
				final int c = columnId(table, FIXTURE_COLUMNS[t][i]);
				keep.add(c);
				final JsonObject col = new JsonObject();
				col.addProperty("id", c);
				col.addProperty("name", FIXTURE_COLUMNS[t][i]);
				col.add("types", typeNames(types[c]));
				final Object[] d = defaults != null && c < defaults.length ? (Object[]) defaults[c] : null;
				col.add("default", d == null ? JsonNull.INSTANCE : rawJson(d));
				columns.add(col);
			}
			sb.append("{\"id\": ").append(table).append(", \"name\": ").append(gson.toJson(tableName))
				.append(", \"columns\": ").append(gson.toJson(columns)).append(", \"rows\": [\n");
			final List<Integer> ids = tableRows(table);
			for (int r = 0; r < ids.size(); r++)
			{
				final int id = ids.get(r);
				final Object[][] values = rows.get(id).getColumnValues();
				final JsonObject row = new JsonObject();
				row.addProperty("row", id);
				row.addProperty("name", rowNames.get(id));
				final JsonObject v = new JsonObject();
				for (int c : keep)
				{
					if (values != null && c < values.length && values[c] != null) v.add(Integer.toString(c), rawJson(values[c]));
				}
				row.add("values", v);
				sb.append(gson.toJson(row)).append(r + 1 < ids.size() ? ",\n" : "\n");
			}
			sb.append("]}").append(t + 1 < FIXTURE_COLUMNS.length ? ",\n" : "\n");
		}
		sb.append("],\n\"varbits\": [\n");
		final List<VarbitDefinition> keepVarbits = new ArrayList<>();
		for (VarbitDefinition d : varbits.values())
		{
			final String name = varbitNames.get(d.getId());
			final boolean onUnlockVarp = d.getIndex() == UNLOCK_VARPS[0] || d.getIndex() == UNLOCK_VARPS[1]
				|| d.getIndex() == UNLOCK_VARPS[2];
			// The pool tests also read whether combat level checks are switched off
			if (onUnlockVarp || (name != null && (name.startsWith("SLAYER_") || name.equals("LUMBRIDGE_ELITE_REWARD")
				|| name.equals("COMBAT_LEVEL_REQS_DISABLED"))))
			{
				keepVarbits.add(d);
			}
		}
		for (int i = 0; i < keepVarbits.size(); i++)
		{
			sb.append(gson.toJson(varbitJson(keepVarbits.get(i)))).append(i + 1 < keepVarbits.size() ? ",\n" : "\n");
		}
		sb.append("],\n\"enums\": [\n");
		// The Buy tab's int enums, keys and values as client.getEnum returns them
		final int[] intEnums = {840, 841, 842};
		for (int i = 0; i < intEnums.length; i++)
		{
			final EnumDefinition e = enums.get(intEnums[i]);
			final JsonObject o = new JsonObject();
			o.addProperty("id", intEnums[i]);
			o.add("keys", intArray(e == null ? new int[0] : e.getKeys()));
			o.add("ints", intArray(e == null ? new int[0] : e.getIntVals()));
			sb.append(gson.toJson(o)).append(i + 1 < intEnums.length ? ",\n" : "\n");
		}
		sb.append("],\n");
		appendMasterFixture(sb, gson);
		sb.append("}\n");
		return sb.toString();
	}

	private static JsonArray intArray(int[] values)
	{
		final JsonArray out = new JsonArray();
		if (values != null)
		{
			for (int v : values) out.add(v);
		}
		return out;
	}

	private static JsonArray rawJson(Object[] values)
	{
		final JsonArray out = new JsonArray();
		for (Object o : values)
		{
			if (o == null) out.add(JsonNull.INSTANCE);
			else if (o instanceof String) out.add((String) o);
			else out.add(((Number) o).intValue());
		}
		return out;
	}

	// ---- gameval names, by reflection over the api jar ----

	private void loadGamevalNames() throws IllegalAccessException
	{
		for (Class<?> table : DBTableID.class.getDeclaredClasses())
		{
			final Integer id = intConstant(table, "ID");
			if (id == null) continue;
			tableNames.put(id, table.getSimpleName());
			final Map<Integer, String> cols = new TreeMap<>();
			for (Field f : table.getDeclaredFields())
			{
				if (isIntConstant(f) && f.getName().startsWith("COL_")) cols.put(f.getInt(null), f.getName());
			}
			columnNames.put(id, cols);
			for (Class<?> nested : table.getDeclaredClasses())
			{
				if (!"Row".equals(nested.getSimpleName())) continue;
				for (Field f : nested.getDeclaredFields())
				{
					if (isIntConstant(f)) rowNames.put(f.getInt(null), f.getName());
				}
			}
		}
		for (Field f : VarbitID.class.getDeclaredFields())
		{
			if (isIntConstant(f)) varbitNames.put(f.getInt(null), f.getName());
		}
		for (Field f : VarPlayerID.class.getDeclaredFields())
		{
			if (isIntConstant(f)) varpNames.put(f.getInt(null), f.getName());
		}
	}

	private static boolean isIntConstant(Field f)
	{
		final int m = f.getModifiers();
		return Modifier.isStatic(m) && Modifier.isFinal(m) && f.getType() == int.class;
	}

	private static Integer intConstant(Class<?> c, String name) throws IllegalAccessException
	{
		try
		{
			final Field f = c.getDeclaredField(name);
			return isIntConstant(f) ? f.getInt(null) : null;
		}
		catch (NoSuchFieldException e)
		{
			return null;
		}
	}

	private int tableId(String name)
	{
		for (Map.Entry<Integer, String> e : tableNames.entrySet())
		{
			if (e.getValue().equals(name)) return e.getKey();
		}
		throw new IllegalStateException("DBTableID has no table " + name);
	}

	private int columnId(int table, String name)
	{
		for (Map.Entry<Integer, String> e : columnNames.get(table).entrySet())
		{
			if (e.getValue().equals(name)) return e.getKey();
		}
		throw new IllegalStateException(tableNames.get(table) + " has no column " + name);
	}

	// ---- cache access ----

	private void loadVarbits(Store store, Index configs) throws IOException
	{
		final Archive archive = configs.getArchive(ConfigType.VARBIT.getId());
		final ArchiveFiles files = archive.getFiles(store.getStorage().loadArchive(archive));
		final VarbitLoader loader = new VarbitLoader();
		for (FSFile file : files.getFiles())
		{
			varbits.put(file.getFileId(), loader.load(file.getFileId(), file.getContents()));
		}
	}

	/** Only the shop's enums. The rest of the archive is not needed. */
	private void loadEnums(Store store, Index configs) throws IOException
	{
		final Archive archive = configs.getArchive(ConfigType.ENUM.getId());
		final ArchiveFiles files = archive.getFiles(store.getStorage().loadArchive(archive));
		final EnumLoader loader = new EnumLoader();
		final Set<Integer> wanted = new TreeSet<>();
		for (int id : SHOP_ENUMS) wanted.add(id);
		for (FSFile file : files.getFiles())
		{
			if (wanted.contains(file.getFileId())) enums.put(file.getFileId(), loader.load(file.getFileId(), file.getContents()));
		}
		for (int id : SHOP_ENUMS)
		{
			if (!enums.containsKey(id)) warnings.add("Shop enum " + id + " is not in the cache");
		}
	}

	/** The Buy tab: each slot's item with its name, cost in points, amount per purchase and description. */
	private JsonObject shopBuyTab()
	{
		final JsonObject out = new JsonObject();
		out.addProperty("note", "Client script 322 draws the Slayer Rewards Buy tab from these enums: 840 slot to item,"
			+ " 842 item to cost in points, 841 item to the amount one purchase gives (1 when absent), 843 item to description."
			+ " Script 322 also makes the rune pouch, looting bag and herb sack free while script 9449 returns 1.");
		final EnumDefinition slots = enums.get(840);
		final JsonArray list = new JsonArray();
		if (slots != null)
		{
			for (int i = 0; i < slots.getKeys().length; i++)
			{
				final int item = slots.getIntVals()[i];
				final JsonObject o = new JsonObject();
				o.addProperty("slot", slots.getKeys()[i]);
				o.addProperty("item", item);
				final ItemDefinition def = items.getItem(item);
				o.addProperty("name", def == null ? null : def.getName());
				o.addProperty("cost", enumInt(842, item));
				o.addProperty("amount", enumInt(841, item));
				o.addProperty("description", enumString(843, item));
				list.add(o);
			}
		}
		out.add("items", list);
		return out;
	}

	private Integer enumInt(int enumId, int key)
	{
		final EnumDefinition e = enums.get(enumId);
		if (e == null || e.getKeys() == null || e.getIntVals() == null) return null;
		for (int i = 0; i < e.getKeys().length; i++)
		{
			if (e.getKeys()[i] == key) return e.getIntVals()[i];
		}
		return null;
	}

	private String enumString(int enumId, int key)
	{
		final EnumDefinition e = enums.get(enumId);
		if (e == null || e.getKeys() == null || e.getStringVals() == null) return null;
		for (int i = 0; i < e.getKeys().length; i++)
		{
			if (e.getKeys()[i] == key) return e.getStringVals()[i];
		}
		return null;
	}

	/** Row ids of a table in the order the client's getDBTableRows returns them (the table's master index). */
	private List<Integer> tableRows(int table)
	{
		final List<Integer> fromIndex = new ArrayList<>();
		final DBTableIndex master = tableIndexes.getMaster(table);
		if (master != null && !master.getTupleIndexes().isEmpty())
		{
			final List<Integer> listed = master.getTupleIndexes().get(0).get(0);
			if (listed != null) fromIndex.addAll(listed);
		}
		final Set<Integer> fromRows = new TreeSet<>();
		for (DBRowDefinition row : rows.getRows())
		{
			if (row.getTableId() == table) fromRows.add(row.getId());
		}
		if (!new TreeSet<>(fromIndex).equals(fromRows))
		{
			warnings.add(tableNames.get(table) + ": master index lists " + fromIndex.size()
				+ " rows but " + fromRows.size() + " rows name this table");
		}
		return fromIndex.isEmpty() ? new ArrayList<>(fromRows) : fromIndex;
	}

	/** Mirrors client.getDBTableField: the row's values, else the table default, as a flat tuple array. */
	private Object[] rawColumn(int rowId, int column)
	{
		final DBRowDefinition row = rows.get(rowId);
		if (row == null) return null;
		final Object[][] values = row.getColumnValues();
		if (values != null && column < values.length && values[column] != null) return values[column];
		final DBTableDefinition table = tables.get(row.getTableId());
		final Object[] defaults = table == null ? null : table.getDefaultColumnValues();
		if (defaults != null && column < defaults.length && defaults[column] != null) return (Object[]) defaults[column];
		return null;
	}

	private boolean isDefaulted(int rowId, int column)
	{
		final Object[][] values = rows.get(rowId).getColumnValues();
		return values == null || column >= values.length || values[column] == null;
	}

	private ScriptVarType[] columnTypes(int table, int column)
	{
		final ScriptVarType[][] types = tables.get(table).getTypes();
		return column < types.length ? types[column] : null;
	}

	/** Every tuple's first element, like client.getDBTableField(row, column, 0). */
	private List<Object> firstElements(int rowId, int column)
	{
		final List<Object> out = new ArrayList<>();
		final Object[] raw = rawColumn(rowId, column);
		final ScriptVarType[] types = columnTypes(rows.get(rowId).getTableId(), column);
		if (raw == null || types == null || types.length == 0) return out;
		for (int i = 0; i < raw.length; i += types.length) out.add(raw[i]);
		return out;
	}

	private Integer firstInt(int rowId, int column)
	{
		final List<Object> values = firstElements(rowId, column);
		return values.isEmpty() || !(values.get(0) instanceof Integer) ? null : (Integer) values.get(0);
	}

	private String firstString(int rowId, int column)
	{
		final List<Object> values = firstElements(rowId, column);
		return values.isEmpty() || !(values.get(0) instanceof String) ? null : (String) values.get(0);
	}

	// ---- JSON building ----

	private JsonObject about(JsonObject cacheInfo)
	{
		final JsonObject about = new JsonObject();
		about.addProperty("generatedAt", Instant.now().toString());
		about.addProperty("generator", "tools/cache-dump SlayerCacheDump (net.runelite:cache, net.runelite:runelite-api)");
		about.addProperty("runeliteApiVersion", System.getProperty("runelite.version", "unknown"));
		about.add("cache", cacheInfo);
		about.add("layout", strings(Arrays.asList(
			"slayerAreas: every SlayerArea row with its in-game names.",
			"konarTaskAreas: Konar's pool rows with weight, amounts and allowed areas resolved to SlayerArea rows.",
			"masters: SlayerMasterTask rows grouped by COL_MASTER_ID, with the master name taken from the row names.",
			"masterNpcs: every NPC named like a slayer master, and every multi-NPC that turns into one, with the varbit"
				+ " that switches it and the NPC shown for each value.",
			"masterQuests: the quests that move a master or decide whether one gives tasks, with their end state.",
			"shopBuyTab: the reward shop's Buy tab from enums 840 to 843, each item with its cost in points.",
			"unlockBitCheck: each SlayerUnlock row's COL_BIT read both as a varbit id and as a bit index into the unlock varps.",
			"varbits: slayer varbits (id to base varp and bit range) and every varbit packed into the slayer varps.",
			"tables: full dump of each table. columns lists column ids, gameval names and types. Rows carry every column value.")));
		about.add("valueShape", strings(Arrays.asList(
			"A column value is a list with one entry per stored tuple. A list column has several entries.",
			"A column with one type holds plain entries. A column with several types holds an array per entry, in type order.",
			"client.getDBTableField(row, column, i) returns element i of every entry. So for a one-type column it returns the whole list,"
				+ " and i must be smaller than the number of types or the client throws IllegalArgumentException.",
			"A row without a value for a column reads the table default (listed under columns), else an empty array."
				+ " Such columns are listed under 'defaulted' on the row.",
			"DBROW entries are written as {row, table, name, label}. 'row' is the raw value, 'label' is the referenced row's display name.")));
		return about;
	}

	private JsonObject dumpTable(int table)
	{
		final JsonObject out = new JsonObject();
		final DBTableDefinition def = tables.get(table);
		out.addProperty("id", table);
		final JsonArray columns = new JsonArray();
		final ScriptVarType[][] types = def.getTypes();
		final Object[] defaults = def.getDefaultColumnValues();
		final Map<Integer, String> names = columnNames.getOrDefault(table, new HashMap<>());
		for (int c = 0; c < types.length; c++)
		{
			if (types[c] == null) continue;
			final JsonObject col = new JsonObject();
			col.addProperty("id", c);
			col.addProperty("name", names.get(c));
			col.add("types", typeNames(types[c]));
			final Object[] d = defaults != null && c < defaults.length ? (Object[]) defaults[c] : null;
			col.add("default", d == null ? JsonNull.INSTANCE : encodeColumn(types[c], d));
			columns.add(col);
		}
		for (Integer c : names.keySet())
		{
			if (c >= types.length || types[c] == null)
			{
				warnings.add(tableNames.get(table) + "." + names.get(c) + " is named in gameval but absent from the cache table");
			}
		}
		out.add("columns", columns);

		final List<Integer> ids = tableRows(table);
		out.addProperty("rowCount", ids.size());
		final JsonArray rowsOut = new JsonArray();
		for (int id : ids)
		{
			final JsonObject row = new JsonObject();
			row.addProperty("row", id);
			row.addProperty("name", rowNames.get(id));
			final JsonObject values = new JsonObject();
			final JsonArray defaulted = new JsonArray();
			for (int c = 0; c < types.length; c++)
			{
				if (types[c] == null) continue;
				final String key = names.getOrDefault(c, "col_" + c);
				final Object[] raw = rawColumn(id, c);
				values.add(key, raw == null ? new JsonArray() : encodeColumn(types[c], raw));
				if (isDefaulted(id, c)) defaulted.add(key);
			}
			row.add("values", values);
			row.add("defaulted", defaulted);
			final ScriptVarType[][] rowTypes = rows.get(id).getColumnTypes();
			for (int c = 0; rowTypes != null && c < rowTypes.length; c++)
			{
				if (rowTypes[c] != null && (c >= types.length || types[c] == null || !Arrays.equals(rowTypes[c], types[c])))
				{
					warnings.add("Row " + id + " column " + c + " types " + Arrays.toString(rowTypes[c]) + " differ from its table");
				}
			}
			rowsOut.add(row);
		}
		out.add("rows", rowsOut);
		return out;
	}

	private static JsonArray typeNames(ScriptVarType[] types)
	{
		final JsonArray out = new JsonArray();
		for (ScriptVarType t : types) out.add(t == null ? "UNKNOWN" : t.name());
		return out;
	}

	private JsonArray encodeColumn(ScriptVarType[] types, Object[] raw)
	{
		final JsonArray out = new JsonArray();
		for (int i = 0; i + types.length <= raw.length; i += types.length)
		{
			if (types.length == 1)
			{
				out.add(encodeValue(types[0], raw[i]));
				continue;
			}
			final JsonArray tuple = new JsonArray();
			for (int k = 0; k < types.length; k++) tuple.add(encodeValue(types[k], raw[i + k]));
			out.add(tuple);
		}
		return out;
	}

	private JsonElement encodeValue(ScriptVarType type, Object value)
	{
		if (value == null) return JsonNull.INSTANCE;
		if (value instanceof String) return new JsonPrimitive((String) value);
		final int v = ((Number) value).intValue();
		if (type == ScriptVarType.DBROW && v >= 0) return ref(v);
		return new JsonPrimitive(v);
	}

	private JsonObject ref(int rowId)
	{
		final JsonObject ref = new JsonObject();
		ref.addProperty("row", rowId);
		final DBRowDefinition row = rows.get(rowId);
		ref.addProperty("table", row == null ? null : tableNames.getOrDefault(row.getTableId(), "table " + row.getTableId()));
		ref.addProperty("name", rowNames.get(rowId));
		ref.addProperty("label", row == null ? null : label(rowId));
		return ref;
	}

	/** Display name of a row in one of the slayer tables. */
	private String label(int rowId)
	{
		final DBRowDefinition row = rows.get(rowId);
		if (row == null) return null;
		final String table = tableNames.get(row.getTableId());
		if (table == null) return null;
		switch (table)
		{
			case "SlayerTask":
				return firstString(rowId, DBTableID.SlayerTask.COL_NAME_UPPERCASE);
			case "SlayerArea":
				final String helper = firstString(rowId, DBTableID.SlayerArea.COL_AREA_NAME_IN_HELPER);
				return helper != null ? helper : firstString(rowId, DBTableID.SlayerArea.COL_AREA_TEXT);
			case "SlayerUnlock":
				return firstString(rowId, DBTableID.SlayerUnlock.COL_NAME);
			case "SlayerModifiers":
				return firstString(rowId, DBTableID.SlayerModifiers.COL_NAME);
			case "SlayerTaskSublist":
				final Integer task = firstInt(rowId, DBTableID.SlayerTaskSublist.COL_TASK);
				return task == null ? null : label(task);
			default:
				return null;
		}
	}

	private JsonArray slayerAreas()
	{
		final JsonArray out = new JsonArray();
		for (int id : tableRows(DBTableID.SlayerArea.ID))
		{
			final JsonObject area = new JsonObject();
			area.addProperty("row", id);
			area.addProperty("name", rowNames.get(id));
			area.addProperty("areaId", firstInt(id, DBTableID.SlayerArea.COL_AREA_ID));
			area.addProperty("helperName", firstString(id, DBTableID.SlayerArea.COL_AREA_NAME_IN_HELPER));
			area.addProperty("text", firstString(id, DBTableID.SlayerArea.COL_AREA_TEXT));
			area.addProperty("hint", firstString(id, DBTableID.SlayerArea.COL_AREA_HINT));
			area.add("restrictedContent", jsonList(firstElements(id, DBTableID.SlayerArea.COL_RESTRICTED_CONTENT)));
			out.add(area);
		}
		return out;
	}

	/** One master's pool rows (selected by gameval row-name prefix) with areas resolved. */
	private JsonObject masterTaskAreas(String prefix)
	{
		final JsonObject out = new JsonObject();
		final JsonArray tasks = new JsonArray();
		final Set<Integer> masterIds = new TreeSet<>();
		int totalWeight = 0;
		for (int id : tableRows(DBTableID.SlayerMasterTask.ID))
		{
			final String name = rowNames.get(id);
			if (name == null || !name.startsWith(prefix)) continue;
			final Integer master = firstInt(id, DBTableID.SlayerMasterTask.COL_MASTER_ID);
			if (master != null) masterIds.add(master);
			final Integer weight = firstInt(id, DBTableID.SlayerMasterTask.COL_WEIGHT);
			totalWeight += weight == null ? 0 : weight;
			tasks.add(poolEntry(id));
		}
		out.addProperty("rowNamePrefix", prefix);
		out.add("masterIds", jsonList(new ArrayList<>(masterIds)));
		out.addProperty("totalWeight", totalWeight);
		out.addProperty("note", "Rows are selected by gameval row name. A pool row added after this api version has no name and"
			+ " is missing here, but it is still listed under masters and tables.SlayerMasterTask.");
		out.add("tasks", tasks);
		return out;
	}

	private JsonObject poolEntry(int id)
	{
		final JsonObject e = new JsonObject();
		e.addProperty("poolRow", id);
		e.addProperty("poolRowName", rowNames.get(id));
		final Integer task = firstInt(id, DBTableID.SlayerMasterTask.COL_TASK);
		e.add("task", task == null ? JsonNull.INSTANCE : ref(task));
		e.addProperty("taskId", task == null ? null : firstInt(task, DBTableID.SlayerTask.COL_ID));
		e.addProperty("weight", firstInt(id, DBTableID.SlayerMasterTask.COL_WEIGHT));
		e.addProperty("minAmount", firstInt(id, DBTableID.SlayerMasterTask.COL_MIN_AMOUNT));
		e.addProperty("maxAmount", firstInt(id, DBTableID.SlayerMasterTask.COL_MAX_AMOUNT));
		final JsonArray areas = new JsonArray();
		for (Object area : firstElements(id, DBTableID.SlayerMasterTask.COL_AREAS))
		{
			if (!(area instanceof Integer)) continue;
			final JsonObject a = ref((Integer) area);
			a.addProperty("areaId", firstInt((Integer) area, DBTableID.SlayerArea.COL_AREA_ID));
			areas.add(a);
		}
		e.add("areas", areas);
		final JsonArray unlocks = new JsonArray();
		for (Object unlock : firstElements(id, DBTableID.SlayerMasterTask.COL_TASK_UNLOCK))
		{
			if (unlock instanceof Integer) unlocks.add(ref((Integer) unlock));
		}
		e.add("taskUnlock", unlocks);
		return e;
	}

	private JsonArray masters()
	{
		final Map<Integer, List<Integer>> byMaster = new TreeMap<>();
		for (int id : tableRows(DBTableID.SlayerMasterTask.ID))
		{
			final Integer master = firstInt(id, DBTableID.SlayerMasterTask.COL_MASTER_ID);
			byMaster.computeIfAbsent(master == null ? -1 : master, k -> new ArrayList<>()).add(id);
		}
		final JsonArray out = new JsonArray();
		for (Map.Entry<Integer, List<Integer>> e : byMaster.entrySet())
		{
			final Set<String> prefixes = new LinkedHashSet<>();
			int weight = 0;
			final JsonArray tasks = new JsonArray();
			for (int id : e.getValue())
			{
				final String name = rowNames.get(id);
				prefixes.add(name == null ? "(unnamed row)" : name.substring(0, name.indexOf('_') + 1));
				final Integer w = firstInt(id, DBTableID.SlayerMasterTask.COL_WEIGHT);
				weight += w == null ? 0 : w;
				final Integer task = firstInt(id, DBTableID.SlayerMasterTask.COL_TASK);
				final JsonObject t = new JsonObject();
				t.addProperty("poolRow", id);
				t.addProperty("poolRowName", name);
				t.addProperty("taskRow", task);
				t.addProperty("task", task == null ? null : label(task));
				t.addProperty("weight", w);
				tasks.add(t);
			}
			final JsonObject m = new JsonObject();
			m.addProperty("masterId", e.getKey());
			m.add("rowNamePrefixes", strings(new ArrayList<>(prefixes)));
			m.addProperty("rowCount", e.getValue().size());
			m.addProperty("totalWeight", weight);
			m.add("tasks", tasks);
			out.add(m);
		}
		return out;
	}

	private JsonArray unlockBitCheck()
	{
		final JsonArray out = new JsonArray();
		for (int id : tableRows(DBTableID.SlayerUnlock.ID))
		{
			final JsonObject u = new JsonObject();
			u.addProperty("row", id);
			u.addProperty("name", rowNames.get(id));
			u.addProperty("label", firstString(id, DBTableID.SlayerUnlock.COL_NAME));
			u.addProperty("cost", firstInt(id, DBTableID.SlayerUnlock.COL_COST));
			final Integer bit = firstInt(id, DBTableID.SlayerUnlock.COL_BIT);
			u.addProperty("bit", bit);
			if (bit == null)
			{
				out.add(u);
				continue;
			}
			final VarbitDefinition asId = varbits.get(bit);
			u.add("asVarbitId", asId == null ? JsonNull.INSTANCE : varbitJson(asId));
			final JsonObject asIndex = new JsonObject();
			final int word = bit / 32;
			if (bit >= 0 && word < UNLOCK_VARPS.length)
			{
				final int varp = UNLOCK_VARPS[word];
				final int bitInVarp = bit % 32;
				asIndex.addProperty("varp", varp);
				asIndex.addProperty("varpName", varpNames.get(varp));
				asIndex.addProperty("bitInVarp", bitInVarp);
				final JsonArray covering = new JsonArray();
				for (VarbitDefinition d : varbits.values())
				{
					if (d.getIndex() == varp && d.getLeastSignificantBit() <= bitInVarp && bitInVarp <= d.getMostSignificantBit())
					{
						covering.add(varbitJson(d));
					}
				}
				asIndex.add("varbits", covering);
			}
			u.add("asUnlockBitIndex", asIndex);
			out.add(u);
		}
		return out;
	}

	// ---- who stands at each master spot ----

	/** Every NPC named like a slayer master or stand-in, by id. */
	private Map<Integer, NpcDefinition> masterNpcDefs()
	{
		final Map<Integer, NpcDefinition> out = new TreeMap<>();
		for (NpcDefinition n : npcs.getNpcs())
		{
			if (n.getName() != null && MASTER_NAMES.contains(n.getName())) out.put(n.getId(), n);
		}
		return out;
	}

	/** Multi-NPCs that can turn into one of {@code masterIds}. */
	private List<NpcDefinition> masterSpots(Set<Integer> masterIds)
	{
		final List<NpcDefinition> out = new ArrayList<>();
		for (NpcDefinition n : npcs.getNpcs())
		{
			if (n.getConfigs() == null) continue;
			for (int c : n.getConfigs())
			{
				if (masterIds.contains(c))
				{
					out.add(n);
					break;
				}
			}
		}
		out.sort(Comparator.comparingInt(NpcDefinition::getId));
		return out;
	}

	private static List<String> ops(NpcDefinition n)
	{
		final List<String> out = new ArrayList<>();
		final EntityOpsDefinition ops = n.getOps();
		if (ops == null || ops.getOps() == null) return out;
		for (EntityOpsDefinition.Op op : ops.getOps())
		{
			out.add(op == null ? null : op.text);
		}
		return out;
	}

	/** Whether this NPC version gives tasks. Quest, cutscene and follower copies of a master do not. */
	private static boolean assigns(NpcDefinition n)
	{
		return ops(n).contains(ASSIGN_OP);
	}

	/** Ops that change with a varbit or varp, e.g. Mortimer's, which depend on MORTIMER_INTRODUCTION. */
	private JsonArray conditionalOps(NpcDefinition n)
	{
		final JsonArray out = new JsonArray();
		final EntityOpsDefinition ops = n.getOps();
		if (ops == null || ops.getConditionalOps() == null) return out;
		for (int i = 0; i < ops.getConditionalOps().size(); i++)
		{
			final List<EntityOpsDefinition.ConditionalOp> list = ops.getConditionalOps().get(i);
			if (list == null) continue;
			for (EntityOpsDefinition.ConditionalOp c : list)
			{
				final JsonObject o = new JsonObject();
				o.addProperty("op", i);
				o.addProperty("text", c.text);
				o.addProperty("varbit", c.getVarbitID() == 65535 ? -1 : c.getVarbitID());
				o.addProperty("varbitName", varbitNames.get(c.getVarbitID()));
				o.addProperty("varp", c.getVarpID() == 65535 ? -1 : c.getVarpID());
				o.addProperty("varpName", varpNames.get(c.getVarpID()));
				o.addProperty("min", c.getMinValue());
				o.addProperty("max", c.getMaxValue());
				out.add(o);
			}
		}
		return out;
	}

	/**
	 * Which NPC stands at each master spot for each value of the varbit or varp that switches it, as the
	 * client's multi-NPC transform picks it.
	 */
	private JsonObject masterNpcs()
	{
		final JsonObject out = new JsonObject();
		out.addProperty("note", "masters lists every NPC named like a slayer master. 'assigns' marks the versions with the "
			+ ASSIGN_OP + " op, the ones that give tasks. spots lists every multi-NPC that can turn into one of them."
			+ " For a value v of its varbit (or varp, when varbit is -1) the client shows transforms[v], and 'otherwise'"
			+ " for any value past the list. npc -1 shows nothing.");
		final Map<Integer, NpcDefinition> masters = masterNpcDefs();
		final JsonArray list = new JsonArray();
		for (NpcDefinition n : masters.values())
		{
			final JsonObject o = new JsonObject();
			o.addProperty("id", n.getId());
			o.addProperty("name", n.getName());
			o.add("ops", jsonList(ops(n)));
			o.addProperty("assigns", assigns(n));
			o.add("conditionalOps", conditionalOps(n));
			list.add(o);
		}
		out.add("masters", list);
		final JsonArray spots = new JsonArray();
		for (NpcDefinition n : masterSpots(masters.keySet()))
		{
			final JsonObject o = new JsonObject();
			o.addProperty("npc", n.getId());
			o.addProperty("varbit", n.getVarbitId());
			o.addProperty("varbitName", varbitNames.get(n.getVarbitId()));
			o.addProperty("varp", n.getVarpIndex());
			o.addProperty("varpName", varpNames.get(n.getVarpIndex()));
			final int[] configs = n.getConfigs();
			final JsonArray transforms = new JsonArray();
			for (int v = 0; v < configs.length - 1; v++)
			{
				final JsonObject t = transformJson(configs[v]);
				t.addProperty("value", v);
				transforms.add(t);
			}
			o.add("transforms", transforms);
			o.add("otherwise", transformJson(configs[configs.length - 1]));
			spots.add(o);
		}
		out.add("spots", spots);
		return out;
	}

	private JsonObject transformJson(int npc)
	{
		final JsonObject t = new JsonObject();
		t.addProperty("npc", npc);
		final NpcDefinition def = npc < 0 ? null : npcs.get(npc);
		t.addProperty("name", def == null ? null : def.getName());
		t.addProperty("assigns", def != null && assigns(def));
		return t;
	}

	/** Each quest in MASTER_QUESTS with its progress value once complete (Quest.COL_ENDSTATE). */
	private JsonArray masterQuests()
	{
		final JsonArray out = new JsonArray();
		for (int row : MASTER_QUESTS)
		{
			final JsonObject q = new JsonObject();
			q.addProperty("row", row);
			q.addProperty("name", rowNames.get(row));
			q.addProperty("id", firstInt(row, DBTableID.Quest.COL_ID));
			q.addProperty("displayName", firstString(row, DBTableID.Quest.COL_DISPLAYNAME));
			q.addProperty("endState", firstInt(row, DBTableID.Quest.COL_ENDSTATE));
			out.add(q);
		}
		return out;
	}

	/** The fixture's copy: spots with their raw transform lists, the names of the NPCs that give tasks, and quest end states. */
	private void appendMasterFixture(StringBuilder sb, Gson gson)
	{
		final Map<Integer, NpcDefinition> masters = masterNpcDefs();
		final List<NpcDefinition> spots = masterSpots(masters.keySet());
		sb.append("\"masterNpcs\": [\n");
		for (int i = 0; i < spots.size(); i++)
		{
			final NpcDefinition n = spots.get(i);
			final JsonObject o = new JsonObject();
			o.addProperty("npc", n.getId());
			o.addProperty("varbit", n.getVarbitId());
			o.addProperty("varp", n.getVarpIndex());
			final JsonArray transforms = new JsonArray();
			for (int c : n.getConfigs()) transforms.add(c);
			o.add("transforms", transforms);
			sb.append(gson.toJson(o)).append(i + 1 < spots.size() ? ",\n" : "\n");
		}
		final JsonObject names = new JsonObject();
		for (NpcDefinition n : masters.values())
		{
			if (assigns(n)) names.addProperty(Integer.toString(n.getId()), n.getName());
		}
		sb.append("],\n\"masterNames\": ").append(gson.toJson(names)).append(",\n\"quests\": [\n");
		for (int i = 0; i < MASTER_QUESTS.length; i++)
		{
			final int row = MASTER_QUESTS[i];
			final JsonObject q = new JsonObject();
			q.addProperty("row", row);
			q.addProperty("name", rowNames.get(row));
			q.addProperty("endState", firstInt(row, DBTableID.Quest.COL_ENDSTATE));
			sb.append(gson.toJson(q)).append(i + 1 < MASTER_QUESTS.length ? ",\n" : "\n");
		}
		sb.append("]\n");
	}

	private JsonObject varbitSection()
	{
		final JsonObject out = new JsonObject();
		out.addProperty("note", "varp is the base VarPlayer. lsb and msb are inclusive bit positions."
			+ " The value is (varp >>> lsb) & ((1 << (msb - lsb + 1)) - 1).");
		final JsonArray named = new JsonArray();
		final Set<Integer> varps = new TreeSet<>();
		for (int v : UNLOCK_VARPS) varps.add(v);
		for (Map.Entry<Integer, String> e : new TreeMap<>(varbitNames).entrySet())
		{
			if (!e.getValue().contains("SLAYER") || e.getValue().contains("DRAGONSLAYER")
				|| e.getValue().contains("DEMONSLAYER") || e.getValue().contains("VAMPYRESLAYER")) continue;
			final VarbitDefinition d = varbits.get(e.getKey());
			if (d == null)
			{
				warnings.add("Varbit " + e.getValue() + " (" + e.getKey() + ") is not in the cache");
				continue;
			}
			named.add(varbitJson(d));
			varps.add(d.getIndex());
		}
		for (Map.Entry<Integer, String> e : varpNames.entrySet())
		{
			if (e.getValue().startsWith("SLAYER_")) varps.add(e.getKey());
		}
		out.add("slayerVarbits", named);

		final JsonArray byVarp = new JsonArray();
		for (int varp : varps)
		{
			final List<VarbitDefinition> packed = new ArrayList<>();
			for (VarbitDefinition d : varbits.values())
			{
				if (d.getIndex() == varp) packed.add(d);
			}
			packed.sort(Comparator.comparingInt(VarbitDefinition::getLeastSignificantBit).thenComparingInt(VarbitDefinition::getId));
			final JsonObject v = new JsonObject();
			v.addProperty("varp", varp);
			v.addProperty("name", varpNames.get(varp));
			final JsonArray list = new JsonArray();
			for (VarbitDefinition d : packed) list.add(varbitJson(d));
			v.add("varbits", list);
			byVarp.add(v);
		}
		out.add("byVarp", byVarp);
		return out;
	}

	private JsonObject varbitJson(VarbitDefinition d)
	{
		final JsonObject v = new JsonObject();
		v.addProperty("id", d.getId());
		v.addProperty("name", varbitNames.get(d.getId()));
		v.addProperty("varp", d.getIndex());
		v.addProperty("varpName", varpNames.get(d.getIndex()));
		v.addProperty("lsb", d.getLeastSignificantBit());
		v.addProperty("msb", d.getMostSignificantBit());
		v.addProperty("bits", d.getMostSignificantBit() - d.getLeastSignificantBit() + 1);
		return v;
	}

	private static JsonArray strings(Collection<String> values)
	{
		final JsonArray out = new JsonArray();
		for (String s : values) out.add(s);
		return out;
	}

	private static JsonArray jsonList(List<?> values)
	{
		final JsonArray out = new JsonArray();
		for (Object o : values)
		{
			if (o == null) out.add(JsonNull.INSTANCE);
			else if (o instanceof Number) out.add((Number) o);
			else out.add(o.toString());
		}
		return out;
	}

	private static void deleteRecursively(File f)
	{
		final File[] children = f.listFiles();
		if (children != null)
		{
			for (File c : children) deleteRecursively(c);
		}
		if (!f.delete())
		{
			f.deleteOnExit();
		}
	}
}
