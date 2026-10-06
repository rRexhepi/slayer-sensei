package com.slayercompanion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Which tasks to block at one master for a goal, starting from the block list the player has. Pure.
 * <p>
 * A block is judged by how much it raises the goal's rate in {@link SlayerGoalModel}: tasks the player can
 * be assigned, weighed by how often they come up, how long they take and how poor they are for the goal (the
 * xp rating for XP, gp for profit, speed for Slayer points). The wiki gives the same rule: "The undesirable
 * tasks with the highest weighting ... should be blocked" (Slayer training).
 * <p>
 * Blocks cost points and unblocking refunds nothing (wiki Slayer Rewards), so a current block that helps the
 * goal stays even when another task would help a little more. For XP a task the wiki's Slayer guide says to do is
 * never blocked, and a block on one does not fit. Free slots are filled with the best blocks on
 * top of the kept ones. With every slot full, one swap is suggested when the best task left would help at
 * least {@link #SWAP_FACTOR} times as much as the weakest kept block.
 */
final class SlayerBlockAdvisor
{
	/** How much more a waiting block must help than the weakest current one before a swap is worth its points. */
	static final double SWAP_FACTOR = 2;
	private static final double EPSILON = 1e-9;

	/** A current block that does not fit the goal. */
	@Value
	static class Misfit
	{
		String name;
		/** Why, e.g. "good for XP". */
		String why;
	}

	@Value
	@AllArgsConstructor
	static class Advice
	{
		SlayerGoal goal;
		/** Points per block at this master. */
		int cost;
		/** Block slots the player can use at this master. */
		int slots;
		/** Current blocks that help the goal, in slot order. */
		List<String> keep;
		/** Tasks to block, best first: one per free slot, plus one per misfit to replace. */
		List<String> add;
		/** Current blocks that do not fit the goal, to unblock. */
		List<Misfit> misfits;
		/** The task rows of {@link #keep} and {@link #add}, so the points plan can value each block. */
		List<Integer> keepRows;
		List<Integer> addRows;

		/** Advice for the sidebar text alone, without task rows. */
		Advice(SlayerGoal goal, int cost, int slots, List<String> keep, List<String> add, List<Misfit> misfits)
		{
			this(goal, cost, slots, keep, add, misfits, Collections.<Integer>emptyList(), Collections.<Integer>emptyList());
		}
	}

	private SlayerBlockAdvisor()
	{
	}

	/**
	 * @param pool the master's pool with the player's current blocks
	 * @param slots block slots the player can use at the master
	 * @param blockedRows task rows on the master's block list, in slot order
	 */
	static Advice advise(SlayerMaster master, SlayerPool pool, SlayerGoal goal, int slots, List<Integer> blockedRows)
	{
		final List<Integer> kept = new ArrayList<>();
		final Map<Integer, Misfit> lifted = new HashMap<>();
		for (int row : blockedRows)
		{
			final SlayerPool.Entry e = pool.find(row);
			if (e == null || !e.isAssignable())
			{
				lifted.put(row, new Misfit(e == null ? "Task row " + row : e.getName(), "you cannot be given it anyway"));
			}
			else if (!e.isRated())
			{
				lifted.put(row, new Misfit(e.getName(), "not rated yet"));
			}
			else if (e.wikiSaysDo(goal))
			{
				lifted.put(row, new Misfit(e.getName(), "the wiki's Slayer guide says to do it for XP"));
			}
			else
			{
				kept.add(row);
			}
		}
		// A current block fits when lifting it, the others kept, would lower the rate. Blocks help each other, so each is
		// judged on top of the rest, and the one whose lifting raises the rate most goes first until every block left fits.
		while (!kept.isEmpty())
		{
			double best = SlayerGoalModel.rate(pool.withBlocks(kept), goal);
			Integer worst = null;
			for (Integer row : kept)
			{
				final List<Integer> without = new ArrayList<>(kept);
				without.remove(row);
				final double r = SlayerGoalModel.rate(pool.withBlocks(without), goal);
				if (r > best + EPSILON)
				{
					best = r;
					worst = row;
				}
			}
			if (worst == null) break;
			kept.remove(worst);
			lifted.put(worst, new Misfit(pool.find(worst).getName(), misfit(pool.find(worst), goal)));
		}
		final List<Misfit> misfits = new ArrayList<>();
		for (int row : blockedRows)
		{
			if (lifted.containsKey(row)) misfits.add(lifted.remove(row));
		}

		final int free = Math.max(0, slots - kept.size());
		List<SlayerPool.Entry> add = SlayerGoalModel.bestBlocks(pool, goal, kept, free);
		if (free == 0 && !kept.isEmpty())
		{
			// Every slot holds a useful block. Suggest one swap when a far better block is waiting.
			final List<SlayerPool.Entry> next = SlayerGoalModel.bestBlocks(pool, goal, kept, 1);
			final int weakest = weakest(pool, goal, kept);
			if (!next.isEmpty() && gain(pool, goal, kept, next.get(0).getTaskRow()) > SWAP_FACTOR * contribution(pool, goal, kept, weakest))
			{
				kept.remove(Integer.valueOf(weakest));
				misfits.add(new Misfit(pool.find(weakest).getName(), "a weaker block than " + next.get(0).getName()));
				add = next;
			}
		}

		final List<String> keep = new ArrayList<>();
		for (int row : kept)
		{
			keep.add(pool.find(row).getName());
		}
		final List<String> addNames = new ArrayList<>();
		final List<Integer> addRows = new ArrayList<>();
		for (SlayerPool.Entry e : add)
		{
			addNames.add(e.getName());
			addRows.add(e.getTaskRow());
		}
		return new Advice(goal, SlayerAssigner.blockCost(master.getId()), slots, Collections.unmodifiableList(keep),
				Collections.unmodifiableList(addNames), Collections.unmodifiableList(misfits),
				Collections.unmodifiableList(new ArrayList<>(kept)), Collections.unmodifiableList(addRows));
	}

	/** The kept block that helps least, given the others. */
	private static int weakest(SlayerPool pool, SlayerGoal goal, List<Integer> kept)
	{
		int weakest = kept.get(0);
		double least = Double.MAX_VALUE;
		for (int row : kept)
		{
			final double c = contribution(pool, goal, kept, row);
			if (c < least)
			{
				least = c;
				weakest = row;
			}
		}
		return weakest;
	}

	/** How much the rate drops when {@code row} is unblocked and the other kept blocks stay. */
	private static double contribution(SlayerPool pool, SlayerGoal goal, List<Integer> kept, int row)
	{
		final List<Integer> without = new ArrayList<>(kept);
		without.remove(Integer.valueOf(row));
		return SlayerGoalModel.rate(pool.withBlocks(kept), goal) - SlayerGoalModel.rate(pool.withBlocks(without), goal);
	}

	/** How much the rate rises when {@code row} is blocked on top of the kept blocks. */
	private static double gain(SlayerPool pool, SlayerGoal goal, List<Integer> kept, int row)
	{
		final List<Integer> with = new ArrayList<>(kept);
		with.add(row);
		return SlayerGoalModel.rate(pool.withBlocks(with), goal) - SlayerGoalModel.rate(pool.withBlocks(kept), goal);
	}

	/**
	 * Why a current block does not fit: the task is good for the goal, or, for a task rated lower, a block on it
	 * lowers the rate all the same. A task rated below good is never called good.
	 */
	private static String misfit(SlayerPool.Entry e, SlayerGoal goal)
	{
		switch (goal)
		{
			case XP:
				return e.rating(goal) >= SlayerSpend.GOOD ? "good for XP" : "a block on it lowers your XP rate";
			case PROFIT:
				return e.rating(goal) >= SlayerSpend.GOOD ? "good for profit" : "a block on it lowers your profit rate";
			default:
				return "quick enough";
		}
	}
}
