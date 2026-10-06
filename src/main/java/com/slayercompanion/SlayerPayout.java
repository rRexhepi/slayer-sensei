package com.slayercompanion;

import lombok.Value;

/**
 * Slayer reward points a completed task pays. Pure. Points per task are not in any cache table, so every
 * number here is from the OSRS Wiki, read as raw wikitext on 4 October 2026: the "Slayer reward point"
 * page (its table of points per master, the milestone list and the first four tasks), checked against
 * the points table on each master's own page.
 */
final class SlayerPayout
{
	/** "Slayer points are not awarded for the first four tasks" of a streak (Slayer reward point). */
	static final int UNPAID_TASKS = 4;

	/**
	 * {interval, multiplier} from the Slayer reward point page: 5x every 10th task, 15x every 50th, 25x
	 * every 100th, 35x every 250th and 50x every 1,000th. A task on several intervals takes the largest,
	 * so the largest interval comes first.
	 */
	private static final int[][] MILESTONES = {{1000, 50}, {250, 35}, {100, 25}, {50, 15}, {10, 5}};

	/** The two elite diaries that raise a master's points. */
	@Value
	static class Diaries
	{
		static final Diaries NONE = new Diaries(false, false);

		/** Elite Western Provinces: Nieve and Steve pay 15 instead of 12. */
		boolean westernElite;
		/** Elite Kourend & Kebos: Konar pays 20 instead of 18. */
		boolean kourendElite;
	}

	private SlayerPayout()
	{
	}

	/**
	 * Points for an ordinary task, before milestones. Wiki pages: "Slayer reward point" for all of them,
	 * and Turael, Aya, Spria, Mazchna, Achtryn, Vannaka, Chaeldar, Nieve, Steve, Duradel, Kuradal,
	 * Konar quo Maten, Krystilia and Mortimer. A replacement pays what the master it replaces pays.
	 */
	static int basePoints(SlayerAssigner master, Diaries diaries)
	{
		switch (master)
		{
			case MAZCHNA:
				return 6;
			case VANNAKA:
				return 8;
			case CHAELDAR:
				return 10;
			case NIEVE:
				return diaries.isWesternElite() ? 15 : 12;
			case DURADEL:
				return 15;
			case KONAR:
				return diaries.isKourendElite() ? 20 : 18;
			case KRYSTILIA:
				return 25;
			case TURAEL:
			case SPRIA:
				return 0;
			case MORTIMER:
			default:
				// Mortimer pays 5 to 40 only through his points modifier, never times a milestone
				return 0;
		}
	}

	/** The milestone multiplier for a task number in its streak, 1 for an ordinary task. */
	static int multiplier(int taskNumber)
	{
		for (int[] m : MILESTONES)
		{
			if (taskNumber > 0 && taskNumber % m[0] == 0) return m[1];
		}
		return 1;
	}

	/**
	 * The milestone multiplier averaged over the tasks a routine takes to its paying master, every
	 * {@code every}th task of a long streak: 8.75 for every 10th and 23.75 for every 50th. The pattern repeats
	 * every 1,000 tasks. Over all 1,000 tasks of a streak the same table averages 1.775 times the base points,
	 * the figure the Slayer reward point page gives.
	 */
	static double averageMultiplier(int every)
	{
		final int cycle = MILESTONES[0][0];
		int sum = 0;
		int count = 0;
		for (int task = every; every > 0 && task <= cycle; task += every)
		{
			sum += multiplier(task);
			count++;
		}
		return count == 0 ? 1 : sum / (double) count;
	}

	/** Whether a task number pays a milestone bonus. Every milestone is a multiple of 10. */
	static boolean isMilestone(int taskNumber)
	{
		return multiplier(taskNumber) > 1;
	}

	/**
	 * Points for completing task {@code taskNumber} of the master's own streak (1 for the first task after
	 * a reset). Tasks 1 to 4 pay nothing anywhere.
	 */
	static int payout(SlayerAssigner master, int taskNumber, Diaries diaries)
	{
		if (taskNumber <= UNPAID_TASKS) return 0;
		return basePoints(master, diaries) * multiplier(taskNumber);
	}
}
