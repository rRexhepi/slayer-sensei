package com.slayercompanion;

import com.slayercompanion.data.TaskRating;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.Value;

/**
 * The "Advisor" line for the task in progress: do it, extend it, skip it or block it, for the goal. Pure.
 * <p>
 * It follows the same model as the plan. A task is worth skipping when the skip model at its master skips it
 * ({@link SlayerGoalModel#skips}, the assumption behind the master ranking and the plan's reserve), and worth
 * blocking when the block advice at its master would block it. Costs are that master's ({@link SlayerAssigner}).
 * Which reward lengthens the task, and whether it is owned, come from the cache.
 * <p>
 * For Slayer points the streak comes first: the line never suggests changing a task from Turael or Aya, whose
 * task change resets the streak, and never suggests skipping a milestone task. A skip costs 30 points, more than
 * an ordinary task pays anywhere, so it never suggests skipping for points at all. A block can still pay: on the
 * routine a filler task from Turael's spot or Spria that the block advice there would block, when the block
 * raises the routine's points per hour enough to notice. A block skips the task in progress "without penalties"
 * (wiki Slayer Rewards), so a milestone task never gets one.
 * <p>
 * For XP or profit a task from Turael's spot or Spria is simply done: every task they give is poor for both, so a
 * block or skip there buys nothing, and the line names the goal's best master instead.
 * <p>
 * For XP the Recommendation column of the wiki's task summary decides where it has advice
 * ({@link TaskRating#getXpAdvice}): a task it says to do is done, and one it says to skip or not to unlock is skipped or
 * blocked at a master that takes skips. The line cites the guide rather than calling a fast task poor.
 * <p>
 * Each decision also gives the compact sidebar's items ({@link #compact}), which say the same in a few words.
 */
final class SlayerUnlockAdvisor
{
	/** Everything the line depends on, gathered by {@link SlayerPlanBuilder}. */
	@Value
	static class Situation
	{
		SlayerGoal goal;
		/** The task in its master's list, with the ratings that apply there. Null when unknown. */
		SlayerPool.Entry entry;
		SlayerAssigner master;
		/** The NPC who gave it. */
		String npc;
		int points;
		/** The skip model at the master skips it for the goal. */
		boolean worthSkipping;
		/**
		 * The block advice at the master would block it. For a filler task on the Slayer points routine, only when
		 * the block also raises the routine's rate enough to notice.
		 */
		boolean worthBlocking;
		/** An open block slot is empty at the master. */
		boolean freeSlot;
		/** A current block that does not fit the goal and can be lifted to make room, or null. */
		String unblock;
		/** The reward that lengthens the task, or null when it has none. */
		Extension extension;
		/** The task's number in its streak, 0 when it counts toward none. */
		int taskNumber;
		/** Points the task pays when done, -1 when unknown. */
		int payout;
		/** The task has a superior and Bigger and Badder is not owned. */
		boolean superiorMissing;
		int superiorCost;
		/** The NPC at the best master for XP or profit, or null when unknown. */
		String bestMaster;
	}

	/** The reward that lengthens the task. */
	@Value
	static class Extension
	{
		String name;
		int cost;
		boolean owned;
		/** What it does for the goal at the master, as in {@link SlayerSpend}. */
		double gain;
	}

	/** The line and the compact sidebar's items that say the same in a few words, e.g. "Skip (30)". */
	private static final class Said
	{
		final String line;
		final List<String> items;

		Said(String line, String... items)
		{
			this.line = line;
			this.items = Collections.unmodifiableList(Arrays.asList(items));
		}
	}

	private SlayerUnlockAdvisor()
	{
	}

	/** The line, or null when there is no task or nothing to judge it by. */
	static String advise(Situation s)
	{
		final Said said = decide(s);
		return said == null ? null : said.line;
	}

	/** The same advice as the compact sidebar's items, e.g. "Skip (30)". Empty when there is no line. */
	static List<String> compact(Situation s)
	{
		final Said said = decide(s);
		return said == null ? Collections.<String>emptyList() : said.items;
	}

	private static Said decide(Situation s)
	{
		if (s == null || s.getMaster() == null) return null;
		if (s.getGoal() == SlayerGoal.POINTS) return forPoints(s);
		if (s.getMaster() == SlayerAssigner.TURAEL || s.getMaster() == SlayerAssigner.SPRIA) return forFillerMaster(s);
		final SlayerPool.Entry e = s.getEntry();
		if (e == null || !e.isRated()) return null;
		final int block = s.getMaster().getBlockCost();
		final int skip = s.getMaster().getCancelCost();
		// For XP the wiki's Slayer guide is the advice where it has some, cited instead of the rating
		final TaskRating.XpAdvice wiki = e.wikiAdvice(s.getGoal());
		final String judged = wiki != null ? SlayerSpend.wikiSays(wiki) : capitalize(SlayerSpend.judge(e, s.getGoal())) + " at " + s.getNpc() + ".";
		final String have = " You have " + s.getPoints() + " points.";
		final String blockLater = "Later: block (" + block + ")";
		if (wiki == TaskRating.XpAdvice.DO) return doIt(s, judged);
		if (s.isWorthBlocking() && s.getPoints() >= block)
		{
			if (s.isFreeSlot())
			{
				return new Said("Block it for " + block + " points. " + judged + " A block slot is free and you have " + s.getPoints() + " points.",
						"Block (" + block + ")");
			}
			if (s.getUnblock() != null)
			{
				return new Said("Block it for " + block + " points once you unblock " + s.getUnblock() + ", which does not fit your goal. " + judged
						+ have, "Unblock " + s.getUnblock(), "Then block (" + block + ")");
			}
		}
		if (s.isWorthSkipping())
		{
			if (s.getPoints() >= skip)
			{
				final String later = s.isWorthBlocking() ? " It is worth a block once you have " + block + " points and a free slot." : "";
				final String line = "Skip it for " + skip + " points. " + judged + have + later;
				return s.isWorthBlocking() ? new Said(line, "Skip (" + skip + ")", blockLater) : new Said(line, "Skip (" + skip + ")");
			}
			return new Said("Do it. Skipping costs " + skip + " points and you have " + s.getPoints() + ". " + judged,
					"Do it", "Skip costs " + skip + ", you have " + s.getPoints());
		}
		if (s.isWorthBlocking())
		{
			return new Said("Do it. " + judged + " It is worth a block once you have " + block + " points and a free slot." + have,
					"Do it", blockLater);
		}
		if (wiki != null && s.getMaster().getStreak() == SlayerAssigner.Streak.MORTIMER)
		{
			// Mortimer takes no skips here: he offers a choice of tasks instead
			return new Said("Do it. " + judged + " " + s.getNpc() + " offers a choice, so pick another next time.",
					"Do it", "Pick another next time");
		}
		return doIt(s, judged);
	}

	/** "Do it." with the judgement and the one purchase that would make the task better: its extension or its superior. */
	private static Said doIt(Situation s, String judged)
	{
		final String line = "Do it. " + judged;
		final Extension x = s.getExtension();
		if (x != null && !x.isOwned() && x.getGain() > SlayerPointsPlanner.NOTICEABLE)
		{
			return new Said(line + " Buy " + x.getName() + " for " + x.getCost() + " points to make the next ones longer.",
					"Do it", "Buy " + x.getName() + " (" + x.getCost() + ")");
		}
		if (s.isSuperiorMissing())
		{
			return new Said(line + " Bigger and Badder, " + s.getSuperiorCost() + " points, adds its superior.",
					"Do it", "Buy Bigger and Badder (" + s.getSuperiorCost() + ")");
		}
		return new Said(line, "Do it");
	}

	/** Slayer points: protect the streak, and spend points only on a block that speeds up the routine. */
	private static Said forPoints(Situation s)
	{
		final SlayerAssigner master = s.getMaster();
		final String npc = s.getNpc();
		final int n = s.getTaskNumber();
		if (master.getStreak() == SlayerAssigner.Streak.MORTIMER)
		{
			return new Said("Do it. " + SlayerSpend.possessive(npc) + " tasks do not count toward your streak.", "Do it, not on your streak");
		}
		final boolean milestone = n > 0 && SlayerPayout.isMilestone(n);
		final int cost = master.getBlockCost();
		final SlayerPool.Entry e = s.getEntry();
		// A block skips the task in progress, so a milestone task is never blocked
		final boolean block = !milestone && s.isWorthBlocking() && s.isFreeSlot() && s.getPoints() >= cost && e != null && e.isRated();
		final String longer = e == null ? "" : e.getName() + " take longer than the average task at " + npc;
		final String have = " A block slot is free and you have " + s.getPoints() + " points.";
		final String blockIt = "Block (" + cost + ")";
		if (SlayerPayout.basePoints(master, SlayerPayout.Diaries.NONE) == 0)
		{
			// Turael, Aya and Spria pay nothing but move the streak. Changing the task with Turael or Aya resets it.
			final boolean changes = master == SlayerAssigner.TURAEL;
			if (block)
			{
				return new Said("Block it for " + cost + " points. " + longer + ", and a block keeps your filler tasks quick." + have
						+ (changes ? " A block skips it without penalty, but changing it with " + npc + " would reset your streak." : ""), blockIt);
			}
			final String change = changes ? " Changing it with " + npc + " would reset your streak." : "";
			return new Said("Do it. Task " + n + " of your streak pays no points but counts toward the next milestone." + change,
					"Do it, #" + n + " pays 0");
		}
		if (milestone)
		{
			final String pays = s.getPayout() > 0 ? npc + " pays " + s.getPayout() + " for it." : "It pays a milestone bonus.";
			return new Said("Do it. Task " + n + " is a milestone. " + pays,
					s.getPayout() > 0 ? "Do it, #" + n + " pays " + s.getPayout() : "Do it, #" + n + " is a milestone");
		}
		if (block)
		{
			return new Said("Block it for " + cost + " points. " + longer + ", and a block keeps your milestone tasks quick." + have, blockIt);
		}
		final String pays = s.getPayout() >= 0 ? " " + npc + " pays " + s.getPayout() + " for task " + n + "." : "";
		return new Said("Do it." + pays, s.getPayout() >= 0 ? "Do it, #" + n + " pays " + s.getPayout() : "Do it");
	}

	/** XP or profit from Turael's spot or Spria, whose tasks are fillers for the points streak. */
	private static Said forFillerMaster(Situation s)
	{
		final String best = s.getBestMaster();
		final String goal = s.getGoal() == SlayerGoal.XP ? "XP" : "profit";
		final String where = best == null || best.equals(s.getNpc()) ? "" : " " + best + " is the best master for " + goal + ".";
		return new Said("Do it. " + SlayerSpend.possessive(s.getNpc()) + " tasks are quick fillers for a Slayer points streak." + where, "Do it");
	}

	private static String capitalize(String text)
	{
		return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
	}
}
