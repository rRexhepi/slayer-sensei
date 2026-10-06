package com.slayercompanion.data;

import com.slayercompanion.SlayerGoal;
import lombok.Data;

/**
 * Rough 1 to 5 judgments of a task, 5 best, used to judge tasks and masters by goal. Each one is from the
 * OSRS Wiki (the Slayer training guide's task summary, the money making guides and the task and master
 * strategy pages, read 4 October 2026) and checked against the dossier. They are never shown as numbers.
 * <ul>
 * <li>xp: Slayer XP per hour with the usual method. 5 is about 90k or more, 4 about 65k, 3 about 45k,
 * 2 about 25k, 1 below that. The tasks in the Slayer training guide's task summary (Duradel's list) use that
 * table's figure with one set of cut points halfway between: 5 from 77,500, 4 from 55,000, 3 from 35,000, 2 from
 * 20,000 and 1 below. Where the table gives several methods, the fastest for the regular monster counts, the one an
 * XP player uses, leaving out boss and variant figures such as Cerberus, tormented demons and wyrmlings. XP per
 * hour alone does not say whether a task is worth doing, so the table's own advice is kept apart in
 * {@link #xpAdvice}.</li>
 * <li>gp: profit per hour on the task's own monsters, supplies counted. 5 is about 2M or more, 4 about
 * 1M, 3 about 400k, 2 about 100k, 1 below that or a loss. On-task bosses are not counted, except where the
 * regular monsters lose money and the boss is how the task is done (cave kraken).</li>
 * <li>speed: how quickly a typical assignment is finished, travel included, at the masters that give the
 * most of it. 5 is about 15 minutes or less, 4 about 30, 3 about 50, 2 about 80, 1 longer.</li>
 * </ul>
 */
@Data
public class TaskRating
{
	private int xp;
	private int gp;
	private int speed;
	/** Short note of where the numbers come from. */
	private String basis;
	/** Krystilia's version of the task, which must be done in the Wilderness, or null when it rates the same. */
	private TaskRating wilderness;
	/** The task as a filler task at Turael's spot, which Aya and Spria share. Only the tasks on their lists have one. */
	private Filler filler;
	/** The wiki advises against extending the task (Slayer training, Task extension), whatever its ratings. */
	private boolean extensionDiscouraged;
	/**
	 * The wiki's advice on the task for XP: the Recommendation column of the Slayer training guide's task summary,
	 * read 4 October 2026. The column weighs task length, weight and supplies, which XP per hour cannot. It covers
	 * Duradel's list and the guide applies it at Nieve and Konar too ("Simply adjust the task list to whatever slayer
	 * master is being used"), so it belongs to the task. Null where the column is conditional or has no row.
	 */
	private XpAdvice xpAdvice;

	/** What the wiki's task summary says to do with a task for XP. */
	public enum XpAdvice
	{
		/** Do it. */
		DO,
		/** Skip or block it. */
		SKIP,
		/** Do not unlock it. */
		DONT_UNLOCK
	}

	/**
	 * How long the task takes as a filler task of the Slayer points routine, from the wiki's Turael task completion
	 * tips (Slayer reward point, Turael boosting, read 4 October 2026). Turael gives small amounts that any account
	 * kills quickly, so the time is in the trip and the awkward parts the table notes, not the kill count. It is a
	 * judgment on one scale, the goal model's 6 minutes between tasks included: 7 for a cannonable task with a close
	 * teleport, 8 for single-way combat or a longer trip, 9 for earmuffs or no cannon, 10 for poison without a cannon
	 * or ice coolers, 11 for poison and a light source, 12 for a light source and few spawns. In words, a task of
	 * {@link #QUICK} minutes or less is a quick filler task, one of {@link #SLOW} or more one of the slower ones, and
	 * the rest average.
	 */
	@Data
	public static class Filler
	{
		/** At most this many minutes: cannonable with a close teleport, a quick filler task. */
		public static final int QUICK = 7;
		/** At least this many minutes: poison, ice coolers or a light source, one of the slower filler tasks. */
		public static final int SLOW = 10;

		/** Minutes per assignment, travel and the minutes between tasks included. */
		private int minutes;
		/** Which of the table's notes the minutes follow. */
		private String basis;
	}

	/** The rating that judges the task for a goal: xp, gp, or speed for Slayer points. */
	public int forGoal(SlayerGoal goal)
	{
		switch (goal)
		{
			case XP:
				return xp;
			case PROFIT:
				return gp;
			default:
				return speed;
		}
	}

	/** Whether all three ratings are on the 1 to 5 scale. */
	public boolean isComplete()
	{
		return inRange(xp) && inRange(gp) && inRange(speed);
	}

	private static boolean inRange(int rating)
	{
		return rating >= 1 && rating <= 5;
	}
}
