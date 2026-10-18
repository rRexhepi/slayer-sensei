package com.slayercompanion;

import com.slayercompanion.SlayerPointsPlanner.Candidate;
import com.slayercompanion.SlayerPointsPlanner.Type;
import com.slayercompanion.data.SlayerTask;
import com.slayercompanion.data.TaskRating;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import lombok.Value;
import net.runelite.api.Skill;

/**
 * What each way of spending Slayer points does for the goal at the plan's master: a shop reward, a block or an
 * item from the Buy tab. Pure. The rate is the one the block advice uses, {@link SlayerGoalModel#rate(SlayerPool,
 * SlayerGoal)} over the master's list. A purchase's gain is how much it raises that rate, divided by the rate
 * today, so every purchase is on one scale: 0.02 adds 2% of today's rate, and below zero it costs. Skips are left
 * out of it. The reserve pays for them.
 * <p>
 * On the Slayer points routine the rate is points per hour over a whole cycle ({@link SlayerGoalModel#cycleRate}):
 * the filler tasks from Turael's list, which Aya and Spria share, and one milestone task from the plan master's
 * list. Blocks at both masters are valued on it, each at its own master's cost and within that list's open slots,
 * and the documented effects below apply to the tasks of both lists.
 * <ul>
 * <li>Blocks: each block a list's block advice puts forward, in its order, on top of the blocks it keeps and the
 * ones before it. The other list stays as it is today.</li>
 * <li>Everything else is valued with those blocks in place, so the plan never lengthens a task it also blocks.</li>
 * <li>Extensions: the list with the task at its extended amount from the cache (SlayerTask COL_EXTENSION_MIN_MAX
 * and COL_EXTENSION_ADDITIVE). That is more time on the task, which helps when the task is better than the
 * master's average for the goal. The average is the one the skip model leaves, as the master ranking and the
 * reserve assume the worst offers are skipped. Only a task rated good or better for the goal is ever worth it ("Only
 * extend tasks that have good value to them", wiki Slayer training, Task extension), and never one that section
 * advises against. For Slayer points the rate is tasks finished an hour, which a longer task always lowers.</li>
 * <li>Task unlocks and Chance of Heavy Frost: the list with the task added, or heavier. For XP an unlock whose task the
 * wiki's Slayer guide says not to unlock is never recommended and always named under Skip these.</li>
 * <li>Stop the Wyvern: fossil wyverns gone from the list, and a slot they took or would take used for the next
 * best block. When the plan buys it now, the block advice already leaves them out, so only their going counts.</li>
 * <li>The rest: the documented effects below, each applied to the tasks it touches in the master's list, so the
 * value follows the list. They are judgments from the wiki pages named, read 4 October 2026. A speed-up raises
 * every goal's rate alike, since the same task in less time leaves more time for the rest.</li>
 * </ul>
 * Rewards that only matter for a task the player cannot be given yet are valued as if they could, and carry the
 * missing level, so the plan can list them under the unlocks that wait for a level.
 */
final class SlayerSpend
{
	/**
	 * A superior spawns once in 200 kills on task and gives "about ten times the hitpoints of the superior" in
	 * Slayer XP (wiki Superior slayer monster). The wiki's table puts that at 26 to 32 kills of the regular monster
	 * for most of them, so about 14% more Slayer XP on a task that has one.
	 */
	static final double SUPERIOR_XP = 1.14;
	/**
	 * Superiors roll the monster's drop table three times, have the imbued heart and eternal gem tables and drop
	 * clue scrolls ten times as often (wiki). Judgment: about 3% more loot on a task that has one.
	 */
	static final double SUPERIOR_LOOT = 1.03;
	/** A superior takes about as long as five regular kills. Judgment. */
	static final double SUPERIOR_TIME = 1.025;
	/**
	 * The helmet keeps the black mask's 16.67% accuracy and damage (15% for ranged and magic once imbued) on tasks
	 * that need a nose peg, earmuffs, facemask, spiny helmet or goggles (wiki Slayer helmet, Slayer training).
	 * Accuracy and damage together make kills about a quarter faster. Judgment.
	 */
	static final double HELMET_TIME = 0.8;
	/** Gargoyle Smasher and the like: "players will save a large amount of time" (wiki Slayer training). Judgment: 15%. */
	static final double FINISHER_TIME = 0.85;
	/** Slayer rings teleport to six task areas (cache, item params). Judgment: a minute less travel on tasks there. */
	static final double RING_MINUTES_SAVED = 1;
	/** Herb sack: room for herb drops on tasks that drop them (wiki Herb sack). Judgment: 10% more loot there. */
	static final double HERB_SACK_LOOT = 1.1;
	/** Rune pouch: three rune types in one slot, so fewer bank trips on barrage and burst tasks. Judgment: 5% faster. */
	static final double RUNE_POUCH_TIME = 0.95;
	/** Looting bag: more loot carried per trip on Krystilia's Wilderness tasks. Judgment: 5% more loot. */
	static final double LOOTING_BAG_LOOT = 1.05;

	/** Where a Slayer ring teleports to, as the dossier names the places (cache, Slayer ring (8) params). */
	private static final String[] RING_PLACES = {"stronghold slayer cave", "slayer tower", "fremennik slayer dungeon",
		"tarn", "mourner tunnels", "wyrmscraig"};

	/** Everything a valuation reads. */
	@Value
	static class Context
	{
		SlayerGoal goal;
		/** The plan's master spot, and the NPC the player finds there. */
		SlayerAssigner master;
		String npc;
		/** The master's list as the player gets it, with the current blocks. Null without the cache tables. */
		SlayerPool pool;
		/** Null without the cache tables. */
		SlayerGameData data;
		SlayerPool.Player player;
		/** Task rows on the master's block list. */
		Set<Integer> blockedRows;
		/** Open block slots with nothing in them. */
		int emptySlots;
		/** The block advice at the master. Null without the cache tables. */
		SlayerBlockAdvisor.Advice blocks;
		/** Rows of the rewards the player owns. */
		Set<Integer> owned;
		/** The dossier entry for a task's cache name, or null. */
		Function<String, SlayerTask> dossier;
		SlayerShopItem.Sightings items;
		/** The routine's filler tasks when the plan's master takes the milestone tasks of the points routine, else null. */
		Routine routine;
		/**
		 * The most of the plan master's offers the skip model skips, as a share, 0 when the goal skips nothing (Slayer
		 * points, Mortimer's choice). See {@link SlayerMasterRanker#skipBudget}.
		 */
		double skipBudget;

		/** The same context with another block advice at the master. */
		Context withBlocks(SlayerBlockAdvisor.Advice blocks)
		{
			return new Context(goal, master, npc, pool, data, player, blockedRows, emptySlots, blocks, owned, dossier, items, routine, skipBudget);
		}
	}

	/**
	 * The Slayer points routine's filler tasks: every task but the milestone goes to Turael's spot. Turael, Aya and
	 * Spria share one block list.
	 */
	@Value
	static class Routine
	{
		/** Filler tasks per milestone task: 9 for every 10th task, 49 for every 50th. */
		int fillers;
		/** Points the milestone task pays on average. It sets the size of the rate, never a comparison. */
		double points;
		/** The NPC at Turael's spot, e.g. Aya. */
		String npc;
		/** Turael's list as the player gets it, with the current blocks. */
		SlayerPool pool;
		/** Open block slots with nothing in them on that list. */
		int emptySlots;
		/** The block advice for that list. */
		SlayerBlockAdvisor.Advice blocks;
	}

	/** A context with the lists everything but the blocks is valued against, and the rate gains are divided by. */
	private static final class Model
	{
		final Context c;
		/** The plan master's list with the advised blocks in place. */
		final SlayerPool pool;
		/** On the routine, the filler list with its advised blocks in place, else null. */
		final SlayerPool fillers;
		/** The goal's rate today. */
		final double base;

		Model(Context c)
		{
			this.c = c;
			this.pool = c.getPool() == null ? null : planned(c.getPool(), c.getBlocks());
			final Routine r = c.getRoutine();
			this.fillers = r == null || r.getPool() == null ? null : planned(r.getPool(), r.getBlocks());
			this.base = c.getPool() == null ? 0 : rate(c.getPool(), r == null ? null : r.getPool());
		}

		SlayerGoal goal()
		{
			return c.getGoal();
		}

		/** The goal's rate with this list at the plan's master and, on the routine, this filler list. */
		double rate(SlayerPool list, SlayerPool fillerList)
		{
			final Routine r = c.getRoutine();
			if (r == null || fillerList == null) return SlayerGoalModel.rate(list, goal());
			return SlayerGoalModel.cycleRate(fillerList, list, r.getFillers(), r.getPoints());
		}

		/** The gain from {@code before} to {@code after} at the plan's master, as a share of today's rate. */
		double gain(SlayerPool before, SlayerPool after)
		{
			return base <= 0 ? 0 : (rate(after, fillers) - rate(before, fillers)) / base;
		}

		/** {@link #gain} with the skip model's skips on both sides, as the master ranking and the reserve assume. */
		double skipGain(SlayerPool before, SlayerPool after)
		{
			if (base <= 0 || c.getSkipBudget() <= 0 || c.getRoutine() != null) return gain(before, after);
			return (withSkips(after, goal(), c.getMaster(), c.getSkipBudget()) - withSkips(before, goal(), c.getMaster(), c.getSkipBudget()))
					/ base;
		}

		/** The gain from a change in how tasks go, over {@code list} and, on the routine, the filler tasks too. */
		double effect(SlayerPool list, ToDoubleFunction<SlayerPool.Entry> time, ToDoubleFunction<SlayerPool.Entry> yield,
				ToDoubleFunction<SlayerPool.Entry> overhead)
		{
			if (base <= 0) return 0;
			final List<SlayerGoalModel.Offer> offers = SlayerGoalModel.offers(list);
			final Routine r = c.getRoutine();
			if (r == null || fillers == null)
			{
				return (SlayerGoalModel.rate(offers, goal(), time, yield, overhead) - SlayerGoalModel.rate(offers, goal(), 0, 0)) / base;
			}
			// Slayer points: what a task earns is its points, which no reward changes, so only the time counts
			final List<SlayerGoalModel.Offer> filler = SlayerGoalModel.offers(fillers);
			final double after = SlayerGoalModel.cycleRate(SlayerGoalModel.minutesPerTask(filler, time, overhead),
					SlayerGoalModel.minutesPerTask(offers, time, overhead), r.getFillers(), r.getPoints());
			final double before = SlayerGoalModel.cycleRate(SlayerGoalModel.minutesPerTask(filler),
					SlayerGoalModel.minutesPerTask(offers), r.getFillers(), r.getPoints());
			return (after - before) / base;
		}
	}

	/** A list with its advised blocks in place. */
	private static SlayerPool planned(SlayerPool list, SlayerBlockAdvisor.Advice blocks)
	{
		if (blocks == null) return list;
		final List<Integer> rows = new ArrayList<>(blocks.getKeepRows());
		rows.addAll(blocks.getAddRows());
		return list.withBlocks(rows);
	}

	private SlayerSpend()
	{
	}

	/**
	 * Every reward, block and item, valued. {@code names} gives a reward's name, null for one taken out of the
	 * shop. {@code costs} and {@code itemCosts} give the cache's costs, null when unknown, and an item cost below
	 * zero means the Buy tab no longer sells it.
	 */
	static List<Candidate> candidates(Context c, Function<SlayerReward, String> names, Function<SlayerReward, Integer> costs,
			Function<SlayerShopItem, Integer> itemCosts)
	{
		final Model m = new Model(c);
		final List<Candidate> out = new ArrayList<>();
		boolean extensionHurts = false;
		for (SlayerReward r : SlayerReward.values())
		{
			final String name = names.apply(r);
			// A reward taken out of the shop keeps its cache row but can no longer be bought
			if (name == null) continue;
			final Candidate candidate = reward(m, r, name, costs.apply(r));
			if (r.getKind() == SlayerReward.Kind.EXTENSION && c.getGoal() == SlayerGoal.POINTS && !candidate.isOwned())
			{
				// For points every extension hurts the same way, so one line says it for all of them
				extensionHurts |= candidate.getGain() < -SlayerPointsPlanner.NOTICEABLE && candidate.getLockedBy() == null;
				out.add(candidate.withGain(Double.NaN));
				continue;
			}
			out.add(candidate);
		}
		if (extensionHurts)
		{
			out.add(new Candidate(Type.SUMMARY, null, "Task extensions", -1, false, false, -1,
					"Longer tasks mean fewer tasks finished an hour, and points are paid per task.", null, null));
		}
		out.addAll(blocks(m));
		for (SlayerShopItem item : SlayerShopItem.values())
		{
			final Integer cost = itemCosts.apply(item);
			// A negative cost: the Buy tab no longer sells it
			if (cost == null || cost >= 0) out.add(item(m, item, cost));
		}
		return out;
	}

	// ---- shop rewards ----

	private static Candidate reward(Model m, SlayerReward r, String name, Integer cost)
	{
		final Context c = m.c;
		final boolean owned = c.getOwned().contains(r.getRow());
		final int price = cost == null ? r.getFallbackCost() : cost;
		if (r.getKind() == SlayerReward.Kind.COSMETIC)
		{
			return new Candidate(Type.REWARD, r, name, price, owned, true, 0, null, null, null);
		}
		final Valued v = owned ? NONE : value(m, r);
		final String lockedBy = v.lockedBy != null ? v.lockedBy : SlayerPlanBuilder.lockedBy(r, c.getPlayer());
		return new Candidate(Type.REWARD, r, name, price, owned, false, v.gain, sentence(v.why, r.getFact()), lockedBy, null, null, v.avoid);
	}

	/** A value with its reason and, for a task the player cannot be given yet, the level it waits for. */
	private static final class Valued
	{
		final double gain;
		final String why;
		final String lockedBy;
		/** The wiki's Slayer guide says not to buy it for the goal. */
		final boolean avoid;

		Valued(double gain, String why, String lockedBy)
		{
			this(gain, why, lockedBy, false);
		}

		Valued(double gain, String why, String lockedBy, boolean avoid)
		{
			this.gain = gain;
			this.why = why;
			this.lockedBy = lockedBy;
			this.avoid = avoid;
		}
	}

	private static final Valued NONE = new Valued(0, null, null);

	private static Valued value(Model m, SlayerReward r)
	{
		if (m.pool == null || m.c.getData() == null)
		{
			// Without the cache tables there is no list to judge against
			return new Valued(Double.NaN, null, null);
		}
		switch (r.getKind())
		{
			case EXTENSION:
				return extension(m, r);
			case TASK_UNLOCK:
				return taskUnlock(m, r);
			case WEIGHTING:
				return weighting(m, r);
			case BLOCKER:
				return blocker(m, r);
			case TASK_HELPER:
				return helper(m, r);
			default:
				return general(m, r);
		}
	}

	private static Valued extension(Model m, SlayerReward r)
	{
		final Map<Integer, SlayerGameData.Extension> extensions = new HashMap<>();
		for (SlayerGameData.Extension e : m.c.getData().extensions(r.getRow())) extensions.put(e.getTaskRow(), e);
		final Waiting w = waiting(m, extensions.keySet());
		if (w == null) return NONE;
		final SlayerPool after = w.pool.map(e ->
		{
			final SlayerGameData.Extension x = e.isOpen() && !e.isBoss() ? extensions.get(e.getTaskRow()) : null;
			return x == null ? e : e.withAmounts(x.extendedMin(e.getMinAmount()), x.extendedMax(e.getMaxAmount()));
		});
		final double gain = m.skipGain(w.pool, after);
		return new Valued(extendable(w.entry, m.goal()) ? gain : Math.min(0, gain),
				"More time on " + w.entry.getName() + ", " + judge(w.entry, m.goal()) + ".", w.lockedBy);
	}

	/** Ratings from this one up count as good for a goal. */
	static final int GOOD = 4;

	/**
	 * Whether more time on a task can be worth points at all: rated good or better for the goal, and not a task the
	 * wiki advises against extending.
	 */
	static boolean extendable(SlayerPool.Entry e, SlayerGoal goal)
	{
		return e.isRated() && e.rating(goal) >= GOOD && !e.getRating().isExtensionDiscouraged();
	}

	/** The goal's rate over a list when the skip model skips the worst offers, at most {@code budget} of them. */
	private static double withSkips(SlayerPool list, SlayerGoal goal, SlayerAssigner master, double budget)
	{
		return SlayerGoalModel.rateWithSkips(SlayerGoalModel.offers(list), goal, SlayerMasterRanker.bonus(master, goal), budget);
	}

	private static Valued taskUnlock(Model m, SlayerReward r)
	{
		final Context c = m.c;
		// The pool rows this reward gates at the master. Its related tasks can be in the list without it mattering.
		final Set<Integer> rows = new HashSet<>();
		for (SlayerGameData.PoolEntry e : c.getData().pool(c.getMaster().getId()))
		{
			if (e.getUnlockRow() == r.getRow()) rows.add(e.getTaskRow());
		}
		final List<SlayerPool.Entry> added = new ArrayList<>();
		String lockedBy = null;
		boolean anyOpen = false;
		for (SlayerPool.Entry e : m.pool.getEntries())
		{
			if (!rows.contains(e.getTaskRow()) || e.getStatus() != SlayerPool.Status.NEEDS_UNLOCK) continue;
			added.add(e);
			if (SlayerPool.meetsLevels(c.getMaster(), c.getData(), c.getPlayer(), e.getTaskRow())) anyOpen = true;
			else if (lockedBy == null) lockedBy = missingLevel(c, e.getTaskRow());
		}
		if (added.isEmpty()) return NONE;
		final boolean later = !anyOpen;
		// A task waiting for a level is judged as if the level were there, so the line can say what it would do
		final SlayerPool after = m.pool.map(e -> !rows.contains(e.getTaskRow()) || e.getStatus() != SlayerPool.Status.NEEDS_UNLOCK ? e
				: e.withStatus(c.getBlockedRows().contains(e.getTaskRow()) ? SlayerPool.Status.BLOCKED
				: later || SlayerPool.meetsLevels(c.getMaster(), c.getData(), c.getPlayer(), e.getTaskRow())
				? SlayerPool.Status.OPEN : SlayerPool.Status.NEEDS_LEVEL));
		final String list = possessive(c.getNpc()) + " list";
		// For XP the wiki's Slayer guide says not to unlock some tasks at all, the boss tasks unless for the helmet's bonus
		boolean dontUnlock = false;
		for (SlayerPool.Entry e : added) dontUnlock |= e.wikiAdvice(m.goal()) == TaskRating.XpAdvice.DONT_UNLOCK;
		if (dontUnlock)
		{
			final boolean bosses = added.get(0).isBoss();
			return new Valued(Math.min(0, m.gain(m.pool, after)), "The wiki's Slayer guide says not to unlock " + (bosses ? "boss tasks" : names(added))
					+ " for XP" + (bosses ? " unless you want the Slayer helmet's bonus on bosses." : "."), later ? lockedBy : null, true);
		}
		final String why;
		if (added.size() > 1) why = "Adds " + names(added) + " to " + list + ".";
		else if (added.get(0).isBoss()) why = "Adds boss tasks to " + list + ". Each is " + judge(added.get(0), m.goal()) + ".";
		else why = "Adds " + added.get(0).getName() + ", " + judge(added.get(0), m.goal()) + ", to " + list + ".";
		return new Valued(m.gain(m.pool, after), why, later ? lockedBy : null);
	}

	private static Valued weighting(Model m, SlayerReward r)
	{
		final Map<Integer, Integer> weights = new HashMap<>();
		for (SlayerGameData.Weighting x : m.c.getData().weightings(r.getRow())) weights.put(x.getTaskRow(), x.getWeight());
		final Waiting w = waiting(m, weights.keySet());
		if (w == null) return NONE;
		final SlayerPool after = w.pool.map(e -> e.isOpen() && weights.containsKey(e.getTaskRow()) ? e.withWeight(weights.get(e.getTaskRow())) : e);
		return new Valued(m.gain(w.pool, after), "Makes " + w.entry.getName() + ", " + judge(w.entry, m.goal())
				+ ", come up more often at " + m.c.getNpc() + ".", w.lockedBy);
	}

	/**
	 * Stop the Wyvern takes the task out of every list. Valued against the advised block list with and without it:
	 * a slot fossil wyverns take, or would take, then holds the next best block. When the plan buys it now, the block
	 * advice already leaves them out (see {@link SlayerPlanBuilder}), so the value is their going on top of those blocks.
	 */
	private static Valued blocker(Model m, SlayerReward r)
	{
		final Context c = m.c;
		final Set<Integer> rows = new HashSet<>(c.getData().blockedBy(r.getRow()));
		if (rows.isEmpty()) rows.addAll(taskRows(c, r));
		boolean present = false;
		String lockedBy = null;
		for (SlayerPool.Entry e : c.getPool().getEntries())
		{
			if (!rows.contains(e.getTaskRow())) continue;
			if (e.isAssignable()) present = true;
			else if (e.getStatus() == SlayerPool.Status.NEEDS_LEVEL && lockedBy == null) lockedBy = missingLevel(c, e.getTaskRow());
		}
		final boolean later = !present && lockedBy != null;
		if (!present && !later) return NONE;
		final SlayerPool pool = later ? open(c.getPool(), rows) : c.getPool();
		final List<Integer> before = new ArrayList<>();
		if (c.getBlocks() != null)
		{
			before.addAll(c.getBlocks().getKeepRows());
			before.addAll(c.getBlocks().getAddRows());
		}
		final SlayerPool gone = pool.map(e -> rows.contains(e.getTaskRow()) && e.isAssignable() ? e.withStatus(SlayerPool.Status.REMOVED) : e);
		final List<Integer> fixed = new ArrayList<>(before);
		fixed.removeAll(rows);
		final int slots = c.getBlocks() == null ? 0 : c.getBlocks().getSlots();
		final List<SlayerPool.Entry> extra = SlayerGoalModel.bestBlocks(gone, m.goal(), fixed, Math.max(0, slots - fixed.size()));
		final List<Integer> after = new ArrayList<>(fixed);
		for (SlayerPool.Entry e : extra) after.add(e.getTaskRow());
		final StringBuilder why = new StringBuilder("Blocks ").append(name(c, rows)).append(" at every master without a slot.");
		boolean usesSlot = false;
		for (int row : rows) usesSlot |= before.contains(row);
		if (usesSlot && !extra.isEmpty())
		{
			why.append(" Once it is bought, their slot frees up for ").append(extra.get(extra.size() - 1).getName()).append('.');
		}
		return new Valued(m.gain(pool.withBlocks(before), gone.withBlocks(after)), why.toString(), later ? lockedBy : null);
	}

	/** "The slot goes to Drakes instead.": the task a slot goes to once the plan buys Stop the Wyvern now. */
	static String slotGoesTo(String task)
	{
		return "The slot goes to " + task + " instead.";
	}

	private static Valued helper(Model m, SlayerReward r)
	{
		final Set<Integer> rows = taskRows(m.c, r);
		final Waiting w = waiting(m, rows);
		// On the routine a filler task counts too, e.g. lizards at Turael's spot
		final SlayerPool.Entry filler = m.fillers == null ? null : firstOpen(m.fillers, rows);
		if (w == null && filler == null) return NONE;
		final boolean now = filler != null || w.lockedBy == null;
		final SlayerPool.Entry entry = w != null && w.lockedBy == null ? w.entry : filler != null ? filler : w.entry;
		final double gain = m.effect(now ? m.pool : w.pool, e -> rows.contains(e.getTaskRow()) ? FINISHER_TIME : 1, e -> 1,
				e -> SlayerGoalModel.OVERHEAD_MINUTES);
		return new Valued(gain, "Finishes " + entry.getName() + " for you at low health, so those tasks go faster.", now ? null : w.lockedBy);
	}

	/** The first rated task of these rows the list gives now, or null. */
	private static SlayerPool.Entry firstOpen(SlayerPool list, Set<Integer> rows)
	{
		for (SlayerPool.Entry e : list.getEntries())
		{
			if (rows.contains(e.getTaskRow()) && e.isOpen() && e.isRated()) return e;
		}
		return null;
	}

	private static Valued general(Model m, SlayerReward r)
	{
		final Context c = m.c;
		final SlayerGoal goal = m.goal();
		switch (r)
		{
			case BIGGER_AND_BADDER:
			{
				if (goal == SlayerGoal.POINTS)
				{
					return new Valued(0, "Superiors add time to a task without making it count for more.", null);
				}
				final double yield = goal == SlayerGoal.XP ? SUPERIOR_XP : SUPERIOR_LOOT;
				return new Valued(m.effect(m.pool, e -> superior(c, e) ? SUPERIOR_TIME : 1, e -> superior(c, e) ? yield : 1,
						e -> SlayerGoalModel.OVERHEAD_MINUTES),
						goal == SlayerGoal.XP ? "Superior monsters spawn on task and give a lot of Slayer XP."
								: "Superior monsters spawn on task, roll the drop table three times and can drop the imbued heart.", null);
			}
			case MALEVOLENT_MASQUERADE:
				return new Valued(m.effect(m.pool, e -> helmetTask(e) ? HELMET_TIME : 1, e -> 1, e -> SlayerGoalModel.OVERHEAD_MINUTES),
						"The Slayer helmet keeps the black mask's boost on tasks that need a nose peg, earmuffs, facemask or spiny helmet.", null);
			case RING_BLING:
				return new Valued(m.effect(m.pool, e -> 1, e -> 1,
						e -> ringTask(c, e) ? SlayerGoalModel.OVERHEAD_MINUTES - RING_MINUTES_SAVED : SlayerGoalModel.OVERHEAD_MINUTES),
						"Make Slayer rings, which teleport to the Slayer Tower, the Stronghold Slayer Cave and other task areas.", null);
			case TASK_STORAGE:
				// Wiki Slayer training: "Purchasing Task Storage is up to the player's preference"
				return new Valued(0, "It splits up long tasks, which the wiki leaves to preference.", null);
			case DOUBLE_TROUBLE:
				return new Valued(0, "It only helps if you kill the Grotesque Guardians on gargoyle tasks.", null);
			case BROADER_FLETCHING:
				return new Valued(0, "It is for Fletching. Broad bolts are sold for points and leaf-bladed weapons work too.", null);
			case DULY_NOTED:
				return new Valued(0, "Noted mithril bars only save space for ironmen.", null);
			default:
				return NONE;
		}
	}

	// ---- blocks ----

	/** The block advice's blocks at the plan's master and, on the routine, at the filler master. */
	private static List<Candidate> blocks(Model m)
	{
		final List<Candidate> out = new ArrayList<>();
		final Context c = m.c;
		if (c.getPool() == null) return out;
		final Routine r = c.getRoutine();
		final SlayerPool fillersToday = r == null ? null : r.getPool();
		out.addAll(blocks(m, SlayerMaster.forAssigner(c.getMaster()), c.getBlocks(), c.getPool(), c.getEmptySlots(), c.getNpc(),
				fillersToday == null ? null : "Milestone tasks get quicker on average.", list -> m.rate(list, fillersToday)));
		if (fillersToday != null)
		{
			out.addAll(blocks(m, SlayerMaster.TURAEL, r.getBlocks(), fillersToday, r.getEmptySlots(), r.getNpc(),
					"Filler tasks get quicker on average.", list -> m.rate(c.getPool(), list)));
		}
		return out;
	}

	/**
	 * One list's blocks in its advice's order, each valued on top of the blocks it keeps and the ones before it.
	 *
	 * @param effect what the blocks do on the routine, or null
	 * @param rate the goal's rate with this list as given and the other list as it is today
	 */
	private static List<Candidate> blocks(Model m, SlayerMaster list, SlayerBlockAdvisor.Advice advice, SlayerPool pool, int emptySlots,
			String npc, String effect, ToDoubleFunction<SlayerPool> rate)
	{
		final List<Candidate> out = new ArrayList<>();
		if (advice == null) return out;
		final List<Integer> blocked = new ArrayList<>(advice.getKeepRows());
		double previous = rate.applyAsDouble(pool.withBlocks(blocked));
		for (int i = 0; i < advice.getAddRows().size(); i++)
		{
			final SlayerPool.Entry e = pool.find(advice.getAddRows().get(i));
			blocked.add(e.getTaskRow());
			final double next = rate.applyAsDouble(pool.withBlocks(blocked));
			final int misfit = i - Math.max(0, emptySlots);
			final String note = misfit >= 0 && misfit < advice.getMisfits().size()
					? "Unblock " + advice.getMisfits().get(misfit).getName() + " first to free its slot." : null;
			out.add(new Candidate(Type.BLOCK, null, blockName(e, npc, m.goal()), advice.getCost(), false, false,
					m.base <= 0 ? 0 : (next - previous) / m.base, blockWhy(e, npc, m.goal(), effect), null, note, list));
			previous = next;
		}
		return out;
	}

	/** "Block Kurask", or for Slayer points, whose plan can hold blocks at two masters, "Block Kurask at Konar". */
	static String blockName(SlayerPool.Entry e, String npc, SlayerGoal goal)
	{
		return "Block " + e.getName() + (goal == SlayerGoal.POINTS ? " at " + npc : "");
	}

	/**
	 * Why a block helps. For Slayer points a block pays in time, and the block advice only blocks a task that takes
	 * longer than the average task left on the list, however quick its rating.
	 */
	static String blockWhy(SlayerPool.Entry e, String npc, SlayerGoal goal, String effect)
	{
		if (goal != SlayerGoal.POINTS) return "Stops " + npc + " giving " + e.getName() + ", " + judge(e, goal) + ".";
		final String why = e.getName() + " take longer than the average task at " + npc + ".";
		return effect == null ? why : why + " " + effect;
	}

	/**
	 * What blocking {@code row} on the filler list does for the routine's rate, as a share of today's: on top of the
	 * blocks {@code kept} there, with the milestone master's list as it is.
	 */
	static double fillerBlockGain(Routine r, SlayerPool milestone, List<Integer> kept, int row)
	{
		if (r == null || r.getPool() == null || milestone == null) return 0;
		final double base = SlayerGoalModel.cycleRate(r.getPool(), milestone, r.getFillers(), r.getPoints());
		if (base <= 0) return 0;
		final List<Integer> with = new ArrayList<>(kept);
		with.add(row);
		return (SlayerGoalModel.cycleRate(r.getPool().withBlocks(with), milestone, r.getFillers(), r.getPoints())
				- SlayerGoalModel.cycleRate(r.getPool().withBlocks(kept), milestone, r.getFillers(), r.getPoints())) / base;
	}

	// ---- the Buy tab ----

	private static Candidate item(Model m, SlayerShopItem item, Integer cost)
	{
		final Context c = m.c;
		final boolean owned = c.getItems().owns(item);
		final int price = cost == null ? item.getFallbackCost() : cost;
		final String lockedBy = item.getSkill() != null && c.getPlayer().level(item.getSkill().ordinal()) < item.getLevel()
				? item.getLevel() + " " + item.getSkill().getName() : null;
		final String seen = owned ? null : c.getItems().isBankSeen()
				? "Could not tell if you have one. None has been seen in your bank, inventory or worn items."
				: "Could not tell if you have one. Open your bank once so the plan can look.";
		final SlayerGoal goal = m.goal();
		double gain = 0;
		String why = null;
		if (m.pool != null)
		{
			switch (item)
			{
				case HERB_SACK:
					if (goal == SlayerGoal.PROFIT)
					{
						gain = m.effect(m.pool, e -> 1, e -> herbTask(c, e) ? HERB_SACK_LOOT : 1, e -> SlayerGoalModel.OVERHEAD_MINUTES);
						why = "Holds the herbs that " + example(m, e -> herbTask(c, e)) + " drop.";
					}
					break;
				case RUNE_POUCH:
					gain = m.effect(m.pool, e -> barrageTask(c, e) ? RUNE_POUCH_TIME : 1, e -> 1, e -> SlayerGoalModel.OVERHEAD_MINUTES);
					why = "Frees inventory space when you barrage " + example(m, e -> barrageTask(c, e)) + ".";
					break;
				case LOOTING_BAG:
					if (goal == SlayerGoal.PROFIT && c.getMaster() == SlayerAssigner.KRYSTILIA)
					{
						gain = m.effect(m.pool, e -> 1, e -> LOOTING_BAG_LOOT, e -> SlayerGoalModel.OVERHEAD_MINUTES);
						why = "Carries more loot on " + possessive(c.getNpc()) + " Wilderness tasks.";
					}
					break;
				default:
					break;
			}
		}
		return new Candidate(Type.ITEM, null, item.getFallbackName(), price, owned, false, m.pool == null ? Double.NaN : gain,
				sentence(why, item.getFact()), lockedBy, seen);
	}

	// ---- the goal model ----

	/**
	 * The extension of the task in progress at its master, as a share of the goal's rate there, valued as the plan
	 * values extensions: against the rate the skip model leaves, and never above 0 for a task an extension cannot
	 * be worth. 0 when it has none.
	 *
	 * @param skipBudget the most of the master's offers the skip model skips, 0 when the goal skips nothing
	 */
	static double extensionGain(SlayerPool pool, SlayerGameData data, int taskRow, SlayerGoal goal, double skipBudget)
	{
		final SlayerGameData.Extension x = data == null ? null : data.extensionOf(taskRow);
		final SlayerPool.Entry entry = pool == null ? null : pool.find(taskRow);
		if (x == null || entry == null || !entry.isOpen() || entry.isBoss()) return 0;
		final SlayerPool after = pool.map(e -> e.getTaskRow() == taskRow
				? e.withAmounts(x.extendedMin(e.getMinAmount()), x.extendedMax(e.getMaxAmount())) : e);
		double gain = change(pool, after, goal);
		if (skipBudget > 0)
		{
			final double before = withSkips(pool, goal, pool.getMaster(), skipBudget);
			gain = before <= 0 ? 0 : (withSkips(after, goal, pool.getMaster(), skipBudget) - before) / before;
		}
		return extendable(entry, goal) ? gain : Math.min(0, gain);
	}

	/** The share by which the goal's rate rises from {@code before} to {@code after}. */
	static double change(SlayerPool before, SlayerPool after, SlayerGoal goal)
	{
		final double base = SlayerGoalModel.rate(before, goal);
		return base <= 0 ? 0 : (SlayerGoalModel.rate(after, goal) - base) / base;
	}

	/** A list to judge a task reward against: as it is, or with the task opened when it only waits for a level. */
	private static final class Waiting
	{
		final SlayerPool pool;
		final SlayerPool.Entry entry;
		final String lockedBy;

		Waiting(SlayerPool pool, SlayerPool.Entry entry, String lockedBy)
		{
			this.pool = pool;
			this.entry = entry;
			this.lockedBy = lockedBy;
		}
	}

	/**
	 * The list to value a reward for these tasks against, or null when the master never gives them, they are
	 * blocked or advised for a block, or they need an unlock or are removed. A task waiting for a level is opened,
	 * with the level named.
	 */
	private static Waiting waiting(Model m, Set<Integer> rows)
	{
		SlayerPool.Entry open = null;
		SlayerPool.Entry level = null;
		for (SlayerPool.Entry e : m.pool.getEntries())
		{
			if (!rows.contains(e.getTaskRow()) || !e.isRated()) continue;
			if (e.isOpen() && open == null) open = e;
			if (e.getStatus() == SlayerPool.Status.NEEDS_LEVEL && level == null) level = e;
		}
		if (open != null) return new Waiting(m.pool, open, null);
		if (level == null) return null;
		final SlayerPool opened = open(m.pool, rows);
		return new Waiting(opened, opened.find(level.getTaskRow()), missingLevel(m.c, level.getTaskRow()));
	}

	private static SlayerPool open(SlayerPool pool, Set<Integer> rows)
	{
		return pool.map(e -> rows.contains(e.getTaskRow()) && e.getStatus() == SlayerPool.Status.NEEDS_LEVEL
				? e.withStatus(SlayerPool.Status.OPEN) : e);
	}

	/** The cache rows of a reward's task, by its related tasks and its name. */
	private static Set<Integer> taskRows(Context c, SlayerReward r)
	{
		final Set<Integer> rows = new HashSet<>();
		final SlayerGameData.Unlock unlock = c.getData().unlock(r.getRow());
		if (unlock != null) rows.addAll(unlock.getRelatedTaskRows());
		if (r.getTask() != null) rows.addAll(c.getData().taskRowsNamed(r.getTask()));
		return rows;
	}

	/** The first level the player lacks for a task, e.g. "87 Slayer" or "combat level 80", or null. */
	static String missingLevel(Context c, int row)
	{
		return missingLevel(c.getData(), c.getPlayer(), c.getMaster(), row);
	}

	static String missingLevel(SlayerGameData data, SlayerPool.Player player, SlayerAssigner master, int row)
	{
		for (SlayerGameData.StatLevel s : data.statsAll(row))
		{
			if (player.level(s.getStat()) < s.getLevel()) return s.getLevel() + " " + skill(s.getStat());
		}
		final List<SlayerGameData.StatLevel> any = data.statsAny(row);
		boolean met = any.isEmpty();
		for (SlayerGameData.StatLevel s : any) met |= player.level(s.getStat()) >= s.getLevel();
		if (!met)
		{
			final List<String> options = new ArrayList<>();
			for (SlayerGameData.StatLevel s : any) options.add(s.getLevel() + " " + skill(s.getStat()));
			return String.join(" or ", options);
		}
		final boolean combatChecked = !player.isCombatChecksOff() && master != SlayerAssigner.KRYSTILIA;
		if (combatChecked && player.getCombatLevel() < data.combatLevel(row)) return "combat level " + data.combatLevel(row);
		return null;
	}

	private static String skill(int stat)
	{
		return stat >= 0 && stat < Skill.values().length ? Skill.values()[stat].getName() : "stat " + stat;
	}

	// ---- which tasks an effect touches ----

	private static boolean superior(Context c, SlayerPool.Entry e)
	{
		final SlayerTask task = c.getDossier().apply(e.getName());
		return task != null && task.isHasSuperior();
	}

	/** Tasks whose gear needs a Slayer helmet can stand in for, from the gear check's own table. */
	static boolean helmetTask(SlayerPool.Entry e)
	{
		return SlayerRequirements.helmetStandsIn(e.getName());
	}

	private static boolean ringTask(Context c, SlayerPool.Entry e)
	{
		final SlayerTask task = c.getDossier().apply(e.getName());
		final String where = task == null || task.getBestLocation() == null ? "" : task.getBestLocation().toLowerCase(Locale.ROOT);
		for (String place : RING_PLACES)
		{
			if (where.contains(place)) return true;
		}
		return false;
	}

	private static boolean herbTask(Context c, SlayerPool.Entry e)
	{
		final SlayerTask task = c.getDossier().apply(e.getName());
		return task != null && task.getNotableDrops() != null && task.getNotableDrops().toLowerCase(Locale.ROOT).contains("herb");
	}

	private static boolean barrageTask(Context c, SlayerPool.Entry e)
	{
		final SlayerTask task = c.getDossier().apply(e.getName());
		final String style = task == null || task.getKillStyle() == null ? "" : task.getKillStyle().toLowerCase(Locale.ROOT);
		return style.contains("barrage") || style.contains("burst");
	}

	/** "Aberrant Spectres and Nechryael", the two commonest open tasks that match, or "some tasks". */
	private static String example(Model m, Predicate<SlayerPool.Entry> match)
	{
		final List<SlayerPool.Entry> found = new ArrayList<>();
		for (SlayerPool.Entry e : m.pool.open())
		{
			if (e.isRated() && match.test(e)) found.add(e);
		}
		found.sort((a, b) -> a.getWeight() != b.getWeight() ? b.getWeight() - a.getWeight() : a.getName().compareTo(b.getName()));
		if (found.isEmpty()) return "some tasks";
		return found.size() == 1 ? found.get(0).getName() : found.get(0).getName() + " and " + found.get(1).getName();
	}

	// ---- words ----

	/**
	 * "a good XP task", "a poor money task", or for Slayer points how long it takes, "a slow task". For XP a task the
	 * wiki's Slayer guide gives advice on is described by that advice: its rating only says how fast the XP comes.
	 */
	static String judge(SlayerPool.Entry e, SlayerGoal goal)
	{
		if (!e.isRated()) return "a task not rated yet";
		final TaskRating.XpAdvice advice = e.wikiAdvice(goal);
		if (advice != null) return "a task " + wikiClause(advice);
		final int rating = e.rating(goal);
		switch (goal)
		{
			case XP:
				return article(quality(rating)) + " XP task";
			case PROFIT:
				return article(quality(rating)) + " money task";
			default:
				return speed(rating);
		}
	}

	/** "the wiki's Slayer guide says to skip or block for XP", to follow "a task". */
	private static String wikiClause(TaskRating.XpAdvice advice)
	{
		switch (advice)
		{
			case DO:
				return "the wiki's Slayer guide says to do for XP";
			case SKIP:
				return "the wiki's Slayer guide says to skip or block for XP";
			default:
				return "the wiki's Slayer guide says not to unlock for XP";
		}
	}

	/** The wiki's advice on a task for XP as a sentence, e.g. "The wiki's Slayer guide says to skip or block it for XP." */
	static String wikiSays(TaskRating.XpAdvice advice)
	{
		switch (advice)
		{
			case DO:
				return "The wiki's Slayer guide says to do it for XP.";
			case SKIP:
				return "The wiki's Slayer guide says to skip or block it for XP.";
			default:
				return "The wiki's Slayer guide says not to unlock it for XP.";
		}
	}

	/** Ratings as words, never as numbers. */
	static String quality(int rating)
	{
		switch (rating)
		{
			case 5:
				return "excellent";
			case 4:
				return "good";
			case 3:
				return "fair";
			case 2:
				return "poor";
			default:
				return "very poor";
		}
	}

	/** A speed rating as words. */
	static String speed(int rating)
	{
		switch (rating)
		{
			case 5:
				return "a very quick task";
			case 4:
				return "a quick task";
			case 3:
				return "a task of average length";
			case 2:
				return "a slow task";
			default:
				return "a very slow task";
		}
	}

	private static String article(String word)
	{
		return ("aeiou".indexOf(word.charAt(0)) >= 0 ? "an " : "a ") + word;
	}

	static String possessive(String npc)
	{
		return npc.endsWith("s") ? npc + "'" : npc + "'s";
	}

	private static String name(Context c, Set<Integer> rows)
	{
		final List<String> names = new ArrayList<>();
		for (SlayerPool.Entry e : c.getPool().getEntries())
		{
			if (rows.contains(e.getTaskRow()) && !names.contains(e.getName())) names.add(e.getName());
		}
		if (names.isEmpty())
		{
			for (int row : rows)
			{
				final String n = c.getData().taskName(row);
				if (n != null && !names.contains(n)) names.add(n);
			}
		}
		return names.isEmpty() ? "the task" : join(names);
	}

	private static String names(Collection<SlayerPool.Entry> entries)
	{
		final List<String> names = new ArrayList<>();
		for (SlayerPool.Entry e : entries) names.add(e.getName());
		return join(names);
	}

	/** "A", "A and B", "A, B and C". */
	static String join(List<String> parts)
	{
		if (parts.size() <= 1) return parts.isEmpty() ? "" : parts.get(0);
		return String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.get(parts.size() - 1);
	}

	/** The reason and the reward's own fact as sentences, or null when there is neither. */
	private static String sentence(String why, String fact)
	{
		if (why == null) return fact == null ? null : fact + ".";
		return fact == null ? why : why + " " + fact + ".";
	}
}
