package com.slayercompanion;

import com.slayercompanion.data.TaskRating;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Value;

/**
 * One master's task pool as this player gets it: every pool row with its weight, whether the master can
 * assign it to the player, and the task's ratings. Pure. {@link SlayerGoalModel} turns a pool into a rate
 * per goal, {@link SlayerMasterRanker} ranks masters with it and {@link SlayerBlockAdvisor} picks blocks.
 * <p>
 * Eligibility follows the cache tables, checked against a dump of the live cache (tools/cache-dump): every
 * stat level in COL_MIN_STAT_REQUIREMENT_ALL (Slayer, and e.g. 50 Magic for cave kraken or 87 Sailing for
 * frost dragons), one of COL_MIN_STAT_REQUIREMENT_ANY (60 Strength or 60 Agility for the God Wars Dungeon),
 * COL_MIN_COMLEVEL, the reward a pool row needs (COL_TASK_UNLOCK) and the tasks a bought blocker takes out
 * of every pool (Stop the Wyvern). The combat level is skipped when the player switched combat checks off
 * at a master (wiki Slayer training, "Combat levels") and by Krystilia, who "will assign any monster
 * regardless of your combat level" (wiki Krystilia). Quest requirements are not in the tables, so a few
 * tasks can still be out of reach.
 */
final class SlayerPool
{
	enum Status
	{
		/** The master can assign it. */
		OPEN,
		/** On the block list. The master could assign it otherwise. */
		BLOCKED,
		/** Needs a reward the player does not own, e.g. Like a Boss. */
		NEEDS_UNLOCK,
		/** Needs a Slayer, other stat or combat level the player lacks. */
		NEEDS_LEVEL,
		/** Taken out of every pool by a bought blocker, e.g. Stop the Wyvern. */
		REMOVED
	}

	/** Everything about the player a pool depends on, apart from the master's block list. */
	@Value
	static class Player
	{
		/** Real levels by {@link net.runelite.api.Skill} ordinal, the order the cache's stat ids use. */
		int[] levels;
		int combatLevel;
		/** COMBAT_LEVEL_REQS_DISABLED: the player told the masters to ignore their combat level. */
		boolean combatChecksOff;
		Set<Integer> ownedUnlocks;
		/** Task rows a bought blocker keeps out of every pool. */
		Set<Integer> removedTasks;

		int level(int stat)
		{
			return stat >= 0 && stat < levels.length ? levels[stat] : 0;
		}
	}

	@Value
	@AllArgsConstructor
	static class Entry
	{
		/** The wiki's advice bounds an XP rating at these: a task to do is fair at least, one to skip poor at best. */
		static final int FAIR = 3;
		static final int POOR = 2;

		int taskRow;
		String name;
		int weight;
		/** Fewest and most kills the master assigns, -1 for boss tasks where the player picks. */
		int minAmount;
		int maxAmount;
		/** The amount the rating's speed was judged at, see {@link SlayerGameData#typicalAmount}. */
		double typicalAmount;
		Status status;
		/** The ratings that apply at this master: Krystilia's Wilderness version where there is one. Null when unrated. */
		TaskRating rating;
		/**
		 * Minutes per assignment from the task's own estimate at this master, the minutes between tasks included, or 0
		 * when the speed rating and the amounts decide. Turael's and Spria's filler tasks have one.
		 */
		double minutes;
		/** The wiki's advice on the task for XP at this master, or null. Krystilia's Wilderness tasks have none. */
		TaskRating.XpAdvice xpAdvice;

		/** A task timed by its speed rating and amounts. */
		Entry(int taskRow, String name, int weight, int minAmount, int maxAmount, double typicalAmount, Status status, TaskRating rating)
		{
			this(taskRow, name, weight, minAmount, maxAmount, typicalAmount, status, rating, 0, null);
		}

		/** A task with its own time estimate. */
		Entry(int taskRow, String name, int weight, int minAmount, int maxAmount, double typicalAmount, Status status, TaskRating rating,
				double minutes)
		{
			this(taskRow, name, weight, minAmount, maxAmount, typicalAmount, status, rating, minutes, null);
		}

		boolean isOpen()
		{
			return status == Status.OPEN;
		}

		/** Whether the master can assign it to this player once it is off the block list. */
		boolean isAssignable()
		{
			return status == Status.OPEN || status == Status.BLOCKED;
		}

		boolean isRated()
		{
			return rating != null && rating.isComplete();
		}

		/**
		 * The task's rating for a goal, 0 when unrated. For XP the wiki's advice bounds it: a task it says to do counts
		 * as fair at least, one it says to skip or not to unlock as poor at best. Every XP decision reads this.
		 */
		int rating(SlayerGoal goal)
		{
			if (!isRated()) return 0;
			final int r = rating.forGoal(goal);
			final TaskRating.XpAdvice advice = wikiAdvice(goal);
			if (advice == null) return r;
			return advice == TaskRating.XpAdvice.DO ? Math.max(r, FAIR) : Math.min(r, POOR);
		}

		/** The wiki's advice on the task for the goal, or null. Only XP has one. */
		TaskRating.XpAdvice wikiAdvice(SlayerGoal goal)
		{
			return goal == SlayerGoal.XP ? xpAdvice : null;
		}

		/** Whether the wiki says to do the task for the goal, so no skip or block may touch it. */
		boolean wikiSaysDo(SlayerGoal goal)
		{
			return wikiAdvice(goal) == TaskRating.XpAdvice.DO;
		}

		/** Average kills per assignment, or -1 for boss tasks. */
		double averageAmount()
		{
			return minAmount > 0 ? (minAmount + maxAmount) / 2.0 : -1;
		}

		/** A boss task, where the player picks the amount. It cannot be blocked, only toggled off with Like a Boss. */
		boolean isBoss()
		{
			return minAmount < 0;
		}

		Entry withStatus(Status status)
		{
			return new Entry(taskRow, name, weight, minAmount, maxAmount, typicalAmount, status, rating, minutes, xpAdvice);
		}

		/** The same task with another amount, e.g. once an extension is bought. */
		Entry withAmounts(int min, int max)
		{
			return new Entry(taskRow, name, weight, min, max, typicalAmount, status, rating, minutes, xpAdvice);
		}

		/** The same task with another weight, e.g. Chance of Heavy Frost. */
		Entry withWeight(int weight)
		{
			return new Entry(taskRow, name, weight, minAmount, maxAmount, typicalAmount, status, rating, minutes, xpAdvice);
		}
	}

	@Getter
	private final SlayerAssigner master;
	@Getter
	private final List<Entry> entries;

	private SlayerPool(SlayerAssigner master, List<Entry> entries)
	{
		this.master = master;
		this.entries = Collections.unmodifiableList(entries);
	}

	/**
	 * @param blockedRows task rows on the master's block list
	 * @param ratings a task's ratings by its cache name, null when unknown. The dataset's lookup.
	 */
	static SlayerPool of(SlayerAssigner master, SlayerGameData data, Player player, Set<Integer> blockedRows,
			Function<String, TaskRating> ratings)
	{
		final List<Entry> entries = new ArrayList<>();
		for (SlayerGameData.PoolEntry e : data.pool(master.getId()))
		{
			final String name = data.taskName(e.getTaskRow());
			TaskRating rating = name == null ? null : ratings.apply(name);
			if (rating != null && master == SlayerAssigner.KRYSTILIA && rating.getWilderness() != null)
			{
				rating = rating.getWilderness();
			}
			// Turael and Spria give small amounts, so their tasks are timed by the trip, not by other masters' amounts
			final boolean filler = master == SlayerAssigner.TURAEL || master == SlayerAssigner.SPRIA;
			final double minutes = filler && rating != null && rating.getFiller() != null ? rating.getFiller().getMinutes() : 0;
			// The wiki's XP advice is for the regular masters, not Krystilia's Wilderness tasks
			final TaskRating.XpAdvice advice = rating == null || master == SlayerAssigner.KRYSTILIA ? null : rating.getXpAdvice();
			entries.add(new Entry(e.getTaskRow(), name == null ? "Task row " + e.getTaskRow() : name, e.getWeight(),
					e.getMinAmount(), e.getMaxAmount(), data.typicalAmount(e.getTaskRow()),
					status(master, data, player, blockedRows, e), rating, minutes, advice));
		}
		return new SlayerPool(master, entries);
	}

	/** A pool of ready-made entries, for tests and what-if pools. */
	static SlayerPool of(SlayerAssigner master, List<Entry> entries)
	{
		return new SlayerPool(master, new ArrayList<>(entries));
	}

	/** The same pool with some entries changed, e.g. a task opened by an unlock or lengthened by an extension. */
	SlayerPool map(UnaryOperator<Entry> change)
	{
		final List<Entry> out = new ArrayList<>();
		for (Entry e : entries)
		{
			out.add(change.apply(e));
		}
		return new SlayerPool(master, out);
	}

	private static Status status(SlayerAssigner master, SlayerGameData data, Player player, Set<Integer> blockedRows,
			SlayerGameData.PoolEntry e)
	{
		final int row = e.getTaskRow();
		if (player.getRemovedTasks().contains(row)) return Status.REMOVED;
		if (e.getUnlockRow() >= 0 && !player.getOwnedUnlocks().contains(e.getUnlockRow())) return Status.NEEDS_UNLOCK;
		if (!meetsLevels(master, data, player, row)) return Status.NEEDS_LEVEL;
		return blockedRows.contains(row) ? Status.BLOCKED : Status.OPEN;
	}

	static boolean meetsLevels(SlayerAssigner master, SlayerGameData data, Player player, int row)
	{
		for (SlayerGameData.StatLevel s : data.statsAll(row))
		{
			if (player.level(s.getStat()) < s.getLevel()) return false;
		}
		final List<SlayerGameData.StatLevel> any = data.statsAny(row);
		boolean anyMet = any.isEmpty();
		for (SlayerGameData.StatLevel s : any)
		{
			anyMet |= player.level(s.getStat()) >= s.getLevel();
		}
		if (!anyMet) return false;
		final boolean combatChecked = !player.isCombatChecksOff() && master != SlayerAssigner.KRYSTILIA;
		return !combatChecked || player.getCombatLevel() >= data.combatLevel(row);
	}

	/** The same pool with these rows blocked and every other block lifted. */
	SlayerPool withBlocks(Collection<Integer> rows)
	{
		final Set<Integer> blocked = new HashSet<>(rows);
		final List<Entry> out = new ArrayList<>();
		for (Entry e : entries)
		{
			if (!e.isAssignable()) out.add(e);
			else out.add(e.withStatus(blocked.contains(e.getTaskRow()) ? Status.BLOCKED : Status.OPEN));
		}
		return new SlayerPool(master, out);
	}

	/** The tasks the master can give right now. */
	List<Entry> open()
	{
		final List<Entry> out = new ArrayList<>();
		for (Entry e : entries)
		{
			if (e.isOpen()) out.add(e);
		}
		return out;
	}

	Entry find(int taskRow)
	{
		for (Entry e : entries)
		{
			if (e.getTaskRow() == taskRow) return e;
		}
		return null;
	}

	/** Weight of the tasks the master can give right now. */
	int openWeight()
	{
		int w = 0;
		for (Entry e : entries)
		{
			if (e.isOpen()) w += e.getWeight();
		}
		return w;
	}

	/** Weight of the tasks the player has the requirements for, blocked or not. */
	int assignableWeight()
	{
		int w = 0;
		for (Entry e : entries)
		{
			if (e.isAssignable()) w += e.getWeight();
		}
		return w;
	}

	int blockedWeight()
	{
		int w = 0;
		for (Entry e : entries)
		{
			if (e.getStatus() == Status.BLOCKED) w += e.getWeight();
		}
		return w;
	}

	/** Chance the next task from this master is this one, 0 when the master cannot give it now. */
	double chance(Entry entry)
	{
		final int open = openWeight();
		return entry.isOpen() && open > 0 ? entry.getWeight() / (double) open : 0;
	}

	/**
	 * The goal's rating averaged over the tasks the master can give now, by their chance of coming up:
	 * the "expected rating" of the pool, 1 to 5. Unrated tasks are left out. -1 when nothing is rated.
	 */
	double expected(SlayerGoal goal)
	{
		double sum = 0;
		int weight = 0;
		for (Entry e : entries)
		{
			if (!e.isOpen() || !e.isRated()) continue;
			sum += e.getWeight() * (double) e.rating(goal);
			weight += e.getWeight();
		}
		return weight > 0 ? sum / weight : -1;
	}

	/** Chance the next task is rated at least {@code min} for the goal, among the rated tasks the master can give now. */
	double share(SlayerGoal goal, int min)
	{
		int good = 0;
		int weight = 0;
		for (Entry e : entries)
		{
			if (!e.isOpen() || !e.isRated()) continue;
			weight += e.getWeight();
			if (e.rating(goal) >= min) good += e.getWeight();
		}
		return weight > 0 ? good / (double) weight : 0;
	}
}
