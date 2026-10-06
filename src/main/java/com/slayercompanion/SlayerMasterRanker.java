package com.slayercompanion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import lombok.Value;

/**
 * Ranks the masters a player can use for the XP and profit goals. Pure.
 * <p>
 * A master is judged by his pool as the player gets it ({@link SlayerPool}), with the player's block slots
 * filled by the blocks that help the goal most ({@link SlayerGoalModel#bestBlocks}), so a block list not
 * built yet does not count against him. On top of the pool come these master-level effects, each from the
 * OSRS Wiki, read 4 October 2026:
 * <ul>
 * <li>Skips. "Use Slayer reward points or Turael to skip undesirable tasks as often as possible", and "use
 * the cancel option as often as it can be used while maintaining a stable level of Slayer reward points"
 * (Slayer training). A master's points per task, milestones averaged in, pay for skipping that share of the
 * worst offers at his cancel cost. This is what Duradel's 15 points a task are worth.</li>
 * <li>Mortimer offers a choice of two tasks, three once 50 of his tasks are done, and his tasks pay no
 * points unless a points modifier comes up (Mortimer), so he gets the best of each offer and no skips.</li>
 * <li>Konar picks the location of every task, each eligible area equally likely, and "some places may be
 * less desirable than others" (Konar quo Maten/Strategies, which skips e.g. abyssal demons outside the
 * Catacombs, 2 chances in 3). Her XP ratings lose {@link #KONAR_LOCATION_XP}, a judgment.</li>
 * <li>Brimstone keys drop on Konar's tasks, about 1 in 100 kills of level 100 monsters, more from Slayer
 * monsters and always from superiors, and the chest averages about 87k (Brimstone key). At 100 to 300 kills
 * an hour that is roughly 100k to 300k, so her profit ratings gain {@link #BRIMSTONE_KEYS_GP}.</li>
 * <li>Larran's keys drop on Krystilia's tasks at about the same rate and the big chest averages about 118k
 * (Larran's key, Larran's big chest). Her profit ratings gain {@link #LARRANS_KEYS_GP}. Her tasks use the
 * ratings' Wilderness version, see {@link SlayerPool}.</li>
 * </ul>
 * With the cache and ratings of 4 October 2026 a maxed account gets Kuradal first for XP, as the Slayer
 * training guide has it ("Duradel is the best Slayer master to use for the fastest experience, but some
 * players may find that Nieve's (or Steve's) task weighting better suit their preferences"), then Steve.
 * Mortimer passes Steve once he offers three tasks, in line with his strategy page ("Players may find more
 * success training at Mortimer if they don't have a lot of slayer points for skipping or a block list fully
 * built out"). For profit Mortimer's choice comes first, then Konar with her keys, then Kuradal.
 * SlayerMasterRankerTest keeps all of this.
 */
final class SlayerMasterRanker
{
	static final double KONAR_LOCATION_XP = -0.4;
	static final double BRIMSTONE_KEYS_GP = 0.3;
	static final double LARRANS_KEYS_GP = 0.3;
	/** Mortimer offers a third task once this many of his tasks are done (wiki Mortimer). */
	static final int MORTIMER_THIRD_CHOICE = 50;
	/** A task rated this or better counts as good for the goal in the reasons. */
	static final int GOOD = 4;
	/** Average milestone multiplier over a long streak, from {@link SlayerPayout#multiplier}. */
	static final double AVERAGE_MULTIPLIER = averageMultiplier();

	@Value
	static class Ranked
	{
		SlayerAssigner master;
		/** The NPC the player finds at the master's spot. */
		String npc;
		/** What the masters are ordered by. Internal, never shown. */
		double score;
		/**
		 * Chance a task is good for the goal (rated {@link #GOOD} or better) once the assumed blocks are in. For
		 * Mortimer, among the tasks the player picks.
		 */
		double goodShare;
		/** The blocks the ranking assumed at this master, best first. */
		List<String> blocks;
		/** Short reason for the sidebar. */
		String reason;
		/** What the player still needs to use the master, e.g. "combat level 100", or null when usable. */
		String missing;
	}

	private SlayerMasterRanker()
	{
	}

	/**
	 * Usable masters for the goal, best first. Empty for the Slayer points goal, which has its routine.
	 *
	 * @param pools a master's pool as the player gets it, null when unknown
	 * @param slots block slots the player can use at a master
	 * @param mortimerTasks SLAYER_MORTIMER_TASKS_COMPLETED
	 */
	static List<Ranked> rank(SlayerGoal goal, List<SlayerMasterAccess.Option> options, Function<SlayerAssigner, SlayerPool> pools,
			ToIntFunction<SlayerAssigner> slots, SlayerPayout.Diaries diaries, int mortimerTasks)
	{
		return judgeAll(goal, options, true, pools, slots, diaries, mortimerTasks);
	}

	/**
	 * The best master the player cannot use yet, when he beats every master in {@code ranking}, or null. He is
	 * judged on the tasks the player could be given today, so the hint errs on the side of silence.
	 */
	static Ranked later(SlayerGoal goal, List<SlayerMasterAccess.Option> options, Function<SlayerAssigner, SlayerPool> pools,
			ToIntFunction<SlayerAssigner> slots, SlayerPayout.Diaries diaries, int mortimerTasks, List<Ranked> ranking)
	{
		final List<Ranked> locked = judgeAll(goal, options, false, pools, slots, diaries, mortimerTasks);
		if (locked.isEmpty()) return null;
		final Ranked best = locked.get(0);
		return ranking.isEmpty() || best.getScore() > ranking.get(0).getScore() ? best : null;
	}

	private static List<Ranked> judgeAll(SlayerGoal goal, List<SlayerMasterAccess.Option> options, boolean usable,
			Function<SlayerAssigner, SlayerPool> pools, ToIntFunction<SlayerAssigner> slots, SlayerPayout.Diaries diaries,
			int mortimerTasks)
	{
		if (goal == SlayerGoal.POINTS) return Collections.emptyList();
		final List<Ranked> out = new ArrayList<>();
		for (SlayerMasterAccess.Option o : options)
		{
			if (o.isUsable() != usable) continue;
			final SlayerPool pool = pools.apply(o.getMaster());
			if (pool == null || SlayerGoalModel.offers(pool).isEmpty()) continue;
			out.add(judge(goal, o, pool, slots.applyAsInt(o.getMaster()), diaries, mortimerTasks));
		}
		out.sort(Comparator.comparingDouble(Ranked::getScore).reversed()
				.thenComparingInt(r -> r.getMaster().ordinal()));
		return Collections.unmodifiableList(out);
	}

	private static Ranked judge(SlayerGoal goal, SlayerMasterAccess.Option option, SlayerPool pool, int slots,
			SlayerPayout.Diaries diaries, int mortimerTasks)
	{
		final SlayerAssigner master = option.getMaster();
		final List<SlayerPool.Entry> blocks = SlayerGoalModel.bestBlocks(pool, goal, slots);
		final List<Integer> rows = new ArrayList<>();
		final List<String> names = new ArrayList<>();
		for (SlayerPool.Entry e : blocks)
		{
			rows.add(e.getTaskRow());
			names.add(e.getName());
		}
		final SlayerPool blocked = pool.withBlocks(rows);
		final List<SlayerGoalModel.Offer> offers = SlayerGoalModel.offers(blocked);
		final double bonus = bonus(master, goal);
		final int choices = choices(master, mortimerTasks);
		final double score;
		final double good;
		if (choices > 1)
		{
			// What counts is the task the player picks from each offer
			final List<SlayerGoalModel.Offer> picked = SlayerGoalModel.bestOf(offers, goal, choices);
			score = SlayerGoalModel.rate(picked, goal, bonus, 0);
			good = SlayerGoalModel.share(picked, goal, GOOD);
		}
		else
		{
			score = SlayerGoalModel.rateWithSkips(offers, goal, bonus, skipBudget(master, diaries));
			good = SlayerGoalModel.share(offers, goal, GOOD);
		}
		return new Ranked(master, option.getNpc(), score, good, Collections.unmodifiableList(names),
				reason(master, option.getNpc(), goal, good, !blocks.isEmpty(), choices), option.getMissing());
	}

	/** What the master adds to every task's rating for the goal. */
	static double bonus(SlayerAssigner master, SlayerGoal goal)
	{
		if (master == SlayerAssigner.KONAR) return goal == SlayerGoal.XP ? KONAR_LOCATION_XP : BRIMSTONE_KEYS_GP;
		if (master == SlayerAssigner.KRYSTILIA && goal == SlayerGoal.PROFIT) return LARRANS_KEYS_GP;
		return 0;
	}

	/** How many tasks each assignment offers: 1, or Mortimer's 2 or 3. */
	static int choices(SlayerAssigner master, int mortimerTasks)
	{
		if (master != SlayerAssigner.MORTIMER) return 1;
		return mortimerTasks >= MORTIMER_THIRD_CHOICE ? 3 : 2;
	}

	/** Share of offers the master's own points pay to skip, at his cancel cost. 0 for masters who pay no points. */
	static double skipBudget(SlayerAssigner master, SlayerPayout.Diaries diaries)
	{
		final double points = SlayerPayout.basePoints(master, diaries) * AVERAGE_MULTIPLIER;
		return points <= 0 ? 0 : points / (master.getCancelCost() + points);
	}

	private static double averageMultiplier()
	{
		// Every milestone interval divides 1,000, so one run of 1,000 tasks is the long-run average
		int sum = 0;
		for (int task = 1; task <= 1000; task++)
		{
			sum += SlayerPayout.multiplier(task);
		}
		return sum / 1000.0;
	}

	/** "About 2 in 5 tasks are good XP tasks once you block the worst." plus the master's own effect. */
	static String reason(SlayerAssigner master, String npc, SlayerGoal goal, double goodShare, boolean blocks, int choices)
	{
		final StringBuilder sb = new StringBuilder();
		if (choices > 1) sb.append("You choose from ").append(choices).append(" tasks each time. ");
		if (master == SlayerAssigner.KONAR && goal == SlayerGoal.PROFIT) sb.append("Brimstone keys add to every task. ");
		if (master == SlayerAssigner.KRYSTILIA && goal == SlayerGoal.PROFIT) sb.append("Larran's keys add to every task. ");
		sb.append(share(goodShare)).append(choices > 1 ? " of the tasks you pick are good " : " tasks are good ")
				.append(goal == SlayerGoal.XP ? "XP" : "money").append(" tasks")
				.append(blocks ? " once you block the worst." : ".");
		if (master == SlayerAssigner.MORTIMER) sb.append(' ').append(npc).append("'s tasks pay no Slayer points.");
		if (master == SlayerAssigner.KRYSTILIA) sb.append(" All of it is in the Wilderness.");
		return sb.toString();
	}

	/** A share as words, so a rough rating never reads as a precise number. */
	static String share(double share)
	{
		final int tenths = (int) Math.round(share * 10);
		if (tenths <= 0) return "Hardly any";
		if (tenths >= 10) return "Nearly all";
		if (tenths == 5) return "About half";
		return "About " + (tenths % 2 == 0 ? tenths / 2 + " in 5" : tenths + " in 10");
	}
}
