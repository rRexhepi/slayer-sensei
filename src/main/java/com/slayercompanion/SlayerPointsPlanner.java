package com.slayercompanion;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Turns valued purchases ({@link SlayerSpend}) into an ordered spend plan. Pure: no client access.
 * <p>
 * Purchases are ranked by what they do for the goal per point: the share by which they raise the goal's rate
 * at the plan's master, divided by their cost. On the Slayer points routine that rate covers the filler master
 * too, so blocks at both masters share the one list. Points above the reserve go down that list. Whatever does not fit
 * is saved for, the ones waiting for a level are listed apart, and the ones that lower the rate are named so they
 * are not bought by mistake.
 * <p>
 * For XP the wiki gives "generally an optimal order" (Slayer training, Using Slayer reward points): blocks, the
 * Fossil Island Wyvern block among them, then superior monsters, the Slayer helmet, extensions and extra unlocks such
 * as Slayer rings. The XP plan follows that order in tiers ({@link #xpTier}), ranks by the goal model within each, and saves
 * for the next purchase rather than buying past it, so the save-for list carries on in the same order.
 */
final class SlayerPointsPlanner
{
	static final int MAX_SAVE_FOR = 5;
	static final int MAX_LATER = 5;
	static final int MAX_AVOID = 5;
	/** A change in the goal's rate smaller than this share neither helps nor hurts enough to list. */
	static final double NOTICEABLE = 0.002;
	/** The longest compact label. The compact sidebar fits about this many characters on a line. */
	static final int MAX_LABEL = 34;

	enum Type
	{
		/** A reward from the shop's Unlock, Extend or Cosmetics tab. */
		REWARD,
		/** A block at the plan's master, or at the filler master on the Slayer points routine. */
		BLOCK,
		/** An item from the shop's Buy tab. */
		ITEM,
		/** One line that stands for several rewards. Not counted in the shop totals. */
		SUMMARY
	}

	@Value
	@AllArgsConstructor
	static class Candidate
	{
		Type type;
		/** The shop reward, or null for a block, item or summary. */
		SlayerReward reward;
		String name;
		/** Points, or -1 when there is no single cost. */
		int cost;
		boolean owned;
		boolean cosmetic;
		/** Share by which the goal's rate rises, below 0 when it falls. NaN when unknown. On the points routine, the cycle's rate. */
		double gain;
		/** A few words on why it helps or hurts the goal, or null. */
		String why;
		/** Unmet requirement, e.g. "85 Slayer", or null when usable now. */
		String lockedBy;
		/** Said after the reason, e.g. whether an item has been seen, or null. */
		String note;
		/** The block list a block goes on, null for everything else. */
		SlayerMaster list;
		/**
		 * The wiki's Slayer guide says not to buy it for the goal, so it is listed under Skip these whatever its gain, and
		 * never recommended.
		 */
		boolean avoid;

		/** Anything but a block. */
		Candidate(Type type, SlayerReward reward, String name, int cost, boolean owned, boolean cosmetic, double gain, String why,
				String lockedBy, String note)
		{
			this(type, reward, name, cost, owned, cosmetic, gain, why, lockedBy, note, null, false);
		}

		/** A block on {@code list}. */
		Candidate(Type type, SlayerReward reward, String name, int cost, boolean owned, boolean cosmetic, double gain, String why,
				String lockedBy, String note, SlayerMaster list)
		{
			this(type, reward, name, cost, owned, cosmetic, gain, why, lockedBy, note, list, false);
		}

		Candidate withGain(double gain)
		{
			return new Candidate(type, reward, name, cost, owned, cosmetic, gain, why, lockedBy, note, list, avoid);
		}
	}

	@Value
	static class Line
	{
		String name;
		int cost;
		String detail;
		/**
		 * Points still short of buying it, 0 when it is affordable now, -1 when the cost is unknown. In the XP order the
		 * purchases saved for before it count too.
		 */
		int need;
		/** Rough tasks to earn {@link #need}, -1 when there is no estimate. */
		int tasks;
		/** The compact sidebar's item: the name and cost, and for some lines what it waits for. */
		String label;

		static Line of(String name, int cost, String detail)
		{
			return new Line(name, cost, detail, 0, -1, label(name, cost));
		}
	}

	/** "Bigger and Badder (50)", or the name alone when there is no single cost. */
	static String label(String name, int cost)
	{
		return label(name, cost, "");
	}

	/** "Block Waterfiends (100), 8 tasks", or "Helmet (400), 19 to go" with the points when there is no estimate of the tasks. */
	static String saveLabel(String name, int cost, int need, int tasks)
	{
		return label(name, cost, tasks > 0 ? ", " + tasks + (tasks == 1 ? " task" : " tasks") : need > 0 ? ", " + need + " to go" : "");
	}

	/** "Gargoyle Smasher (120), 75 Slayer": the purchase and the level it waits for. */
	static String laterLabel(String name, int cost, String lockedBy)
	{
		return label(name, cost, ", " + lockedBy);
	}

	/**
	 * The compact label: the name, the cost and {@code wait}. When that is too long for a compact line it leaves out a
	 * block's master first, as in "Block Kurask" for "Block Kurask at Konar", then the cost, then the wait.
	 */
	static String label(String name, int cost, String wait)
	{
		final String brief = name.replaceFirst("^(Block .+) at \\S+$", "$1");
		final String price = cost >= 0 ? " (" + cost + ")" : "";
		for (String label : new String[]{name + price + wait, brief + price + wait, brief + wait, brief + price})
		{
			if (label.length() <= MAX_LABEL) return label;
		}
		return brief;
	}

	/**
	 * Points the plan keeps for skips, and why. The master ranking assumes the worst offers are skipped at the
	 * master's cancel cost ({@link SlayerMasterRanker#skipBudget}), so the plan keeps enough for the skips that
	 * come in a row nine times in ten.
	 */
	@Value
	static class Reserve
	{
		static final Reserve NONE = new Reserve(0, 0, 0, 0, Collections.<String>emptyList(), 0);

		/** Points kept. */
		int points;
		/** Skips in a row they pay for at the plan's master. */
		int skips;
		/** Points per skip there. */
		int cancelCost;
		/** Share of the master's tasks worth skipping for the goal. */
		double share;
		/** The tasks worth skipping, worst first, at most two. */
		List<String> tasks;
		/** The least the player keeps, from config. */
		int floor;

		/** Whether the configured floor, not the skips, set the amount. */
		boolean isFloor()
		{
			return floor > skips * cancelCost;
		}
	}

	@Value
	static class Plan
	{
		int points;
		Reserve reserve;
		List<Line> buyNow;
		List<Line> saveFor;
		List<Line> later;
		List<Line> avoid;
		/** Shop rewards bought, of {@link #total}. Blocks and items are not counted. */
		int owned;
		int total;
		int cosmeticLeft;
	}

	private SlayerPointsPlanner()
	{
	}

	static Plan plan(List<Candidate> candidates, int points, Reserve reserve)
	{
		return plan(candidates, points, reserve, null);
	}

	/**
	 * @param tasksFor rough tasks to earn a number of points, -1 when unknown (see {@link SlayerPointsForecast}).
	 * Null leaves the estimate out of the save-for lines.
	 */
	static Plan plan(List<Candidate> candidates, int points, Reserve reserve, IntUnaryOperator tasksFor)
	{
		return plan(candidates, points, reserve, tasksFor, null);
	}

	/**
	 * @param goal XP follows the wiki's tiers ({@link #xpTier}) and buys in order only. Other goals, or null, keep
	 * the goal model's order and buy whatever the points reach.
	 */
	static Plan plan(List<Candidate> candidates, int points, Reserve reserve, IntUnaryOperator tasksFor, SlayerGoal goal)
	{
		final List<Candidate> wanted = new ArrayList<>();
		final List<Candidate> later = new ArrayList<>();
		final List<Candidate> avoid = new ArrayList<>();
		int owned = 0;
		int total = 0;
		int cosmeticLeft = 0;

		for (Candidate c : candidates)
		{
			if (c.getType() == Type.REWARD)
			{
				total++;
				if (c.isOwned()) owned++;
			}
			if (c.isOwned()) continue;
			if (c.isCosmetic())
			{
				cosmeticLeft++;
				continue;
			}
			if (c.isAvoid())
			{
				// What the wiki says not to buy is never recommended, and is named once it can be bought
				if (c.getLockedBy() == null) avoid.add(c);
				continue;
			}
			final double gain = c.getGain();
			if (Double.isNaN(gain)) continue;
			if (gain < -NOTICEABLE)
			{
				// It can only hurt once it can be used
				if (c.getLockedBy() == null) avoid.add(c);
				continue;
			}
			if (gain <= NOTICEABLE) continue;
			(c.getLockedBy() != null ? later : wanted).add(c);
		}

		final boolean inOrder = goal == SlayerGoal.XP;
		wanted.sort(inOrder ? XP_ORDER : BEST_FIRST);
		keepBlockOrder(wanted, candidates);
		later.sort(BEST_FIRST);
		// The wiki's own advice against a purchase comes first, then the goal model's, worst first
		avoid.sort(Comparator.comparing((Candidate c) -> !c.isAvoid()).thenComparingDouble(Candidate::getGain).thenComparing(Candidate::getName));

		final List<Line> buyNow = new ArrayList<>();
		final List<Line> saveFor = new ArrayList<>();
		int budget = Math.max(0, points - reserve.getPoints());
		boolean saving = false;
		// In the XP order a purchase waits for the ones before it, so its shortfall counts them too
		int ahead = 0;
		for (Candidate c : wanted)
		{
			final String detail = detail(c);
			if (!saving && c.getCost() >= 0 && c.getCost() <= budget)
			{
				buyNow.add(Line.of(c.getName(), c.getCost(), detail));
				budget -= c.getCost();
				continue;
			}
			// In the XP order nothing is bought past a purchase the points do not reach yet
			saving = inOrder;
			if (saveFor.size() < MAX_SAVE_FOR)
			{
				final int need = c.getCost() < 0 ? -1 : ahead + c.getCost() - budget;
				final int tasks = need > 0 && tasksFor != null ? tasksFor.applyAsInt(need) : -1;
				saveFor.add(new Line(c.getName(), c.getCost(), shortfall(need, tasks) + detail, need, tasks,
						saveLabel(c.getName(), c.getCost(), need, tasks)));
			}
			if (inOrder && c.getCost() > 0) ahead += c.getCost();
		}

		final List<Line> laterLines = new ArrayList<>();
		for (Candidate c : later)
		{
			if (laterLines.size() >= MAX_LATER) break;
			laterLines.add(new Line(c.getName(), c.getCost(), "Needs " + c.getLockedBy() + ". " + detail(c), 0, -1,
					laterLabel(c.getName(), c.getCost(), c.getLockedBy())));
		}
		final List<Line> avoidLines = new ArrayList<>();
		for (Candidate c : avoid)
		{
			// Every purchase the wiki says not to make is named, the goal model's only up to the limit
			if (!c.isAvoid() && avoidLines.size() >= MAX_AVOID) break;
			// What not to buy needs no price
			avoidLines.add(new Line(c.getName(), c.getCost(), detail(c), 0, -1, c.getName()));
		}
		return new Plan(points, reserve, buyNow, saveFor, laterLines, avoidLines, owned, total, cosmeticLeft);
	}

	/**
	 * Blocks help each other: every block raises the master's average, so the next poor task is worth more to block.
	 * Their values depend on the order they are bought in, and each list's block advice gives that order. The blocks
	 * keep the places the sort gave them among the other purchases, but each list fills its places in its advice's
	 * order, so the plan and the block lists agree on which block comes first.
	 */
	private static void keepBlockOrder(List<Candidate> sorted, List<Candidate> given)
	{
		final Map<SlayerMaster, Deque<Candidate>> blocks = new HashMap<>();
		for (Candidate c : given)
		{
			if (c.getType() == Type.BLOCK && sorted.contains(c)) blocks.computeIfAbsent(c.getList(), k -> new ArrayDeque<>()).add(c);
		}
		for (int i = 0; i < sorted.size(); i++)
		{
			final Candidate c = sorted.get(i);
			if (c.getType() == Type.BLOCK) sorted.set(i, blocks.get(c.getList()).poll());
		}
	}

	/** Most gain per point first. A free purchase counts as costing one point. Ties go to the bigger gain, then the name. */
	static final Comparator<Candidate> BEST_FIRST = Comparator
			.comparingDouble((Candidate c) -> c.getGain() / Math.max(1, c.getCost())).reversed()
			.thenComparing(Comparator.comparingDouble(Candidate::getGain).reversed())
			.thenComparing(Candidate::getName);

	/** The wiki's tiers for XP first, the goal model's order within each. */
	static final Comparator<Candidate> XP_ORDER = Comparator.comparingInt(SlayerPointsPlanner::xpTier).thenComparing(BEST_FIRST);

	/**
	 * A purchase's place in the XP order: 1 regular blocks and Bigger and Badder, 2 Stop the Wyvern and Malevolent
	 * Masquerade for the Slayer helmet, 3 extensions, 4 everything else. This departs from the wiki's literal list,
	 * which puts the Fossil Island Wyvern block with the blocks and superior monsters after them. Bigger and Badder costs
	 * 50 points and the wiki itself recommends turning it on ("optional, but recommended since you can toggle it off
	 * without penalty"), while Stop the Wyvern costs 500, five blocks' worth, to free one block slot. Taken literally, the
	 * order would hold a 50 point purchase behind a 500 point one.
	 */
	static int xpTier(Candidate c)
	{
		final SlayerReward r = c.getReward();
		if (c.getType() == Type.BLOCK || r == SlayerReward.BIGGER_AND_BADDER) return 1;
		if (r == SlayerReward.STOP_THE_WYVERN || r == SlayerReward.MALEVOLENT_MASQUERADE) return 2;
		if (r != null && r.getKind() == SlayerReward.Kind.EXTENSION) return 3;
		return 4;
	}

	/** "119 more points, about 12 tasks. " or "" when the cost is unknown. */
	static String shortfall(int need, int tasks)
	{
		if (need < 0) return "";
		final StringBuilder sb = new StringBuilder().append(need).append(" more points");
		if (tasks > 0) sb.append(", about ").append(tasks).append(tasks == 1 ? " task" : " tasks");
		return sb.append(". ").toString();
	}

	private static String detail(Candidate c)
	{
		final String why = c.getWhy() == null ? "" : c.getWhy();
		if (c.getNote() == null) return why;
		return why.isEmpty() ? c.getNote() : why + " " + c.getNote();
	}
}
