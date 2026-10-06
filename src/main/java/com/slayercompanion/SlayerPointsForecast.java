package com.slayercompanion;

import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * Roughly how many tasks it takes to earn a number of Slayer points, by walking the upcoming task numbers
 * so milestones and the unpaid start of a streak are counted. Pure.
 */
final class SlayerPointsForecast
{
	/** The walk stops here. A goal further away than this counts as out of reach. */
	static final int MAX_TASKS = 2000;

	private SlayerPointsForecast()
	{
	}

	/**
	 * Tasks until {@code needed} points are earned: the task in progress first when there is one, then task
	 * {@code nextTask}, {@code nextTask + 1} and so on. 0 when nothing is needed, -1 when the payouts never
	 * get there.
	 *
	 * @param current points the task in progress pays, or -1 without one
	 * @param payoutAt points a task pays, by its number in the streak
	 */
	static int tasksToEarn(int needed, int current, int nextTask, IntUnaryOperator payoutAt)
	{
		if (needed <= 0) return 0;
		int earned = Math.max(0, current);
		int tasks = current >= 0 ? 1 : 0;
		for (int task = nextTask; earned < needed; task++)
		{
			if (tasks >= MAX_TASKS) return -1;
			earned += payoutAt.applyAsInt(task);
			tasks++;
		}
		return tasks;
	}

	/** Tasks to earn {@code needed} points with the Slayer points routine. */
	static int withRoutine(int needed, SlayerPointsRoutine.Streaks streaks, List<SlayerMasterAccess.Option> options,
			SlayerPayout.Diaries diaries, BoostInterval boost)
	{
		return tasksToEarn(needed, SlayerPointsRoutine.currentPayout(streaks, diaries), SlayerPointsRoutine.nextTask(streaks),
				task -> SlayerPointsRoutine.routinePayout(task, boost, options, diaries));
	}

	/** Tasks to earn {@code needed} points doing every task at one master, milestones included. */
	static int atMaster(int needed, SlayerAssigner master, SlayerPointsRoutine.Streaks streaks, SlayerPayout.Diaries diaries)
	{
		return tasksToEarn(needed, SlayerPointsRoutine.currentPayout(streaks, diaries), SlayerPointsRoutine.nextTask(streaks, master),
				task -> SlayerPayout.payout(master, task, diaries));
	}
}
