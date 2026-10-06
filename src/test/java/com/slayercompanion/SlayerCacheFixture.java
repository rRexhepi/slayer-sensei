package com.slayercompanion;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * The trimmed dump of the live cache in src/test/resources, read back with the client's
 * getDBTableField rules so SlayerGameData parses it exactly as it parses the game. It also holds the
 * multi-NPCs that put a slayer master or a stand-in at each spot, the quest end states that go with them,
 * and the enums of the reward shop's Buy tab.
 * Regenerate it with tools/cache-dump ({@code -Pfixture}). Columns left out of the fixture throw.
 */
final class SlayerCacheFixture implements SlayerGameData.Db
{
	static final class Table
	{
		final int id;
		final String name;
		final Map<String, Integer> columnIds = new HashMap<>();
		final Map<Integer, Integer> typeCounts = new HashMap<>();
		final Map<Integer, Object[]> defaults = new HashMap<>();
		final List<Integer> rows = new ArrayList<>();

		Table(int id, String name)
		{
			this.id = id;
			this.name = name;
		}
	}

	static final class Varbit
	{
		final int id;
		final String name;
		final int varp;
		final int lsb;
		final int msb;

		Varbit(int id, String name, int varp, int lsb, int msb)
		{
			this.id = id;
			this.name = name;
			this.varp = varp;
			this.lsb = lsb;
			this.msb = msb;
		}
	}

	/** A multi-NPC that turns into a slayer master. The last transform is the default for values past the list. */
	static final class Spot
	{
		final int npc;
		final int varbit;
		final int varp;
		final int[] transforms;

		Spot(int npc, int varbit, int varp, int[] transforms)
		{
			this.npc = npc;
			this.varbit = varbit;
			this.varp = varp;
			this.transforms = transforms;
		}

		/** The NPC the client shows for a varbit value, -1 for none, as the client's transform picks it. */
		int shown(int value)
		{
			return value >= 0 && value < transforms.length - 1 ? transforms[value] : transforms[transforms.length - 1];
		}
	}

	private static SlayerCacheFixture instance;

	private final Map<Integer, Table> tables = new HashMap<>();
	private final Map<Integer, Table> tableOfRow = new HashMap<>();
	private final Map<Integer, String> rowNames = new HashMap<>();
	private final Map<Integer, Map<Integer, Object[]>> values = new HashMap<>();
	private final Map<Integer, Varbit> varbits = new HashMap<>();
	private final List<Spot> masterSpots = new ArrayList<>();
	/** NPC ids of the master versions that give tasks, to their names. */
	private final Map<Integer, String> masterNames = new HashMap<>();
	/** Quest end states by the Quest table's gameval row name. */
	private final Map<String, Integer> questEnds = new HashMap<>();
	/** The reward shop's Buy tab enums, {keys, int values}. */
	private final Map<Integer, int[][]> enums = new HashMap<>();

	private SlayerCacheFixture()
	{
	}

	static synchronized SlayerCacheFixture get()
	{
		if (instance == null)
		{
			instance = load("/slayer_cache_fixture.json");
		}
		return instance;
	}

	private static SlayerCacheFixture load(String resource)
	{
		try (InputStream in = SlayerCacheFixture.class.getResourceAsStream(resource))
		{
			if (in == null) throw new IllegalStateException(resource + " is missing. Run tools/cache-dump with -Pfixture.");
			final JsonObject root = new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			final SlayerCacheFixture fixture = new SlayerCacheFixture();
			for (JsonElement t : root.getAsJsonArray("tables"))
			{
				fixture.addTable(t.getAsJsonObject());
			}
			for (JsonElement v : root.getAsJsonArray("varbits"))
			{
				final JsonObject o = v.getAsJsonObject();
				final Varbit varbit = new Varbit(o.get("id").getAsInt(),
						o.get("name").isJsonNull() ? null : o.get("name").getAsString(),
						o.get("varp").getAsInt(), o.get("lsb").getAsInt(), o.get("msb").getAsInt());
				fixture.varbits.put(varbit.id, varbit);
			}
			for (JsonElement s : root.getAsJsonArray("masterNpcs"))
			{
				final JsonObject o = s.getAsJsonObject();
				final JsonArray t = o.getAsJsonArray("transforms");
				final int[] transforms = new int[t.size()];
				for (int i = 0; i < transforms.length; i++) transforms[i] = t.get(i).getAsInt();
				fixture.masterSpots.add(new Spot(o.get("npc").getAsInt(), o.get("varbit").getAsInt(), o.get("varp").getAsInt(), transforms));
			}
			for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("masterNames").entrySet())
			{
				fixture.masterNames.put(Integer.parseInt(e.getKey()), e.getValue().getAsString());
			}
			for (JsonElement q : root.getAsJsonArray("quests"))
			{
				final JsonObject o = q.getAsJsonObject();
				fixture.questEnds.put(o.get("name").getAsString(), o.get("endState").getAsInt());
			}
			for (JsonElement e : root.getAsJsonArray("enums"))
			{
				final JsonObject o = e.getAsJsonObject();
				fixture.enums.put(o.get("id").getAsInt(), new int[][]{ints(o.getAsJsonArray("keys")), ints(o.getAsJsonArray("ints"))});
			}
			return fixture;
		}
		catch (IOException e)
		{
			throw new UncheckedIOException(e);
		}
	}

	private void addTable(JsonObject t)
	{
		final Table table = new Table(t.get("id").getAsInt(), t.get("name").getAsString());
		for (JsonElement c : t.getAsJsonArray("columns"))
		{
			final JsonObject col = c.getAsJsonObject();
			final int id = col.get("id").getAsInt();
			table.columnIds.put(col.get("name").getAsString(), id);
			table.typeCounts.put(id, col.getAsJsonArray("types").size());
			if (!col.get("default").isJsonNull()) table.defaults.put(id, raw(col.getAsJsonArray("default")));
		}
		for (JsonElement r : t.getAsJsonArray("rows"))
		{
			final JsonObject row = r.getAsJsonObject();
			final int id = row.get("row").getAsInt();
			table.rows.add(id);
			tableOfRow.put(id, table);
			if (!row.get("name").isJsonNull()) rowNames.put(id, row.get("name").getAsString());
			final Map<Integer, Object[]> rowValues = new HashMap<>();
			for (Map.Entry<String, JsonElement> e : row.getAsJsonObject("values").entrySet())
			{
				rowValues.put(Integer.parseInt(e.getKey()), raw(e.getValue().getAsJsonArray()));
			}
			values.put(id, rowValues);
		}
		tables.put(table.id, table);
	}

	private static int[] ints(JsonArray array)
	{
		final int[] out = new int[array.size()];
		for (int i = 0; i < out.length; i++) out[i] = array.get(i).getAsInt();
		return out;
	}

	private static Object[] raw(JsonArray array)
	{
		final Object[] out = new Object[array.size()];
		for (int i = 0; i < out.length; i++)
		{
			final JsonElement e = array.get(i);
			if (e.isJsonNull()) out[i] = null;
			else if (e.getAsJsonPrimitive().isString()) out[i] = e.getAsString();
			else out[i] = e.getAsInt();
		}
		return out;
	}

	@Override
	public List<Integer> rows(int table)
	{
		final Table t = tables.get(table);
		if (t == null) throw new IllegalArgumentException("Table " + table + " is not in the fixture");
		return Collections.unmodifiableList(t.rows);
	}

	/** The client's rules: element {@code tupleIndex} of every tuple, the column default for a missing value. */
	@Override
	public Object[] field(int row, int column, int tupleIndex)
	{
		final Table table = tableOfRow.get(row);
		if (table == null) throw new IllegalArgumentException("Row " + row + " is not in the fixture");
		final Integer types = table.typeCounts.get(column);
		if (types == null) throw new IllegalArgumentException(table.name + " column " + column + " is not in the fixture");
		if (tupleIndex >= types) throw new IllegalArgumentException("tuple index too large");
		Object[] flat = values.get(row).get(column);
		if (flat == null) flat = table.defaults.get(column);
		if (flat == null) return new Object[0];
		final Object[] out = new Object[flat.length / types];
		for (int i = 0; i < out.length; i++)
		{
			out[i] = flat[i * types + tupleIndex];
		}
		return out;
	}

	/** Like client.getEnum: the keys and int values, or empty arrays for an enum the fixture does not hold. */
	@Override
	public int[][] enumInts(int enumId)
	{
		final int[][] e = enums.get(enumId);
		return e == null ? new int[][]{new int[0], new int[0]} : new int[][]{e[0].clone(), e[1].clone()};
	}

	Table table(int id)
	{
		return tables.get(id);
	}

	String rowName(int row)
	{
		return rowNames.get(row);
	}

	/** First element of every tuple in a column, by gameval column name. */
	Object[] column(int row, String columnName)
	{
		return field(row, tableOfRow.get(row).columnIds.get(columnName), 0);
	}

	int intValue(int row, String columnName)
	{
		final Object[] v = column(row, columnName);
		return v.length == 0 ? -1 : (Integer) v[0];
	}

	Varbit varbit(int id)
	{
		return varbits.get(id);
	}

	Collection<Varbit> varbits()
	{
		return Collections.unmodifiableCollection(varbits.values());
	}

	List<Spot> masterSpots()
	{
		return Collections.unmodifiableList(masterSpots);
	}

	/** The name of a master version that gives tasks, or null for any other NPC. */
	String masterName(int npc)
	{
		return masterNames.get(npc);
	}

	/** Names of the task-giving masters every spot switched by {@code varbit} shows at {@code value}. */
	Set<String> mastersShown(int varbit, int value)
	{
		final Set<String> out = new TreeSet<>();
		for (Spot spot : masterSpots)
		{
			final String name = spot.varbit == varbit ? masterName(spot.shown(value)) : null;
			if (name != null) out.add(name);
		}
		return out;
	}

	/** The varbit values at which a spot switched by {@code varbit} shows the task-giving {@code master}. */
	SortedSet<Integer> valuesShowing(int varbit, String master)
	{
		final SortedSet<Integer> out = new TreeSet<>();
		for (Spot spot : masterSpots)
		{
			if (spot.varbit != varbit) continue;
			for (int v = 0; v < spot.transforms.length - 1; v++)
			{
				if (master.equals(masterName(spot.shown(v)))) out.add(v);
			}
		}
		return out;
	}

	/** A quest's progress value once complete, by its Quest table row name, e.g. QUEST_PORCINEOFINTEREST. */
	int questEnd(String row)
	{
		final Integer end = questEnds.get(row);
		if (end == null) throw new IllegalArgumentException(row + " is not in the fixture");
		return end;
	}
}
