package com.slayercompanion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarPlayerID;

/**
 * Reward shop and task-pool data read from the game cache's database tables, so costs, names and
 * master weights track game updates without a plugin release. Must be loaded on the client thread.
 * Every column read here was checked against a dump of the live cache and the game's own client
 * scripts (tools/cache-dump). That includes which reward lengthens which task and to what amount, and
 * the items and costs of the shop's Buy tab, which come from two enums rather than a table.
 */
@Slf4j
final class SlayerGameData
{
	/**
	 * SlayerUnlock.COL_BIT is a bit index across these three varps, 32 bits each, in this order. It is
	 * not a varbit id. The game's own ownership check (client script 8048) reads it the same way.
	 */
	static final int[] UNLOCK_VARPS = {
		VarPlayerID.SLAYER_REWARDS_UNLOCKS, VarPlayerID.SLAYER_REWARDS_UNLOCKS1, VarPlayerID.SLAYER_REWARDS_UNLOCKS2};

	/** Stat ids in the requirement columns follow {@link net.runelite.api.Skill} order. */
	private static final int STAT_SLAYER = 18;

	/** Shop tabs, the first value of SlayerUnlock.COL_LIST_POSITION. */
	static final int TAB_UNLOCK = 0;
	static final int TAB_EXTEND = 1;
	static final int TAB_COSMETIC = 2;

	/**
	 * The reward shop's Buy tab, as client script 322 draws it: enum 840 holds the item in each slot and enum 842
	 * each item's cost in points. RuneLite has no gameval names for these enums. Checked against the cache dump
	 * (tools/cache-dump, section shopBuyTab).
	 */
	static final int SHOP_ITEMS_ENUM = 840;
	static final int SHOP_COSTS_ENUM = 842;

	/** The client's DB table reads, behind an interface so parsing can be tested against a cache dump. */
	interface Db
	{
		List<Integer> rows(int table);

		/**
		 * Same contract as {@link Client#getDBTableField}: element {@code tupleIndex} of every tuple stored
		 * in the column, so a list column returns one value per entry. A row without a value returns the
		 * column default, else an empty array. A tuple index past the column's types throws.
		 */
		Object[] field(int row, int column, int tupleIndex);

		/** An enum's keys and int values as {@link Client#getEnum} holds them, {keys, values}. Empty when unknown. */
		default int[][] enumInts(int enumId)
		{
			return new int[][]{new int[0], new int[0]};
		}

		/**
		 * Same contract as {@link Client#getDBRowsByValue}: the rows of {@code table} whose column holds {@code value} at
		 * {@code tupleIndex}, in table order. The client looks it up in its index. This default scans the rows.
		 */
		default List<Integer> rowsByValue(int table, int column, int tupleIndex, Object value)
		{
			final List<Integer> found = new ArrayList<>();
			for (int row : rows(table))
			{
				if (Arrays.asList(field(row, column, tupleIndex)).contains(value)) found.add(row);
			}
			return found;
		}
	}

	@Value
	static class Unlock
	{
		int row;
		String name;
		int cost;
		/** Bit index into {@link #UNLOCK_VARPS}, or -1. */
		int bit;
		/** Every task the reward names. Pedal to the Metals lists all metal dragons, I Wildy More Slayer four tasks. */
		List<Integer> relatedTaskRows;
		/** False for rewards taken out of the shop. Their rows stay in the cache with list position (-1, -1). */
		boolean listed;
		/** The shop tab: {@link #TAB_UNLOCK}, {@link #TAB_EXTEND} or {@link #TAB_COSMETIC}. -1 when unlisted. */
		int tab;
	}

	/**
	 * How a reward changes the amount of one task (SlayerTask COL_EXTENSION_MIN_MAX and COL_EXTENSION_ADDITIVE).
	 * The wiki: extending "will increase the amount assigned across all slayer masters that can assign it
	 * regardless of the quantity they can originally assign" (Slayer Rewards).
	 */
	@Value
	static class Extension
	{
		int unlockRow;
		int taskRow;
		/** The new fewest and most kills, or the kills added to the master's own range when {@link #additive}. */
		int min;
		int max;
		boolean additive;

		int extendedMin(int masterMin)
		{
			return additive ? masterMin + min : min;
		}

		int extendedMax(int masterMax)
		{
			return additive ? masterMax + max : max;
		}
	}

	/** A reward that changes a task's weight, e.g. Chance of Heavy Frost (SlayerTask COL_UNLOCK_WEIGHTING). */
	@Value
	static class Weighting
	{
		int unlockRow;
		int taskRow;
		int weight;
	}

	@Value
	static class PoolEntry
	{
		int taskRow;
		int weight;
		/** Unlock the player must own before this master assigns the task, or -1. */
		int unlockRow;
		/** Fewest and most kills the master assigns. -1 for boss tasks, where the player picks the amount. */
		int minAmount;
		int maxAmount;
	}

	/** A stat level a master checks before assigning a task. Stat ids follow {@link net.runelite.api.Skill} order. */
	@Value
	static class StatLevel
	{
		int stat;
		int level;
	}

	private final Map<Integer, Unlock> unlocks = new HashMap<>();
	private final Map<Integer, String> taskNames = new HashMap<>();
	private final Map<String, List<Integer>> taskRowsByName = new HashMap<>();
	private final Map<Integer, Integer> taskRowById = new HashMap<>();
	private final Map<Integer, Integer> slayerLevels = new HashMap<>();
	private final Map<Integer, Integer> combatLevels = new HashMap<>();
	private final Map<Integer, List<StatLevel>> statsAll = new HashMap<>();
	private final Map<Integer, List<StatLevel>> statsAny = new HashMap<>();
	private final Map<Integer, List<PoolEntry>> pools = new HashMap<>();
	private final Map<Integer, Double> typicalAmounts = new HashMap<>();
	private final Set<Integer> poolUnlocks = new HashSet<>();
	private final Map<Integer, List<Extension>> extensions = new HashMap<>();
	private final Map<Integer, Extension> extensionOfTask = new HashMap<>();
	private final Map<Integer, List<Weighting>> weightings = new HashMap<>();
	private final Map<Integer, List<Integer>> blockedBy = new HashMap<>();
	private final Map<Integer, Integer> shopCosts = new LinkedHashMap<>();

	private SlayerGameData()
	{
	}

	static SlayerGameData load(Client client)
	{
		return load(db(client));
	}

	/** The client's own table reads. Client thread. */
	static Db db(Client client)
	{
		return new Db()
		{
			@Override
			public List<Integer> rows(int table)
			{
				return client.getDBTableRows(table);
			}

			@Override
			public Object[] field(int row, int column, int tupleIndex)
			{
				return client.getDBTableField(row, column, tupleIndex);
			}

			@Override
			public int[][] enumInts(int enumId)
			{
				final EnumComposition e = client.getEnum(enumId);
				if (e == null || e.getKeys() == null || e.getIntVals() == null) return new int[][]{new int[0], new int[0]};
				return new int[][]{e.getKeys(), e.getIntVals()};
			}

			@Override
			public List<Integer> rowsByValue(int table, int column, int tupleIndex, Object value)
			{
				return client.getDBRowsByValue(table, column, tupleIndex, value);
			}
		};
	}

	static SlayerGameData load(Db db)
	{
		final SlayerGameData data = new SlayerGameData();
		try
		{
			data.loadTasks(db);
			data.loadUnlocks(db);
			data.loadPools(db);
		}
		catch (RuntimeException ex)
		{
			log.warn("Slayer Sensei could not read the Slayer cache tables. The points planner uses its built-in fallback.", ex);
			return null;
		}
		// The rest only adds detail to the plan, so a failure leaves it out instead of losing the tables
		try
		{
			data.loadTaskRewards(db);
		}
		catch (RuntimeException ex)
		{
			log.warn("Slayer Sensei could not read the task extensions from the cache", ex);
			data.extensions.clear();
			data.extensionOfTask.clear();
			data.weightings.clear();
			data.blockedBy.clear();
		}
		try
		{
			data.loadShop(db);
		}
		catch (RuntimeException ex)
		{
			log.warn("Slayer Sensei could not read the reward shop's Buy tab from the cache", ex);
			data.shopCosts.clear();
		}
		log.debug("Slayer Sensei cache: {} tasks, {} unlocks, master pools {}, {} extensions, Buy tab {}",
				data.taskNames.size(), data.unlocks.size(), data.pools.keySet(), data.extensions.size(), data.shopCosts);
		return data;
	}

	private void loadTasks(Db db)
	{
		for (int row : db.rows(DBTableID.SlayerTask.ID))
		{
			final String name = stringField(db, row, DBTableID.SlayerTask.COL_NAME_UPPERCASE);
			if (name != null && !name.isEmpty())
			{
				taskNames.put(row, name);
				taskRowsByName.computeIfAbsent(name.toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(row);
			}
			// The id the block-list varbits and the SLAYER_TARGET varp store. Boss sub-tasks have -1.
			final int id = intField(db, row, DBTableID.SlayerTask.COL_ID, -1);
			if (id > 0) taskRowById.put(id, row);
			// (level, stat) tuples, all of which a master checks before assigning the task
			final List<StatLevel> all = statLevels(db, row, DBTableID.SlayerTask.COL_MIN_STAT_REQUIREMENT_ALL);
			for (StatLevel s : all)
			{
				if (s.getStat() == STAT_SLAYER) slayerLevels.put(row, s.getLevel());
			}
			if (!all.isEmpty()) statsAll.put(row, all);
			// At least one of these, e.g. 60 Strength or 60 Agility for the God Wars Dungeon
			final List<StatLevel> any = statLevels(db, row, DBTableID.SlayerTask.COL_MIN_STAT_REQUIREMENT_ANY);
			if (!any.isEmpty()) statsAny.put(row, any);
			final int combat = intField(db, row, DBTableID.SlayerTask.COL_MIN_COMLEVEL, 0);
			if (combat > 0) combatLevels.put(row, combat);
		}
	}

	private static List<StatLevel> statLevels(Db db, int row, int column)
	{
		final Object[] levels = db.field(row, column, 0);
		final Object[] stats = db.field(row, column, 1);
		final List<StatLevel> out = new ArrayList<>();
		for (int i = 0; i < Math.min(levels.length, stats.length); i++)
		{
			final int stat = asInt(stats[i], -1);
			if (stat >= 0) out.add(new StatLevel(stat, asInt(levels[i], 0)));
		}
		return out;
	}

	private void loadUnlocks(Db db)
	{
		for (int row : db.rows(DBTableID.SlayerUnlock.ID))
		{
			// (tab, position) in the shop
			final int tab = intField(db, row, DBTableID.SlayerUnlock.COL_LIST_POSITION, -1);
			unlocks.put(row, new Unlock(row,
					stringField(db, row, DBTableID.SlayerUnlock.COL_NAME),
					intField(db, row, DBTableID.SlayerUnlock.COL_COST, -1),
					intField(db, row, DBTableID.SlayerUnlock.COL_BIT, -1),
					intList(db, row, DBTableID.SlayerUnlock.COL_RELATED_TASK),
					tab >= 0, Math.max(-1, tab)));
		}
	}

	/** Extensions, weightings and blockers, which each task row names with the reward that changes it. */
	private void loadTaskRewards(Db db)
	{
		for (int row : db.rows(DBTableID.SlayerTask.ID))
		{
			addExtensions(db, row, DBTableID.SlayerTask.COL_EXTENSION_MIN_MAX, false);
			addExtensions(db, row, DBTableID.SlayerTask.COL_EXTENSION_ADDITIVE, true);
			// (unlock, weight)
			final Object[] weightUnlocks = db.field(row, DBTableID.SlayerTask.COL_UNLOCK_WEIGHTING, 0);
			final Object[] weights = db.field(row, DBTableID.SlayerTask.COL_UNLOCK_WEIGHTING, 1);
			for (int i = 0; i < Math.min(weightUnlocks.length, weights.length); i++)
			{
				final int unlock = asInt(weightUnlocks[i], -1);
				if (unlock >= 0) weightings.computeIfAbsent(unlock, k -> new ArrayList<>()).add(new Weighting(unlock, row, asInt(weights[i], 0)));
			}
			for (Object blocker : db.field(row, DBTableID.SlayerTask.COL_BLOCK_UNLOCK, 0))
			{
				final int unlock = asInt(blocker, -1);
				if (unlock >= 0) blockedBy.computeIfAbsent(unlock, k -> new ArrayList<>()).add(row);
			}
		}
	}

	/** (unlock, min, max) tuples. */
	private void addExtensions(Db db, int row, int column, boolean additive)
	{
		final Object[] rewards = db.field(row, column, 0);
		final Object[] mins = db.field(row, column, 1);
		final Object[] maxes = db.field(row, column, 2);
		for (int i = 0; i < Math.min(rewards.length, Math.min(mins.length, maxes.length)); i++)
		{
			final int unlock = asInt(rewards[i], -1);
			if (unlock < 0) continue;
			final Extension e = new Extension(unlock, row, asInt(mins[i], 0), asInt(maxes[i], 0), additive);
			extensions.computeIfAbsent(unlock, k -> new ArrayList<>()).add(e);
			extensionOfTask.putIfAbsent(row, e);
		}
	}

	private void loadShop(Db db)
	{
		final int[][] slots = db.enumInts(SHOP_ITEMS_ENUM);
		final int[][] costs = db.enumInts(SHOP_COSTS_ENUM);
		final Map<Integer, Integer> cost = new HashMap<>();
		for (int i = 0; i < Math.min(costs[0].length, costs[1].length); i++)
		{
			cost.put(costs[0][i], costs[1][i]);
		}
		// Slot order, as the tab lists them
		final int[] order = slots[0].clone();
		Arrays.sort(order);
		for (int slot : order)
		{
			for (int i = 0; i < Math.min(slots[0].length, slots[1].length); i++)
			{
				if (slots[0][i] == slot && cost.containsKey(slots[1][i])) shopCosts.put(slots[1][i], cost.get(slots[1][i]));
			}
		}
	}

	private void loadPools(Db db)
	{
		for (int row : db.rows(DBTableID.SlayerMasterTask.ID))
		{
			final int master = intField(db, row, DBTableID.SlayerMasterTask.COL_MASTER_ID, -1);
			if (master < 0) continue;
			// The column could hold a list, but every pool row has at most one unlock
			final int unlock = intField(db, row, DBTableID.SlayerMasterTask.COL_TASK_UNLOCK, -1);
			if (unlock >= 0) poolUnlocks.add(unlock);
			pools.computeIfAbsent(master, k -> new ArrayList<>()).add(new PoolEntry(
					intField(db, row, DBTableID.SlayerMasterTask.COL_TASK, -1),
					intField(db, row, DBTableID.SlayerMasterTask.COL_WEIGHT, 0),
					unlock,
					intField(db, row, DBTableID.SlayerMasterTask.COL_MIN_AMOUNT, -1),
					intField(db, row, DBTableID.SlayerMasterTask.COL_MAX_AMOUNT, -1)));
		}
		// The task ratings' speed is judged at the amounts the regular masters give most of. Mortimer's
		// amounts move with his modifiers, so his count only when no other master assigns the task.
		final Map<Integer, Double> regular = new HashMap<>();
		final Map<Integer, Double> mortimer = new HashMap<>();
		for (Map.Entry<Integer, List<PoolEntry>> pool : pools.entrySet())
		{
			final SlayerAssigner master = SlayerAssigner.forId(pool.getKey());
			if (master == null) continue;
			for (PoolEntry e : pool.getValue())
			{
				if (e.getMinAmount() <= 0) continue;
				final double average = (e.getMinAmount() + e.getMaxAmount()) / 2.0;
				(master == SlayerAssigner.MORTIMER ? mortimer : regular).merge(e.getTaskRow(), average, Math::max);
			}
		}
		typicalAmounts.putAll(mortimer);
		typicalAmounts.putAll(regular);
	}

	Unlock unlock(int row)
	{
		return unlocks.get(row);
	}

	Collection<Unlock> unlocks()
	{
		return Collections.unmodifiableCollection(unlocks.values());
	}

	String taskName(int taskRow)
	{
		return taskNames.get(taskRow);
	}

	/** Rows of the tasks with this display name, ignoring case. */
	List<Integer> taskRowsNamed(String name)
	{
		final List<Integer> rows = taskRowsByName.get(name.toLowerCase(Locale.ROOT));
		return rows == null ? Collections.emptyList() : rows;
	}

	/** Block-list varbits store the task's numeric id, not its database row. */
	int taskRowForId(int taskId)
	{
		final Integer row = taskRowById.get(taskId);
		return row == null ? -1 : row;
	}

	/** Slayer level a master needs before assigning the task, 0 when it needs none. */
	int slayerLevel(int taskRow)
	{
		final Integer level = slayerLevels.get(taskRow);
		return level == null ? 0 : level;
	}

	/** Combat level a master needs before assigning the task, 0 when it needs none. */
	int combatLevel(int taskRow)
	{
		final Integer level = combatLevels.get(taskRow);
		return level == null ? 0 : level;
	}

	/** Every stat level the task needs, Slayer included. */
	List<StatLevel> statsAll(int taskRow)
	{
		final List<StatLevel> stats = statsAll.get(taskRow);
		return stats == null ? Collections.emptyList() : Collections.unmodifiableList(stats);
	}

	/** Stat levels of which the task needs one, or an empty list. */
	List<StatLevel> statsAny(int taskRow)
	{
		final List<StatLevel> stats = statsAny.get(taskRow);
		return stats == null ? Collections.emptyList() : Collections.unmodifiableList(stats);
	}

	/**
	 * The largest average amount any regular master assigns of the task, or Mortimer's when only he does.
	 * The task ratings judge speed at this amount. 0 for boss tasks and unknown rows.
	 */
	double typicalAmount(int taskRow)
	{
		final Double amount = typicalAmounts.get(taskRow);
		return amount == null ? 0 : amount;
	}

	/** The master's whole pool, including tasks the player has not unlocked or has blocked. */
	List<PoolEntry> pool(SlayerMaster master)
	{
		return pool(master.getId());
	}

	/** The pool of a game master id. Spria and Mortimer have pools of their own. */
	List<PoolEntry> pool(int masterId)
	{
		final List<PoolEntry> pool = pools.get(masterId);
		return pool == null ? Collections.emptyList() : Collections.unmodifiableList(pool);
	}

	/** Whether some master needs this unlock before assigning a task, i.e. it adds tasks. */
	boolean gatesPool(int unlockRow)
	{
		return poolUnlocks.contains(unlockRow);
	}

	/** The tasks a reward lengthens, one entry per task. Empty for every other reward. */
	List<Extension> extensions(int unlockRow)
	{
		final List<Extension> list = extensions.get(unlockRow);
		return list == null ? Collections.emptyList() : Collections.unmodifiableList(list);
	}

	/** The reward that lengthens a task, or null when the task has none. */
	Extension extensionOf(int taskRow)
	{
		return extensionOfTask.get(taskRow);
	}

	/** The task weights a reward changes, e.g. Chance of Heavy Frost. */
	List<Weighting> weightings(int unlockRow)
	{
		final List<Weighting> list = weightings.get(unlockRow);
		return list == null ? Collections.emptyList() : Collections.unmodifiableList(list);
	}

	/** Task rows a reward keeps out of every master's list without a block slot, e.g. Stop the Wyvern. */
	List<Integer> blockedBy(int unlockRow)
	{
		final List<Integer> list = blockedBy.get(unlockRow);
		return list == null ? Collections.<Integer>emptyList() : Collections.unmodifiableList(list);
	}

	/** The Buy tab's items in slot order, each with its cost in points. Empty when the enums could not be read. */
	Map<Integer, Integer> shopItems()
	{
		return Collections.unmodifiableMap(shopCosts);
	}

	/** Whether the reward on this bit is owned. {@code varp} reads a varp, e.g. {@code client::getVarpValue}. */
	static boolean ownsBit(IntUnaryOperator varp, int bit)
	{
		if (bit < 0 || bit >= UNLOCK_VARPS.length * 32) return false;
		return (varp.applyAsInt(UNLOCK_VARPS[bit / 32]) >>> (bit % 32) & 1) != 0;
	}

	/** First entry of a column, or the fallback when the row has no value. */
	private static int intField(Db db, int row, int column, int fallback)
	{
		final Object[] values = db.field(row, column, 0);
		return values == null || values.length == 0 ? fallback : asInt(values[0], fallback);
	}

	/** Every entry of a list column. */
	private static List<Integer> intList(Db db, int row, int column)
	{
		final List<Integer> out = new ArrayList<>();
		final Object[] values = db.field(row, column, 0);
		if (values == null) return out;
		for (Object value : values)
		{
			if (value instanceof Number) out.add(((Number) value).intValue());
		}
		return out;
	}

	private static String stringField(Db db, int row, int column)
	{
		final Object[] values = db.field(row, column, 0);
		if (values == null || values.length == 0 || !(values[0] instanceof String)) return null;
		return (String) values[0];
	}

	private static int asInt(Object value, int fallback)
	{
		return value instanceof Number ? ((Number) value).intValue() : fallback;
	}
}
