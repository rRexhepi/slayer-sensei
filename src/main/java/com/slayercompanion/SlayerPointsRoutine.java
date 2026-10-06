package com.slayercompanion;

import com.slayercompanion.SlayerMasterAccess.Option;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import lombok.Value;

/**
 * The Slayer points routine: every task at Turael or Aya, who pay nothing but move the streak, and each
 * boost task (every 10th or every 50th) at the master who pays most. Turael, Aya and Spria all count
 * toward the four unpaid tasks at the start of a streak, which is why the routine reaches milestones
 * fastest (OSRS Wiki, Slayer reward point, "Point boosting").
 * <p>
 * Pure. Only masters on the main streak are ranked. Krystilia keeps her own streak (her milestones follow
 * her own count, not this one), and Mortimer's tasks never count toward it and pay through his modifier
 * only, so neither can turn this streak's milestone into points.
 */
final class SlayerPointsRoutine
{
	/** The streak counters and the task in progress, as the client holds them. */
	@Value
	static class Streaks
	{
		/** SLAYER_TASKS_COMPLETED: tasks done in a row for every master except Krystilia and Mortimer. */
		int main;
		/** SLAYER_WILDERNESS_TASKS_COMPLETED: Krystilia's own streak. */
		int wilderness;
		/** SLAYER_MASTER: the master of the current assignment. Only meaningful while a task is in progress. */
		int taskMaster;
		boolean taskInProgress;

		/** Who gave the task in progress, or null without a task or for an unknown master id. */
		SlayerAssigner currentMaster()
		{
			return taskInProgress ? SlayerAssigner.forId(taskMaster) : null;
		}
	}

	/** One master's pay for a milestone task. */
	@Value
	static class Ranked
	{
		SlayerAssigner master;
		String npc;
		int points;
	}

	/** The routine's advice, as short sentences for the sidebar plus the numbers behind them. */
	@Value
	static class Advice
	{
		/** Who gave the task in progress and what it pays, or null without a task. */
		String current;
		/** Where to get the next task. */
		String next;
		/** The wiki's warning about Konar on 10th tasks, or null. */
		String caveat;
		/** The next milestone and what it pays at the best master. */
		String milestone;
		/** Changing a task at Turael or Aya resets the streak. */
		String warning;
		/** The next task to get, numbered in the main streak. */
		int nextTask;
		/** Masters ranked for the next task when it is a boost task, highest first, else empty. */
		List<Ranked> ranking;
		/** The compact sidebar's heading, "NEXT TASK", or "NEXT TASK #50" when the next task is a milestone. */
		String heading;
		/**
		 * The compact sidebar's items: the next task and the next milestone, as "#48 Aya, 0 pts" and "#50 Konar, 270 pts".
		 * Before a milestone the paying masters instead, as "Konar 270".
		 */
		List<String> items;
	}

	/** Masters the compact sidebar names for a milestone task. {@link Advice#getNext} keeps the whole list. */
	static final int COMPACT_MASTERS = 3;

	private SlayerPointsRoutine()
	{
	}

	/** The next main-streak task to get. A task in progress from a master on that streak comes first. */
	static int nextTask(Streaks streaks)
	{
		final SlayerAssigner current = streaks.currentMaster();
		// An unknown master id is most likely a main streak master, so its task is counted
		final boolean currentCounts = streaks.isTaskInProgress()
				&& (current == null || current.getStreak() == SlayerAssigner.Streak.MAIN);
		return streaks.getMain() + (currentCounts ? 2 : 1);
	}

	/** The next task of a master's own streak, after any task in progress that counts toward it. */
	static int nextTask(Streaks streaks, SlayerAssigner master)
	{
		if (master.getStreak() == SlayerAssigner.Streak.WILDERNESS)
		{
			final boolean currentCounts = streaks.currentMaster() == SlayerAssigner.KRYSTILIA;
			return streaks.getWilderness() + (currentCounts ? 2 : 1);
		}
		return nextTask(streaks);
	}

	/** Points the task in progress pays on completion, or -1 without a task or when its master is unknown. */
	static int currentPayout(Streaks streaks, SlayerPayout.Diaries diaries)
	{
		final SlayerAssigner master = streaks.currentMaster();
		if (master == null) return -1;
		switch (master.getStreak())
		{
			case WILDERNESS:
				return SlayerPayout.payout(master, streaks.getWilderness() + 1, diaries);
			case MORTIMER:
				return 0;
			default:
				return SlayerPayout.payout(master, streaks.getMain() + 1, diaries);
		}
	}

	/**
	 * Usable main-streak masters that pay for task {@code task}, highest first. On a tie the master with
	 * the lower combat requirement comes first, as its tasks are usually quicker.
	 */
	static List<Ranked> rank(int task, List<Option> options, SlayerPayout.Diaries diaries)
	{
		final List<Ranked> out = new ArrayList<>();
		for (Option o : options)
		{
			if (!o.isUsable() || o.getMaster().getStreak() != SlayerAssigner.Streak.MAIN) continue;
			final int points = SlayerPayout.payout(o.getMaster(), task, diaries);
			if (points > 0) out.add(new Ranked(o.getMaster(), o.getNpc(), points));
		}
		out.sort(Comparator.comparingInt(Ranked::getPoints).reversed()
				.thenComparingInt(r -> r.getMaster().getCombatLevel()));
		return out;
	}

	/** What task {@code task} pays when the routine is followed: the best master on a boost task, else 0. */
	static int routinePayout(int task, BoostInterval boost, List<Option> options, SlayerPayout.Diaries diaries)
	{
		if (!boost.isBoostTask(task)) return 0;
		final List<Ranked> ranking = rank(task, options, diaries);
		return ranking.isEmpty() ? 0 : ranking.get(0).getPoints();
	}

	static Advice advise(Streaks streaks, List<Option> options, SlayerPayout.Diaries diaries, BoostInterval boost)
	{
		final Option low = SlayerMasterAccess.find(options, SlayerAssigner.TURAEL);
		final Option spria = SlayerMasterAccess.find(options, SlayerAssigner.SPRIA);
		final int next = nextTask(streaks);
		final boolean boostNext = boost.isBoostTask(next);
		final List<Ranked> ranking = boostNext ? rank(next, options, diaries) : Collections.<Ranked>emptyList();
		final String caveat = caveat(next, ranking);

		final List<String> items = new ArrayList<>();
		final String heading;
		if (boostNext && !ranking.isEmpty())
		{
			heading = "NEXT TASK #" + next;
			for (Ranked r : ranking.subList(0, Math.min(COMPACT_MASTERS, ranking.size())))
			{
				items.add(r.getNpc() + " " + r.getPoints());
			}
			if (caveat != null) items.add("Wiki: avoid Konar for 10ths");
		}
		else
		{
			heading = "NEXT TASK";
			items.add("#" + next + " " + low.getNpc() + (spria != null && spria.isUsable() ? " or " + spria.getNpc() : "") + ", 0 pts");
			items.add(milestoneItem(boostNext ? next + boost.getEvery() : boost.nextBoostTask(next), options, diaries));
		}

		return new Advice(
				current(streaks, options, diaries),
				nextText(next, boostNext, ranking, low, spria),
				caveat,
				milestone(next, boostNext, streaks.isTaskInProgress(), boost, options, diaries),
				"Changing a task with " + low.getNpc() + " resets your streak to 0.",
				next,
				ranking,
				heading,
				Collections.unmodifiableList(items));
	}

	/** "#50 Konar, 270 pts": the master who pays most for milestone {@code target}, or "No master pays yet". */
	private static String milestoneItem(int target, List<Option> options, SlayerPayout.Diaries diaries)
	{
		final List<Ranked> ranking = rank(target, options, diaries);
		if (ranking.isEmpty()) return "No master pays yet";
		return "#" + target + " " + ranking.get(0).getNpc() + ", " + ranking.get(0).getPoints() + " pts";
	}

	private static String current(Streaks streaks, List<Option> options, SlayerPayout.Diaries diaries)
	{
		if (!streaks.isTaskInProgress()) return null;
		final SlayerAssigner master = streaks.currentMaster();
		final int task = streaks.getMain() + 1;
		if (master == null)
		{
			return "This is task " + task + " of your streak. What it pays depends on the master who gave it.";
		}
		final Option option = SlayerMasterAccess.find(options, master);
		final String npc = option == null ? master.getDisplayName() : option.getNpc();
		switch (master.getStreak())
		{
			case WILDERNESS:
				final int wild = streaks.getWilderness() + 1;
				return pays(npc, SlayerPayout.payout(master, wild, diaries)) + " for task " + wild
						+ " of her Wilderness streak. That streak is counted apart from this one.";
			case MORTIMER:
				return "Mortimer pays points only through his points modifier. His tasks do not count toward your streak.";
			default:
				final int points = SlayerPayout.payout(master, task, diaries);
				if (points == 0 && task <= SlayerPayout.UNPAID_TASKS)
				{
					return "Task " + task + " of your streak pays nothing. The first " + SlayerPayout.UNPAID_TASKS
							+ " tasks of a streak never pay.";
				}
				return pays(npc, points) + " for task " + task + " of your streak."
						+ (points == 0 ? " It still counts toward the streak." : "");
		}
	}

	private static String pays(String npc, int points)
	{
		return points == 0 ? npc + " pays no points" : npc + " pays " + points;
	}

	private static String nextText(int next, boolean boostNext, List<Ranked> ranking, Option low, Option spria)
	{
		final String lowAt = low.getNpc() + " in " + low.getMaster().getLocation()
				+ (spria != null && spria.isUsable() ? " or " + spria.getNpc() + " in " + spria.getMaster().getLocation() : "");
		if (boostNext && !ranking.isEmpty())
		{
			return "Task " + next + " is a milestone. Get it from the master who pays most: " + list(ranking) + ".";
		}
		if (boostNext)
		{
			return "Task " + next + " is a milestone, but no master you can use pays points yet. Get it from " + lowAt + ".";
		}
		if (next <= SlayerPayout.UNPAID_TASKS)
		{
			return "Get task " + next + " from " + lowAt + ". The first " + SlayerPayout.UNPAID_TASKS
					+ " tasks of a streak pay no points anywhere.";
		}
		return "Get task " + next + " from " + lowAt + ". It pays no points but still counts toward your streak.";
	}

	private static String list(List<Ranked> ranking)
	{
		final StringBuilder sb = new StringBuilder();
		for (Ranked r : ranking)
		{
			if (sb.length() > 0) sb.append(", ");
			sb.append(r.getNpc()).append(' ').append(r.getPoints());
		}
		return sb.toString();
	}

	/**
	 * The Turael and Aya wiki pages advise against boosting every 10th task with Konar, as she gives many
	 * unfavourable tasks, unless another master takes the 10th. Her 50th and later milestones are fine.
	 */
	private static String caveat(int next, List<Ranked> ranking)
	{
		if (ranking.isEmpty() || ranking.get(0).getMaster() != SlayerAssigner.KONAR || next % 50 == 0) return null;
		final String runnerUp = ranking.size() > 1
				? " " + ranking.get(1).getNpc() + " at " + ranking.get(1).getPoints() + " is the next best." : "";
		return "Konar gives many unfavourable tasks, so the wiki suggests another master for 10th tasks." + runnerUp;
	}

	private static String milestone(int next, boolean boostNext, boolean taskInProgress, BoostInterval boost,
			List<Option> options, SlayerPayout.Diaries diaries)
	{
		final int target = boostNext ? next + boost.getEvery() : boost.nextBoostTask(next);
		final List<Ranked> ranking = rank(target, options, diaries);
		if (ranking.isEmpty())
		{
			return "No master you can use pays points yet." + firstPayingLock(options);
		}
		final Ranked best = ranking.get(0);
		final String pays = " " + best.getNpc() + " pays " + best.getPoints() + " for it.";
		if (boostNext)
		{
			return "After task " + next + ", the next is task " + target + "." + pays;
		}
		final int away = target - next + 1;
		return "Task " + target + ", " + away + (taskInProgress ? " tasks after this one." : " tasks from now.") + pays;
	}

	/** What keeps the lowest paying master out of reach, so a new account knows what to work toward. */
	private static String firstPayingLock(List<Option> options)
	{
		for (Option o : options)
		{
			if (o.getMaster().getStreak() == SlayerAssigner.Streak.MAIN && !o.isUsable()
					&& SlayerPayout.basePoints(o.getMaster(), SlayerPayout.Diaries.NONE) > 0)
			{
				return " " + o.getNpc() + " needs " + o.getMissing() + ".";
			}
		}
		return "";
	}
}
